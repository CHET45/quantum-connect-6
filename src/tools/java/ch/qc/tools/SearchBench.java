package ch.qc.tools;

import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import ch.qc.bot.search.TranspositionTable;
import ch.qc.bot.search.Vcf;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Fixed-depth search bench for move-ordering work: an ordering change keeps the minimax value, so the nodes to reach a
 * depth measure it directly, in seconds and with no game noise.
 *
 * <pre>
 * --gen N --rows R --cols C --out FILE [--nodes 3000] [--every 5] [--seed 1]   writes N positions from fixed-node self-play
 * --positions FILE --depth D --specs "A;B;C" [--weights hand|FILE] [--threads 4] [--cap NODES] [--limit N]
 *                                              fixed-depth runs; ratios are geometric means against the first spec
 * --positions FILE --budget NODES --specs "A;B"    fixed-node runs: mean completed depth and nodes per second
 * </pre>
 */
public final class SearchBench {
  private SearchBench() {}

  record Position(int rows, int cols, int stm, int cdStm, int cdOpp, int turn, byte[] cells) {}

  public static void main(String[] args) throws Exception {
    int gen = 0;
    int rows = 8;
    int cols = 12;
    String out = null;
    long nodes = 3000;
    int every = 5;
    long seed = 1;
    String positions = null;
    int depth = 0;
    long budget = 0;
    long cap = 20_000_000L;
    String specs = "";
    String weights = "hand";
    int threads = 4;
    int limit = Integer.MAX_VALUE;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--gen" -> gen = Integer.parseInt(args[++i]);
        case "--rows" -> rows = Integer.parseInt(args[++i]);
        case "--cols" -> cols = Integer.parseInt(args[++i]);
        case "--out" -> out = args[++i];
        case "--nodes" -> nodes = Long.parseLong(args[++i]);
        case "--every" -> every = Integer.parseInt(args[++i]);
        case "--seed" -> seed = Long.parseLong(args[++i]);
        case "--positions" -> positions = args[++i];
        case "--depth" -> depth = Integer.parseInt(args[++i]);
        case "--budget" -> budget = Long.parseLong(args[++i]);
        case "--cap" -> cap = Long.parseLong(args[++i]);
        case "--specs" -> specs = args[++i];
        case "--weights" -> weights = args[++i];
        case "--threads" -> threads = Integer.parseInt(args[++i]);
        case "--limit" -> limit = Integer.parseInt(args[++i]);
        default -> throw new IllegalArgumentException("unknown argument " + args[i]);
      }
    }
    if (gen > 0) {
      generate(gen, rows, cols, out, nodes, every, seed, weights);
      return;
    }
    List<Position> ps = read(Path.of(positions), limit);
    run(ps, specs.split(";", -1), weights, depth, budget, cap, threads);
  }

  private static void generate(int n, int rows, int cols, String out, long nodes, int every, long seed, String weights)
      throws Exception {
    Geometry g = new Geometry(rows, cols);
    Evaluator eval = Evaluators.factory(weights).apply(g);
    List<int[]> pool = MatchMain.readOpenings(Path.of("suites/openings-" + rows + "x" + cols + ".txt"));
    SplittableRandom rnd = new SplittableRandom(seed);
    List<String> lines = new ArrayList<>();
    int game = 0;
    while (lines.size() < n) {
      int[] opening = pool.get(rnd.nextInt(pool.size()));
      FastGame.Engine e1 = FastMatch.engine(g, Searcher.Options.DEFAULT, eval, nodes);
      FastGame.Engine e2 = FastMatch.engine(g, Searcher.Options.DEFAULT, eval, nodes);
      int offset = rnd.nextInt(every);
      int minTurn = opening.length;
      FastGame.play(g, opening, e1, e2, (b, stm, cdStm, cdOpp, turn, move) -> {
        if (turn >= minTurn && (turn + offset) % every == 0) {
          StringBuilder sb = new StringBuilder();
          byte[] cells = b.cells();
          for (byte v : cells) {
            sb.append((char) ('0' + v));
          }
          lines.add(rows + " " + cols + " " + stm + " " + cdStm + " " + cdOpp + " " + turn + " " + sb);
        }
      });
      game++;
    }
    java.util.Collections.shuffle(lines, new java.util.Random(seed));
    Files.write(Path.of(out), lines.subList(0, n), StandardCharsets.UTF_8);
    System.out.println(n + " positions from " + game + " games");
  }

  private static List<Position> read(Path file, int limit) throws Exception {
    List<Position> ps = new ArrayList<>();
    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      if (ps.size() >= limit) {
        break;
      }
      String[] f = line.trim().split(" ");
      byte[] cells = new byte[f[6].length()];
      for (int i = 0; i < cells.length; i++) {
        cells[i] = (byte) (f[6].charAt(i) - '0');
      }
      ps.add(new Position(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]),
          Integer.parseInt(f[4]), Integer.parseInt(f[5]), cells));
    }
    return ps;
  }

  private static void run(List<Position> ps, String[] specs, String weights, int depth, long budget, long cap, int threads)
      throws Exception {
    Geometry g = new Geometry(ps.get(0).rows, ps.get(0).cols);
    Evaluator eval = Evaluators.factory(weights).apply(g);
    ExecutorService ex = Executors.newFixedThreadPool(threads);
    long[][] base = null;
    try {
      for (int round = 0; round < specs.length; round++) {
        Searcher.Options o = SearchOptions.parse(specs[round]).withTrace(true);
        if (depth > 0 && o.maxDepth == 0) {
          o = o.withMaxDepth(depth);
        }
        if (budget > 0) {
          o = o.withNodeRule(false);
        }
        final Searcher.Options opts = o;
        final long limit = budget > 0 ? budget : cap;
        List<Future<long[]>> fs = new ArrayList<>();
        for (Position p : ps) {
          fs.add(ex.submit(() -> {
            // a new table per position, the bot's size: no position profits from the one before it
            TranspositionTable tt = new TranspositionTable(18);
            Searcher s = new Searcher(g, eval, tt, opts, new Vcf.Table(16));
            Board b = new Board(g);
            b.load(p.cells);
            long t0 = System.nanoTime();
            Searcher.Result r = s.run(b, p.stm, p.cdStm, p.cdOpp, p.turn, ch.qc.bot.Clock.nanos(), Long.MAX_VALUE / 4, limit);
            long t = System.nanoTime() - t0;
            long[] st = r.stats();
            long[] res = new long[6 + (st == null ? 0 : st.length)];
            res[0] = r.nodes();
            res[1] = t;
            res[2] = r.depth();
            res[3] = r.move();
            res[4] = r.score();
            res[5] = r.vcfNodes();
            if (st != null) {
              for (int i = 0; i < st.length; i++) {
                res[6 + i] = st[i];
              }
            }
            return res;
          }));
        }
        long[][] rs = new long[ps.size()][];
        for (int i = 0; i < rs.length; i++) {
          rs[i] = fs.get(i).get();
        }
        if (round == 0) {
          base = rs;
        }
        report(specs[round], rs, base);
      }
    } finally {
      ex.shutdownNow();
    }
  }

  private static void report(String spec, long[][] rs, long[][] base) {
    long nodes = 0;
    long nanos = 0;
    long vcf = 0;
    double logRatio = 0;
    double depthSum = 0;
    int counted = 0;
    int sameMove = 0;
    int sameScore = 0;
    long[] st = new long[rs[0].length];
    double diff = 0;
    double absDiff = 0;
    int scored = 0;
    for (int i = 0; i < rs.length; i++) {
      long[] r = rs[i];
      if (Math.abs(r[4]) < 20000 && Math.abs(base[i][4]) < 20000) {
        diff += r[4] - base[i][4];
        absDiff += Math.abs(r[4] - base[i][4]);
        scored++;
      }
      nodes += r[0];
      nanos += r[1];
      depthSum += r[2];
      vcf += r[5];
      for (int k = 6; k < r.length; k++) {
        st[k] += r[k];
      }
      if (r[0] >= 100 && base[i][0] >= 100) {
        logRatio += Math.log((double) r[0] / base[i][0]);
        counted++;
        if (r[3] == base[i][3]) {
          sameMove++;
        }
        if (r[4] == base[i][4]) {
          sameScore++;
        }
      }
    }
    System.out.printf(Locale.ROOT,
        "[%s] %d pos: nodes %d (geomean %.3f of base on %d), %.1f knps, mean depth %.2f, same move %.1f%%, same score %.1f%%, vcf %d%n",
        spec, rs.length, nodes, Math.exp(logRatio / Math.max(1, counted)), counted, nodes * 1e6 / Math.max(1, nanos),
        depthSum / rs.length, 100.0 * sameMove / Math.max(1, counted), 100.0 * sameScore / Math.max(1, counted), vcf);
    System.out.printf(Locale.ROOT, "   score vs base: mean %+.1f, mean abs %.1f on %d%n", diff / Math.max(1, scored),
        absDiff / Math.max(1, scored), scored);
    long interior = st[6];
    if (interior > 0) {
      long cut = st[7];
      System.out.printf(Locale.ROOT,
          "   interior %d (%.1f%% of nodes), cut %.1f%%, all %.1f%%, pv %.1f%%; first-move cuts %.1f%%, mean cut index %.2f;"
              + " tt move at %.1f%%, tt cutoffs %d; forced %.1f%%, blast children %d of %d children, leaf evals %d%n",
          interior, 100.0 * interior / Math.max(1, nodes), 100.0 * cut / interior, 100.0 * st[8] / interior,
          100.0 * st[9] / interior, 100.0 * st[10] / Math.max(1, cut), (double) st[11] / Math.max(1, cut),
          100.0 * st[12] / interior, st[13], 100.0 * st[14] / interior, st[15], st[16], st[17]);
      StringBuilder sb = new StringBuilder("   cuts by index:");
      for (int i = 0; i < 12; i++) {
        sb.append(String.format(Locale.ROOT, " %.1f", 100.0 * st[20 + i] / Math.max(1, cut)));
      }
      sb.append("   interior by depth:");
      for (int i = 1; i < 12; i++) {
        sb.append(' ').append(st[35 + i]);
      }
      sb.append("   dbg:");
      for (int i = 12; i < 13; i++) {
        sb.append(' ').append(st[6 + i]);
      }
      sb.append(" |");
      for (int i = 50; i < 57; i++) {
        sb.append(' ').append(st[6 + i]);
      }
      System.out.println(sb);
    }
  }
}
