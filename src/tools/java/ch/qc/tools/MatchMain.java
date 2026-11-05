package ch.qc.tools;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.Experiment;
import ch.cern.quantumconnect.core.ExperimentStatus;
import ch.cern.quantumconnect.core.Move;
import ch.cern.quantumconnect.core.SpaceOwnership;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Match runner in the official protocol (real 100 ms limits, thread per call): colour-swapped game pairs from a
 * shared opening, pentanomial statistics with an optional SPRT that stops the match at a verdict (design 5.2,
 * 5.3), timeouts and illegal answers per bot, latency and search statistics.
 *
 * <pre>
 * --a SPEC --b SPEC       bots: a class name, qcbot:+opt,-opt (current DarkPrince with search switches), jar:PATH
 * --pairs N               game pairs, the cap when --sprt is given (default 50)
 * --sprt ELO0,ELO1        stop at the SPRT verdict (alpha = beta = 0.05)
 * --openings FILE         opening pool, one comma-separated column list per line; pair p uses line (p + seed) mod N
 * --opening N             without a pool: random normal plies before the bots take over, at most 10 (default 4)
 * --sizes 8x12,6x7        board sizes, cycled per pair (default 8x12; a pool is for its own size)
 * --threads N             parallel games (default cores - 1)
 * --seed S                random-opening seed and pool offset (default 1)
 * --out FILE              one JSON line per game, written as pairs finish
 * </pre>
 */
public final class MatchMain {
  public record Result(int pair, int game, int opening, int rows, int cols, boolean aFirst, int scoreA2,
      String reason, int plies, int timeoutsA, int timeoutsB, int invalidA, int invalidB, int badSpecialsA,
      int badSpecialsB) {
    String json() {
      return String.format(java.util.Locale.ROOT,
          "{\"pair\":%d,\"game\":%d,\"opening\":%d,\"rows\":%d,\"cols\":%d,\"aFirst\":%b,\"scoreA\":%.1f,"
              + "\"reason\":\"%s\",\"plies\":%d,\"timeoutsA\":%d,\"timeoutsB\":%d,\"invalidA\":%d,\"invalidB\":%d,"
              + "\"badSpecialsA\":%d,\"badSpecialsB\":%d}",
          pair, game, opening, rows, cols, aFirst, scoreA2 / 2.0, reason, plies, timeoutsA, timeoutsB, invalidA,
          invalidB, badSpecialsA, badSpecialsB);
    }
  }

  public static void main(String[] args) throws Exception {
    String a = "ch.qc.bot.DarkPrince";
    String b = "ch.qc.starter.RandomBot";
    int pairs = 50;
    String sizes = "8x12";
    int opening = 4;
    int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    long seed = 1;
    String out = null;
    double[] sprt = null;
    String openings = null;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--a" -> a = args[++i];
        case "--b" -> b = args[++i];
        case "--pairs" -> pairs = Integer.parseInt(args[++i]);
        case "--sizes" -> sizes = args[++i];
        case "--opening" -> opening = Integer.parseInt(args[++i]);
        case "--threads" -> threads = Integer.parseInt(args[++i]);
        case "--seed" -> seed = Long.parseLong(args[++i]);
        case "--out" -> out = args[++i];
        case "--sprt" -> {
          String[] e = args[++i].split(",");
          sprt = new double[] {Double.parseDouble(e[0]), Double.parseDouble(e[1])};
        }
        case "--openings" -> openings = args[++i];
        default -> throw new IllegalArgumentException("unknown argument " + args[i]);
      }
    }
    Match m = new Match(a, b, parseSizes(sizes), opening, seed);
    if (openings != null) {
      m.useOpenings(readOpenings(Path.of(openings)));
    }
    if (sprt != null) {
      m.useSprt(sprt[0], sprt[1]);
    }
    PrintWriter w = null;
    if (out != null) {
      Path p = Path.of(out);
      if (p.getParent() != null) {
        Files.createDirectories(p.getParent());
      }
      w = new PrintWriter(Files.newBufferedWriter(p, StandardCharsets.UTF_8), true);
    }
    long t0 = System.nanoTime();
    try {
      List<Result> results = m.run(pairs, threads, w);
      System.out.println(m.report(results));
      System.out.printf(java.util.Locale.ROOT, "  wall time %.1f min on %d threads%n", (System.nanoTime() - t0) / 6e10,
          threads);
    } finally {
      if (w != null) {
        w.close();
      }
    }
  }

  /** Opening pool file: one comma-separated column list per line (plies alternate from the first player). */
  public static List<int[]> readOpenings(Path file) throws java.io.IOException {
    List<int[]> pool = new ArrayList<>();
    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      line = line.trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      String[] parts = line.split(",");
      int[] cols = new int[parts.length];
      for (int i = 0; i < parts.length; i++) {
        cols[i] = Integer.parseInt(parts[i].trim());
      }
      pool.add(cols);
    }
    return pool;
  }

  static int[][] parseSizes(String spec) {
    String[] parts = spec.split(",");
    int[][] s = new int[parts.length][];
    for (int i = 0; i < parts.length; i++) {
      String[] rc = parts[i].trim().toLowerCase().split("x");
      s[i] = new int[] {Integer.parseInt(rc[0]), Integer.parseInt(rc[1])};
    }
    return s;
  }

  /** One match between two bot classes; reusable from tests. */
  public static final class Match {
    private final String classA;
    private final String classB;
    private final int[][] sizes;
    private final int openingPlies;
    private final long seed;
    public final LatencyHistogram latencyA = new LatencyHistogram();
    public final LatencyHistogram latencyB = new LatencyHistogram();
    public final LatencyHistogram specialLatencyA = new LatencyHistogram();
    private final AtomicInteger callsA = new AtomicInteger();
    public final LatencyHistogram allocationA = new LatencyHistogram();
    public final LatencyHistogram searchTimeA = new LatencyHistogram();
    public final LatencyHistogram outsideSearchA = new LatencyHistogram();
    private final long[] depthA = new long[65];
    private long nodesA;
    private long nanosA;
    private final long[] vcfA = new long[3];
    private List<int[]> openingPool;
    private Sprt sprt = new Sprt(0, 10);
    private boolean stopAtVerdict;

    public Match(String classA, String classB, int[][] sizes, int openingPlies, long seed) {
      this.classA = classA;
      this.classB = classB;
      this.sizes = sizes;
      this.openingPlies = openingPlies;
      this.seed = seed;
    }

    public int callsA() {
      return callsA.get();
    }

    public void useOpenings(List<int[]> pool) {
      openingPool = pool;
    }

    /** Stop the match at the verdict of an SPRT with these bounds. */
    public void useSprt(double elo0, double elo1) {
      sprt = new Sprt(elo0, elo1);
      stopAtVerdict = true;
    }

    public Sprt sprt() {
      return sprt;
    }

    public List<Result> run(int pairs, int threads) throws Exception {
      return run(pairs, threads, null);
    }

    /** Plays up to the given number of pairs; each pair is one task, its two games share the opening. */
    public List<Result> run(int pairs, int threads, PrintWriter out) throws Exception {
      ExecutorService pool = Executors.newFixedThreadPool(threads);
      try {
        CompletionService<Result[]> done = new ExecutorCompletionService<>(pool);
        for (int p = 0; p < pairs; p++) {
          int pair = p;
          done.submit(() -> new Result[] {play(pair, 0), play(pair, 1)});
        }
        List<Result> results = new ArrayList<>();
        for (int p = 0; p < pairs; p++) {
          Result[] r = done.take().get();
          results.add(r[0]);
          results.add(r[1]);
          sprt.add(r[0].scoreA2 + r[1].scoreA2);
          if (out != null) {
            out.println(r[0].json());
            out.println(r[1].json());
          }
          if (stopAtVerdict && sprt.verdict() != Sprt.Verdict.CONTINUE) {
            break;
          }
        }
        return results;
      } finally {
        pool.shutdownNow();
      }
    }

    Result play(int pair, int game) throws Exception {
      int[] size = sizes[pair % sizes.length];
      int rows = size[0];
      int cols = size[1];
      int openingId = openingPool == null ? -1 : (int) ((pair + seed) % openingPool.size());
      int[][] openings = openingPool == null ? opening(rows, cols, new SplittableRandom(seed * 1_000_003L + pair))
          : split(openingPool.get(openingId));
      boolean aFirst = game == 0;
      BotProbe pa = new BotProbe(instantiate(classA));
      BotProbe pb = new BotProbe(instantiate(classB));
      Algorithm first = new ScriptedOpening(aFirst ? pa : pb, openings[0]);
      Algorithm second = new ScriptedOpening(aFirst ? pb : pa, openings[1]);
      Experiment exp = new Experiment(first, second, false, false, rows, cols);
      exp.runExperiment();

      ExperimentStatus st = exp.getExperimentStatus();
      List<Move> history = exp.getHistory();
      SpaceOwnership aColour = aFirst ? SpaceOwnership.FIRST_PLAYER : SpaceOwnership.SECOND_PLAYER;
      int scoreA2 = 1;
      String reason = st == ExperimentStatus.UNCERTAIN ? "full" : "cap";
      if (st == ExperimentStatus.COLLAPSED) {
        scoreA2 = exp.getCurrentPlayer() == aColour ? 2 : 0;
        reason = history.get(history.size() - 1).isSpecialMove() ? "blast" : "six";
      }
      int placedA = 0;
      int placedB = 0;
      for (Move mv : history) {
        if (mv.player() == aColour) {
          placedA++;
        } else {
          placedB++;
        }
      }
      latencyA.merge(pa.accelerate);
      latencyB.merge(pb.accelerate);
      specialLatencyA.merge(pa.special);
      callsA.addAndGet(pa.calls());
      allocationA.merge(pa.allocation);
      searchTimeA.merge(pa.searchTime);
      outsideSearchA.merge(pa.outsideSearch);
      synchronized (depthA) {
        for (int d = 0; d < depthA.length; d++) {
          depthA[d] += pa.depthCounts[d];
        }
        nodesA += pa.searchNodes();
        nanosA += pa.searchNanos();
        long[] v = pa.vcfStats();
        for (int i = 0; i < 3; i++) {
          vcfA[i] += v[i];
        }
      }
      return new Result(pair, game, openingId, rows, cols, aFirst, scoreA2, reason, plies(exp),
          Math.max(0, pa.expectedPlacements() - placedA), Math.max(0, pb.expectedPlacements() - placedB),
          pa.invalidColumns(), pb.invalidColumns(), pa.invalidSpecials(), pb.invalidSpecials());
    }

    /** Columns of an opening line split into the first player's and the second player's scripted turns. */
    private static int[][] split(int[] line) {
      int[] first = new int[(line.length + 1) / 2];
      int[] second = new int[line.length / 2];
      for (int i = 0; i < line.length; i++) {
        if (i % 2 == 0) {
          first[i / 2] = line[i];
        } else {
          second[i / 2] = line[i];
        }
      }
      return new int[][] {first, second};
    }

    /** Random normal plies on an empty board, split per side; at most 10, so neither side can have six yet. */
    private int[][] opening(int rows, int cols, SplittableRandom rnd) {
      int n = Math.min(Math.min(openingPlies, 10), Math.max(0, rows * cols - cols));
      List<Integer> mine = new ArrayList<>();
      List<Integer> theirs = new ArrayList<>();
      int[] height = new int[cols];
      for (int i = 0; i < n; i++) {
        int c = rnd.nextInt(cols);
        for (int tries = 0; tries < cols && height[c] >= rows - 1; tries++) {
          c = (c + 1) % cols;
        }
        if (height[c] >= rows - 1) {
          break;
        }
        height[c]++;
        (i % 2 == 0 ? mine : theirs).add(c);
      }
      return new int[][] {
          mine.stream().mapToInt(Integer::intValue).toArray(), theirs.stream().mapToInt(Integer::intValue).toArray()};
    }

    private static int plies(Experiment exp) {
      try {
        java.lang.reflect.Field f = Experiment.class.getDeclaredField("iterations");
        f.setAccessible(true);
        return ((AtomicInteger) f.get(exp)).get();
      } catch (ReflectiveOperationException e) {
        return -1;
      }
    }

    static Algorithm instantiate(String spec) throws Exception {
      return BotLoader.create(spec);
    }

    /** Median completed search depth of A, or -1 if A reported none. */
    public int medianDepthA() {
      synchronized (depthA) {
        long total = 0;
        for (long n : depthA) {
          total += n;
        }
        long seen = 0;
        for (int d = 0; d < depthA.length; d++) {
          seen += depthA[d];
          if (total > 0 && 2 * seen >= total) {
            return d;
          }
        }
        return -1;
      }
    }

    public String searchSummary() {
      synchronized (depthA) {
        double nps = nanosA == 0 ? 0 : nodesA * 1e9 / nanosA;
        long calls = searchTimeA.count();
        return String.format(java.util.Locale.ROOT, "median depth %d, %.0f knodes/s, allocation per call p99 %.0f KB max %.0f KB%n"
                + "    search time %s; outside the search %s%n"
                + "    VCF: %.1f%% of nodes, %.2f proofs per call, %d defender-blast refutations",
            medianDepthA(), nps / 1000, allocationA.quantileMillis(0.99), allocationA.maxMillis(),
            searchTimeA.summary(), outsideSearchA.summary(),
            nodesA + vcfA[0] == 0 ? 0 : 100.0 * vcfA[0] / (nodesA + vcfA[0]),
            calls == 0 ? 0 : (double) vcfA[1] / calls, vcfA[2]);
      }
    }

    public String report(List<Result> results) {
      int games = results.size();
      double score = 0;
      int wins = 0;
      int draws = 0;
      int losses = 0;
      int[] reasons = new int[4];
      int tA = 0;
      int tB = 0;
      int iA = 0;
      int iB = 0;
      int sA = 0;
      int sB = 0;
      double firstScore = 0;
      for (Result r : results) {
        firstScore += r.aFirst ? r.scoreA2 / 2.0 : 1 - r.scoreA2 / 2.0;
        score += r.scoreA2 / 2.0;
        if (r.scoreA2 == 2) {
          wins++;
        } else if (r.scoreA2 == 1) {
          draws++;
        } else {
          losses++;
        }
        reasons[switch (r.reason) {
          case "six" -> 0;
          case "blast" -> 1;
          case "full" -> 2;
          default -> 3;
        }]++;
        tA += r.timeoutsA;
        tB += r.timeoutsB;
        iA += r.invalidA;
        iB += r.invalidB;
        sA += r.badSpecialsA;
        sB += r.badSpecialsB;
      }
      double pct = games == 0 ? 0 : score / games;
      double elo = pct <= 0 || pct >= 1 ? Double.NaN : -400 * Math.log10(1 / pct - 1);
      return String.format(java.util.Locale.ROOT,
          "%s vs %s: %d games, A +%d =%d -%d, score %.1f%% (Elo %+.0f)%n"
              + "  %s%n"
              + "  decided by six %d, by blast %d; draws full %d, at cap %d; draw share %.1f%%, blast share %.1f%%,"
              + " first player scores %.1f%%%n"
              + "  A: timeouts %d, invalid columns %d, invalid specials %d; accelerate %s; special %s%n"
              + "  A search: %s%n"
              + "  B: timeouts %d, invalid columns %d, invalid specials %d; accelerate %s",
          classA, classB, games, wins, draws, losses, 100 * pct, elo, sprt.summary(), reasons[0], reasons[1],
          reasons[2], reasons[3], games == 0 ? 0 : 100.0 * (reasons[2] + reasons[3]) / games,
          games == 0 ? 0 : 100.0 * reasons[1] / games, games == 0 ? 0 : 100 * firstScore / games,
          tA, iA, sA, latencyA.summary(), specialLatencyA.summary(), searchSummary(), tB, iB, sB, latencyB.summary());
    }
  }

  private MatchMain() {}
}
