package ch.qc.tools;

import ch.qc.bot.Clock;
import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.eval.HandEval;
import ch.qc.bot.eval.Weights;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import ch.qc.bot.search.TranspositionTable;
import ch.qc.bot.search.Vcf;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Deterministic speed bench: fixed positions per board size (real server play, see {@link #extract}), a fixed node
 * budget, one thread. Each position gets a fresh transposition table, so its search does not depend on what ran
 * before. It prints nodes per second and a signature of best move, score, depth and node count (search) and of move,
 * nodes, proofs and blast refutations (threat search). A speed change counts only if every signature stays the same.
 * The threat search is benched on its own because the search's root calls stop on a 10 ms clock, which is not
 * reproducible; the node-budget searches run with those calls off.
 *
 * <pre>
 * run:     [--positions suites/bench-positions.txt] [--sizes 8x12,15x11,15x22] [--nodes 40000] [--warm 3] [--rounds 5]
 *          [--eval bot|hand|FILE.qcw] [--sig FILE] [--vcf-only]
 * cold:    --cold --sizes 15x22 --nodes 4000 --seconds 3   one first pass in this JVM, time per position
 * build:   --build --sizes 40x40 [--after-bot] [--eval NAME]  time and bytes to build the tables of a size, twice
 * latency: --limit 25 --calls 500 --sizes 40x40 [--after-bot] [--eval NAME]  searches with that stop as in play (root VCF on, table
 *          kept): by call number the nodes, the overrun past the stop, the longest gap between clock polls
 * extract: --extract OUT [--per-tag 6] [--unseen 12x16,20x20,10x10,7x9] matches.jsonl ...
 *          --fill OUT --unseen 40x40,...   tall random boards with both blasts ready, the worst case for a node's cost
 * </pre>
 */
public final class SpeedBench {
  private static final long FAR_NANOS = 3_600_000_000_000L;
  private static final int ROOT_VCF_MOVES = 12;
  private static final long ROOT_VCF_NODES = 20_000;
  private static final int OPP_VCF_MOVES = 8;
  private static final long OPP_VCF_NODES = 8_000;
  private static final int COOLDOWN = 10;

  /** One bench position; cells in Board index order as digits. */
  record Pos(String tag, int rows, int cols, int me, int cdMe, int cdOpp, int turn, String cells) {
    String line() {
      return tag + " " + rows + " " + cols + " " + me + " " + cdMe + " " + cdOpp + " " + turn + " " + cells;
    }

    static Pos parse(String line) {
      String[] p = line.trim().split(" ");
      return new Pos(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
          Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6]), p[7]);
    }

    Board board(Geometry g) {
      byte[] c = new byte[cells.length()];
      for (int i = 0; i < c.length; i++) {
        c[i] = (byte) (cells.charAt(i) - '0');
      }
      Board b = new Board(g);
      if (!b.load(c)) {
        throw new IllegalStateException("not compact: " + line());
      }
      return b;
    }
  }

  private SpeedBench() {}

  public static void main(String[] args) throws Exception {
    String positions = "suites/bench-positions.txt";
    String sizes = "8x12,15x11,15x22";
    long nodes = 40_000;
    int warm = 3;
    int rounds = 5;
    String evalName = "bot";
    String sig = null;
    boolean vcfOnly = false;
    boolean cold = false;
    double seconds = 3;
    String extract = null;
    int perTag = 6;
    String unseen = "12x16,20x20,10x10,7x9";
    String fill = null;
    boolean build = false;
    boolean afterBot = false;
    double limit = 0;
    int calls = 300;
    List<String> files = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--positions" -> positions = args[++i];
        case "--sizes" -> sizes = args[++i];
        case "--nodes" -> nodes = Long.parseLong(args[++i]);
        case "--warm" -> warm = Integer.parseInt(args[++i]);
        case "--rounds" -> rounds = Integer.parseInt(args[++i]);
        case "--eval" -> evalName = args[++i];
        case "--sig" -> sig = args[++i];
        case "--vcf-only" -> vcfOnly = true;
        case "--cold" -> cold = true;
        case "--seconds" -> seconds = Double.parseDouble(args[++i]);
        case "--extract" -> extract = args[++i];
        case "--per-tag" -> perTag = Integer.parseInt(args[++i]);
        case "--unseen" -> unseen = args[++i];
        case "--fill" -> fill = args[++i];
        case "--build" -> build = true;
        case "--after-bot" -> afterBot = true;
        case "--limit" -> limit = Double.parseDouble(args[++i]);
        case "--calls" -> calls = Integer.parseInt(args[++i]);
        default -> files.add(args[i]);
      }
    }
    if (extract != null) {
      extract(Path.of(extract), files, perTag, unseen);
      return;
    }
    if (fill != null) {
      fillPositions(Path.of(fill), unseen);
      return;
    }
    if (build) {
      buildTimes(sizes, evalName, afterBot);
      return;
    }
    if (afterBot && limit > 0) {
      new ch.qc.bot.QcBot(); // its warm-up runs, so the first calls below are those of a size it did not prepare
    }
    Map<String, List<Pos>> bySize = load(Path.of(positions));
    PrintWriter w = sig == null ? null : new PrintWriter(Files.newBufferedWriter(Path.of(sig), StandardCharsets.UTF_8));
    try {
      for (String size : sizes.split(",")) {
        List<Pos> list = bySize.get(size.trim());
        if (list == null) {
          throw new IllegalArgumentException("no positions for " + size);
        }
        Geometry g = new Geometry(list.get(0).rows, list.get(0).cols);
        Evaluator e = evaluator(g, evalName);
        if (limit > 0) {
          latency(size.trim(), g, e, list, limit, calls);
        } else if (cold) {
          cold(g, e, list, nodes, seconds);
        } else {
          hot(size.trim(), g, e, list, nodes, warm, rounds, w, vcfOnly);
        }
      }
    } finally {
      if (w != null) {
        w.close();
      }
    }
  }

  private static Map<String, List<Pos>> load(Path file) throws Exception {
    Map<String, List<Pos>> out = new LinkedHashMap<>();
    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      if (line.isBlank() || line.startsWith("#")) {
        continue;
      }
      Pos p = Pos.parse(line);
      out.computeIfAbsent(p.rows + "x" + p.cols, k -> new ArrayList<>()).add(p);
    }
    return out;
  }

  /** Counts evaluations (bench option --eval count:NAME); slows the run. */
  private static final class Counting implements Evaluator {
    private final Evaluator inner;
    long calls;

    Counting(Evaluator inner) {
      this.inner = inner;
    }

    @Override
    public int evaluate(Board b, int stm, int cdStm, int cdOpp) {
      calls++;
      return inner.evaluate(b, stm, cdStm, cdOpp);
    }
  }

  private static Evaluator evaluator(Geometry g, String name) throws Exception {
    if (name.startsWith("count:")) {
      return new Counting(evaluator(g, name.substring(6)));
    }
    if (name.equals("bot")) {
      Evaluator e = Weights.forGeometry(g);
      return e != null ? e : new HandEval(g);
    }
    return name.equals("hand") ? new HandEval(g) : WeightFiles.load(Path.of(name), g);
  }

  private static Searcher searcher(Geometry g, Evaluator e) {
    return new Searcher(g, e, new TranspositionTable(18), Searcher.Options.DEFAULT.withSwitches(false, false, false,
        true, false), new Vcf.Table(16));
  }

  private static void hot(String size, Geometry g, Evaluator e, List<Pos> list, long nodes, int warm, int rounds,
      PrintWriter w, boolean vcfOnly) {
    int n = list.size();
    Board[] boards = new Board[n];
    for (int i = 0; i < n; i++) {
      boards[i] = list.get(i).board(g);
    }
    long[] searchBest = new long[n];
    Arrays.fill(searchBest, Long.MAX_VALUE);
    long[] vcfBest = new long[n];
    Arrays.fill(vcfBest, Long.MAX_VALUE);
    long[] searchRounds = new long[rounds];
    long[] vcfRounds = new long[rounds];
    String[] searchSig = new String[n];
    String[] vcfSig = new String[n];
    long totalNodes = 0;
    long totalVcfNodes = 0;
    for (int r = -warm; r < rounds; r++) {
      long searchSum = 0;
      long vcfSum = 0;
      for (int i = 0; i < n; i++) {
        Pos p = list.get(i);
        if (!vcfOnly) {
          Searcher s = searcher(g, e);
          long t0 = System.nanoTime();
          Searcher.Result res = s.run(boards[i], p.me, p.cdMe, p.cdOpp, p.turn, Clock.nanos(), FAR_NANOS, nodes);
          long dt = System.nanoTime() - t0;
          String one = res.move() + " " + res.score() + " " + res.depth() + " " + res.nodes();
          check(searchSig, i, one, size);
          if (r >= 0) {
            searchBest[i] = Math.min(searchBest[i], dt);
            searchSum += dt;
          }
          if (r == 0) {
            totalNodes += res.nodes();
          }
        }
        Vcf v = new Vcf(g, new Vcf.Table(16), true);
        long t0 = System.nanoTime();
        int mine = v.attack(boards[i], p.me, p.cdMe, p.cdOpp, ROOT_VCF_MOVES, p.turn, Clock.nanos() + FAR_NANOS,
            ROOT_VCF_NODES);
        int theirs = v.attack(boards[i], 3 - p.me, p.cdOpp, p.cdMe, OPP_VCF_MOVES, p.turn + 1,
            Clock.nanos() + FAR_NANOS, OPP_VCF_NODES);
        long dt = System.nanoTime() - t0;
        String one = mine + " " + theirs + " " + v.nodes + " " + v.proofs + " " + v.blastRefutations;
        check(vcfSig, i, one, size);
        if (r >= 0) {
          vcfBest[i] = Math.min(vcfBest[i], dt);
          vcfSum += dt;
        }
        if (r == 0) {
          totalVcfNodes += v.nodes;
        }
      }
      if (r >= 0) {
        searchRounds[r] = searchSum;
        vcfRounds[r] = vcfSum;
      }
    }
    Arrays.sort(searchRounds);
    Arrays.sort(vcfRounds);
    long sigS = 0;
    long sigV = 0;
    for (int i = 0; i < n; i++) {
      sigS = 31 * sigS + (searchSig[i] == null ? 0 : searchSig[i].hashCode());
      sigV = 31 * sigV + vcfSig[i].hashCode();
      if (w != null) {
        w.println(size + " " + i + " " + list.get(i).tag + " search " + searchSig[i] + " vcf " + vcfSig[i]);
      }
    }
    long bestSum = 0;
    Map<String, long[]> tags = new LinkedHashMap<>();
    if (!vcfOnly) {
      for (int i = 0; i < n; i++) {
        bestSum += searchBest[i];
        long[] t = tags.computeIfAbsent(list.get(i).tag, k -> new long[2]);
        t[0] += Long.parseLong(searchSig[i].split(" ")[3]);
        t[1] += searchBest[i];
      }
    }
    StringBuilder tagText = new StringBuilder();
    for (Map.Entry<String, long[]> t : tags.entrySet()) {
      tagText.append(String.format(Locale.ROOT, " %s=%.0f", t.getKey(), knps(t.getValue()[0], t.getValue()[1])));
    }
    long vcfBestSum = Arrays.stream(vcfBest).sum();
    if (e instanceof Counting c) {
      System.out.printf(Locale.ROOT, "evaluations per node %.3f%n", c.calls / (double) (totalNodes * (warm + rounds)));
    }
    System.out.printf(Locale.ROOT,
        "%-6s %s pos=%d nodes=%d knps best=%.0f median=%.0f | vcf nodes=%d kn/s best=%.0f median=%.0f | sig %016x %016x%n"
            + "       by tag:%s%n",
        size, e.getClass().getSimpleName(), n, totalNodes, knps(totalNodes, bestSum),
        knps(totalNodes, searchRounds[rounds / 2]), totalVcfNodes, knps(totalVcfNodes, vcfBestSum),
        knps(totalVcfNodes, vcfRounds[rounds / 2]), sigS, sigV, tagText);
    System.out.flush();
  }

  private static void check(String[] sig, int i, String one, String size) {
    if (sig[i] != null && !sig[i].equals(one)) {
      throw new IllegalStateException(size + " position " + i + " is not reproducible: " + sig[i] + " vs " + one);
    }
    sig[i] = one;
  }

  private static double knps(long nodes, long nanos) {
    return nanos == 0 ? 0 : nodes * 1e6 / nanos;
  }

  /** One first pass in a fresh JVM: per position the elapsed time and nodes, until the time is used up. */
  private static void cold(Geometry g, Evaluator e, List<Pos> list, long nodes, double seconds) {
    long start = System.nanoTime();
    long sum = 0;
    StringBuilder sb = new StringBuilder();
    for (int pass = 0; (System.nanoTime() - start) / 1e9 < seconds; pass++) {
      for (int i = 0; i < list.size() && (System.nanoTime() - start) / 1e9 < seconds; i++) {
        Pos p = list.get(i);
        Board b = p.board(g);
        Searcher s = searcher(g, e);
        long t0 = System.nanoTime();
        Searcher.Result res = s.run(b, p.me, p.cdMe, p.cdOpp, p.turn, Clock.nanos(), FAR_NANOS, nodes);
        long t1 = System.nanoTime();
        sum += res.nodes();
        sb.append(String.format(Locale.ROOT, "%.1f %d %.1f%n", (t1 - start) / 1e6, sum, (t1 - t0) / 1e6));
      }
    }
    System.out.print(sb);
  }

  private static long allocated() {
    return ((com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean())
        .getCurrentThreadAllocatedBytes();
  }

  /** What a board size without prepared tables costs: each step in ms and kB, cold on the first pass, then again. */
  private static void buildTimes(String sizes, String evalName, boolean afterBot) throws Exception {
    if (afterBot) {
      new ch.qc.bot.QcBot();
    }
    for (int pass = 0; pass < 2; pass++) {
      for (String size : sizes.split(",")) {
        String[] rc = size.trim().split("x");
        int rows = Integer.parseInt(rc[0]);
        int cols = Integer.parseInt(rc[1]);
        long a0 = allocated();
        long t0 = System.nanoTime();
        Geometry g = new Geometry(rows, cols);
        long t1 = System.nanoTime();
        long a1 = allocated();
        Evaluator e = evaluator(g, evalName);
        long t2 = System.nanoTime();
        long a2 = allocated();
        TranspositionTable tt = new TranspositionTable(10);
        Vcf.Table vt = new Vcf.Table(10);
        long t3 = System.nanoTime();
        long a3 = allocated();
        Searcher s = new Searcher(g, e, tt, Searcher.Options.DEFAULT, vt);
        long t4 = System.nanoTime();
        long a4 = allocated();
        Board b = new Board(g);
        long t5 = System.nanoTime();
        long a5 = allocated();
        b.load(new byte[g.cells]);
        long t6 = System.nanoTime();
        System.out.printf(Locale.ROOT,
            "pass %d %-8s windows %5d  ms: geometry %.2f eval %.2f searcher %.2f board %.2f load %.2f total %.2f"
                + "  kB: geometry %d eval %d searcher %d board %d  (%s)%n",
            pass, size.trim(), g.windows, (t1 - t0) / 1e6, (t2 - t1) / 1e6, (t4 - t3) / 1e6, (t5 - t4) / 1e6,
            (t6 - t5) / 1e6, (t6 - t0 - (t3 - t2)) / 1e6, (a1 - a0) / 1024, (a2 - a1) / 1024, (a4 - a3) / 1024,
            (a5 - a4) / 1024, e.getClass().getSimpleName());
        if (s.hashCode() == 0) {
          System.out.println();
        }
      }
    }
  }

  /** Searches as in play at a fixed stop: nodes, overrun past the stop and longest poll gap, by call number. */
  private static void latency(String size, Geometry g, Evaluator e, List<Pos> list, double limitMs, int calls) {
    long hard = (long) (limitMs * 1e6);
    Searcher s = new Searcher(g, e, new TranspositionTable(18), Searcher.Options.DEFAULT, new Vcf.Table(16));
    Vcf v = new Vcf(g, new Vcf.Table(16), true);
    int[] edges = {1, 3, 10, 30, 100, 300, 1000, 3000, 10000};
    int from = 0;
    System.out.printf(Locale.ROOT, "%s %s stop %.0f ms, windows %d%n", size, e.getClass().getSimpleName(), limitMs,
        g.windows);
    for (int edge : edges) {
      int to = Math.min(edge, calls);
      if (to <= from) {
        continue;
      }
      long[] over = new long[to - from];
      long maxGap = 0;
      long maxVcf = 0;
      long sumNodes = 0;
      for (int i = from; i < to; i++) {
        Pos p = list.get(i % list.size());
        Board b = p.board(g);
        long t0 = System.nanoTime();
        Searcher.Result r = s.run(b, p.me, p.cdMe, p.cdOpp, p.turn, Clock.nanos(), hard);
        over[i - from] = System.nanoTime() - t0 - hard;
        maxGap = Math.max(maxGap, s.lastMaxGapNanos());
        sumNodes += r.nodes();
        // the threat search alone, with no node limit to stop it: how far past a 1 ms deadline does it run?
        long t1 = System.nanoTime();
        v.attack(b, p.me, p.cdMe, p.cdOpp, ROOT_VCF_MOVES, p.turn, Clock.nanos() + 1_000_000L, Long.MAX_VALUE / 4);
        maxVcf = Math.max(maxVcf, System.nanoTime() - t1 - 1_000_000L);
      }
      Arrays.sort(over);
      System.out.printf(Locale.ROOT,
          "  calls %5d-%-5d mean knodes %7.1f  overrun ms p50 %6.2f p99 %6.2f max %6.2f  max poll gap ms %5.1f"
              + "  vcf past 1 ms deadline, max ms %6.2f%n",
          from, to - 1, sumNodes / 1000.0 / (to - from), over[over.length / 2] / 1e6, over[over.length * 99 / 100] / 1e6,
          over[over.length - 1] / 1e6, maxGap / 1e6, maxVcf / 1e6);
      from = to;
    }
    System.out.flush();
  }

  /** Samples positions from server games (per tag and size) and from short self-play on unseen sizes. */
  private static void extract(Path out, List<String> files, int perTag, String unseen) throws Exception {
    SplittableRandom rnd = new SplittableRandom(20261104L);
    Map<String, List<Pos>> pools = new LinkedHashMap<>();
    int games = 0;
    for (String f : files) {
      for (String line : Files.readAllLines(Path.of(f), StandardCharsets.UTF_8)) {
        if (line.isBlank()) {
          continue;
        }
        ServerGames.Game game = ServerGames.game(line);
        ServerGames.Replay r = ServerGames.replayAny(game, false, 0, 0);
        if (r.problem != null || !r.how.equals("recorded")) {
          continue;
        }
        games++;
        sample(r.geo, game.moves(), pools);
      }
    }
    List<String> lines = new ArrayList<>();
    lines.add("# bench positions: server games (" + games + " games) and self-play on unseen sizes");
    lines.add("# tag rows cols me cdMe cdOpp turn cells(col*rows+y)");
    for (Map.Entry<String, List<Pos>> e : pools.entrySet()) {
      List<Pos> pool = new ArrayList<>(e.getValue());
      Collections.shuffle(pool, new java.util.Random(rnd.nextLong()));
      int taken = 0;
      for (Pos p : pool) {
        if (taken >= perTag) {
          break;
        }
        if (usable(p)) {
          lines.add(p.line());
          taken++;
        }
      }
    }
    for (String size : unseen.split(",")) {
      if (!size.isBlank()) {
        selfPlay(size.trim(), rnd, lines);
      }
    }
    Files.write(out, lines, StandardCharsets.UTF_8);
    System.out.println("wrote " + (lines.size() - 2) + " positions from " + games + " games");
  }

  /** Random boards filled to 30 to 85% with both blasts ready: tall columns make blasts cost the most. */
  private static void fillPositions(Path out, String sizes) throws Exception {
    SplittableRandom rnd = new SplittableRandom(20261105L);
    List<String> lines = new ArrayList<>();
    for (String size : sizes.split(",")) {
      String[] rc = size.trim().split("x");
      Geometry g = new Geometry(Integer.parseInt(rc[0]), Integer.parseInt(rc[1]));
      int taken = 0;
      for (int tries = 0; tries < 200 && taken < 8; tries++) {
        Board b = new Board(g);
        int want = g.cells * (30 + 55 * taken / 7) / 100;
        int colour = 1;
        int stuck = 0;
        while (b.discs() < want && stuck < 4 * g.cells) {
          int c = rnd.nextInt(g.cols);
          if (b.isFull(c) || b.height(c) + 1 >= g.rows || b.threats(colour, g.index(c, b.height(c))) > 0) {
            stuck++;
            continue;
          }
          b.make(c, colour);
          colour = 3 - colour;
        }
        Pos p = pos("fill", g, b, colour, COOLDOWN, COOLDOWN, 2 * COOLDOWN + b.discs());
        if (usable(p)) {
          lines.add(p.line());
          taken++;
        }
      }
    }
    Files.write(out, lines, StandardCharsets.UTF_8);
    System.out.println("wrote " + lines.size() + " positions");
  }

  /** Walks a game (as {@link ServerGames#replay} does) and files each position under its tag. */
  private static void sample(Geometry g, String s, Map<String, List<Pos>> pools) {
    Board b = new Board(g);
    int[] counter = new int[3];
    int expected = 1;
    int turn = 0;
    int i = 0;
    while (i < s.length()) {
      int side = s.charAt(i) == 'F' ? 1 : 2;
      int col = Character.digit(s.charAt(i + 1), 36);
      boolean star = i + 2 < s.length() && s.charAt(i + 2) == '*';
      i += star ? 3 : 2;
      if (side != expected) {
        counter[expected]++;
        turn++;
        expected = side;
      }
      String tag = tag(g, b, counter[side], counter[3 - side], turn);
      if (tag != null) {
        pools.computeIfAbsent(g.rows + "x" + g.cols + " " + tag, k -> new ArrayList<>())
            .add(pos(tag, g, b, side, counter[side], counter[3 - side], turn));
      }
      if (star) {
        b.blast(col);
        counter[side] = 0;
      } else {
        b.make(col, side);
      }
      counter[side]++;
      turn++;
      expected = 3 - side;
    }
  }

  private static Pos pos(String tag, Geometry g, Board b, int me, int cdMe, int cdOpp, int turn) {
    StringBuilder sb = new StringBuilder(g.cells);
    for (byte v : b.cells()) {
      sb.append((char) ('0' + v));
    }
    return new Pos(tag, g.rows, g.cols, me, cdMe, cdOpp, turn, sb.toString());
  }

  private static String tag(Geometry g, Board b, int cdMe, int cdOpp, int turn) {
    double fill = b.discs() / (double) g.cells;
    boolean mine = cdMe >= COOLDOWN;
    boolean theirs = cdOpp >= COOLDOWN;
    if (turn >= 4 && turn <= 16) {
      return "early";
    }
    if (turn < 20) {
      return null;
    }
    if (mine && theirs) {
      return fill > 0.35 ? "bothLate" : "both";
    }
    if (mine) {
      return "mine";
    }
    if (theirs) {
      return "theirs";
    }
    return "none";
  }

  /** A position the search really searches: no root win, a choice of moves, no quick proof. */
  private static boolean usable(Pos p) {
    Geometry g = new Geometry(p.rows, p.cols);
    Board b = p.board(g);
    int want = Math.min(4000, 20 * g.cells);
    Searcher.Result r = searcher(g, new HandEval(g)).run(b, p.me, p.cdMe, p.cdOpp, p.turn, Clock.nanos(), FAR_NANOS, want);
    return r.nodes() * 3 > want && Math.abs(r.score()) <= Searcher.MATE_BOUND;
  }

  /** Positions for a size the server has not shown yet, from a short deterministic self-play game. */
  private static void selfPlay(String size, SplittableRandom rnd, List<String> lines) {
    String[] rc = size.split("x");
    Geometry g = new Geometry(Integer.parseInt(rc[0]), Integer.parseInt(rc[1]));
    Evaluator e = new HandEval(g);
    int[] taken = new int[1];
    for (int game = 0; game < 40 && taken[0] < 14; game++) {
      int[] opening = new int[6];
      for (int i = 0; i < opening.length; i++) {
        opening[i] = rnd.nextInt(g.cols);
      }
      FastGame.Engine engine = (b, me, cdMe, cdOpp, turn) -> searcher(g, e)
          .run(b, me, cdMe, cdOpp, turn, Clock.nanos(), FAR_NANOS, 2000).move();
      FastGame.Observer obs = (b, stm, cdStm, cdOpp, turn, move) -> {
        if (taken[0] < 14 && (turn == 10 || turn == 24 || turn == 40 || turn == 80 || turn == 160)) {
          String tag = tag(g, b, cdStm, cdOpp, turn);
          if (tag != null) {
            Pos p = pos(tag, g, b, stm, cdStm, cdOpp, turn);
            if (usable(p)) {
              lines.add(p.line());
              taken[0]++;
            }
          }
        }
      };
      try {
        FastGame.play(g, opening, engine, engine, obs);
      } catch (IllegalArgumentException ex) {
        continue; // the random opening made a line or overflowed a column
      }
    }
  }
}
