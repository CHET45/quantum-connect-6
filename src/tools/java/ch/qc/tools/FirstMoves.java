package ch.qc.tools;

import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import ch.qc.bot.search.Tactics;
import ch.qc.bot.search.TranspositionTable;
import ch.qc.bot.search.Vcf;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Is there a rule for the first moves on the empty board that holds on any size? Two equal fixed-node engines
 * (generic tables, jittered budgets, no book) play on from a forced start; the only difference between arms is that start.
 *
 * <pre>
 * first  F's first move at each offset from the centre (0, 1, 2, ... to the edge); score of F
 * reply  after F in the centre: S at each offset from the centre, among them 0, the same column; score of S
 * rule   a whole rule against plain search, colour-swapped: "centre" A opens in the centre, B searches;
 *        "stack" A as S answers in the column F just played on its first turn, otherwise searches; B searches;
 *        "near" A's first K moves stay within the columns next to the centre, chosen by search among them
 * </pre>
 *
 * --sizes 6x7,7x9,... --mode first|reply|rule --rule NAME --k K --weights FILE --nodes N --games N --threads N --out FILE
 * --offsets 0,-2,s (these arms only: offsets from the centre, s = the search's own choice) --seed S (other games)
 */
public final class FirstMoves {
  private FirstMoves() {}

  private static final long NO_TIME_LIMIT = Long.MAX_VALUE / 4;

  public static void main(String[] args) throws Exception {
    String sizes = "6x7,7x9,10x10,12x16,9x20,20x9,8x12,15x11,15x22";
    String mode = "first";
    String rule = "centre";
    int k = 4;
    String weights = "weights/eval-generic.qcw";
    long nodes = 40_000;
    int games = 100;
    int threads = 2;
    int maxOffset = 4;
    String offsets = null;
    long seedOffset = 0;
    Path out = null;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--sizes" -> sizes = args[++i];
        case "--mode" -> mode = args[++i];
        case "--rule" -> rule = args[++i];
        case "--k" -> k = Integer.parseInt(args[++i]);
        case "--weights" -> weights = args[++i];
        case "--nodes" -> nodes = Long.parseLong(args[++i]);
        case "--games" -> games = Integer.parseInt(args[++i]);
        case "--threads" -> threads = Integer.parseInt(args[++i]);
        case "--max-offset" -> maxOffset = Integer.parseInt(args[++i]);
        case "--offsets" -> offsets = args[++i];
        case "--seed" -> seedOffset = Long.parseLong(args[++i]);
        case "--out" -> out = Path.of(args[++i]);
        default -> throw new IllegalArgumentException("unknown argument " + args[i]);
      }
    }
    PrintWriter w = out == null ? null : new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8,
        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND), true);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      for (int[] size : MatchMain.parseSizes(sizes)) {
        Geometry g = new Geometry(size[0], size[1]);
        Evaluator eval = Evaluators.factory(weights).apply(g);
        if (mode.equals("rule")) {
          ruleArm(g, eval, rule, k, nodes, games, pool, w);
        } else {
          arms(g, eval, mode.equals("reply"), nodes, games, maxOffset, offsets, seedOffset, pool, w);
        }
      }
    } finally {
      pool.shutdownNow();
      if (w != null) {
        w.close();
      }
    }
  }

  private static void arms(Geometry g, Evaluator eval, boolean reply, long nodes, int games, int maxOffset, String offsets,
      long seedOffset, ExecutorService pool, PrintWriter w) throws Exception {
    int centre = (g.cols - 1) / 2;
    int side = reply ? 2 : 1;
    StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%dx%d %s", g.rows, g.cols, reply ? "reply" : "first"));
    Board start = new Board(g);
    if (reply) {
      start.make(centre, 1);
    }
    Searcher plain = new Searcher(g, eval, new TranspositionTable(18), Searcher.Options.DEFAULT, new Vcf.Table(16));
    int searched = Tactics.column(plain.run(start, side, 0, reply ? 1 : 0, reply ? 1 : 0, ch.qc.bot.Clock.nanos(), NO_TIME_LIMIT, nodes).move());
    line.append(" | search plays col ").append(searched).append(", centre ").append(centre);
    List<Integer> list = new ArrayList<>();
    if (offsets == null) {
      for (int offset = -maxOffset; offset <= maxOffset; offset++) {
        list.add(offset);
      }
    } else {
      for (String o : offsets.split(",")) {
        // "s" is the search's own choice from this position
        list.add(o.equals("s") ? searched - centre : Integer.parseInt(o));
      }
    }
    for (int offset : list) {
      int col = centre + offset;
      if (col < 0 || col >= g.cols) {
        continue;
      }
      if (offsets != null && (!reply || g.cols % 2 == 1)) {
        col = Math.min(col, g.cols - 1 - col);
      }
      // the mirror of a column is the same position (after F in the centre only if the centre is on the axis)
      if ((!reply || g.cols % 2 == 1) && col > g.cols - 1 - col) {
        continue;
      }
      int[] opening = reply ? new int[] {centre, col} : new int[] {col};
      double[] r = rollout(g, eval, opening, side, nodes, games, pool,
          (g.rows * 1009L + g.cols) * 31 + col + (reply ? 7 : 0) + 1_000_000L * seedOffset);
      String cell = String.format(Locale.ROOT, "col %d: %.1f%%", col, r[0]);
      line.append(" | ").append(cell);
      if (w != null) {
        w.printf(Locale.ROOT, "{\"size\":\"%dx%d\",\"mode\":\"%s\",\"col\":%d,\"centre\":%d,\"searched\":" + searched
                + ",\"seed\":" + seedOffset + ",\"games\":%d,\"score\":%.2f,\"draws\":%d,\"nodes\":%d}%n",
            g.rows, g.cols, reply ? "reply" : "first", col, centre, games, r[0], (int) r[1], nodes);
        w.flush();
      }
    }
    System.out.println(line);
  }

  private static double[] rollout(Geometry g, Evaluator eval, int[] opening, int side, long nodes, int games, ExecutorService pool,
      long seedBase) throws Exception {
    List<Future<Double>> done = new ArrayList<>();
    for (int i = 0; i < games; i++) {
      long seed = seedBase * 9_000_001L + i;
      done.add(pool.submit(() -> {
        FastGame.Engine f = BookFast.engine(g, eval, nodes, 0.3, seed, null, null);
        FastGame.Engine s = BookFast.engine(g, eval, nodes, 0.3, seed ^ 0x5DEECE66DL, null, null);
        FastGame.Outcome o = FastGame.play(g, opening, f, s, null);
        return o.winner() == 0 ? 0.5 : o.winner() == side ? 1.0 : 0.0;
      }));
    }
    double sum = 0;
    int draws = 0;
    for (Future<Double> f : done) {
      double x = f.get();
      sum += x;
      draws += x == 0.5 ? 1 : 0;
    }
    return new double[] {100 * sum / games, draws};
  }

  /** A plain engine with a rule on top: returns the rule's column on the turns it speaks, otherwise the search's move. */
  private static FastGame.Engine ruled(Geometry g, FastGame.Engine plain, Evaluator eval, long nodes, long seed, String rule, int k) {
    Searcher s = new Searcher(g, eval, new TranspositionTable(18), Searcher.Options.DEFAULT, new Vcf.Table(16));
    int centre = (g.cols - 1) / 2;
    return (Board b, int me, int cdMe, int cdOpp, int turn) -> {
      switch (rule) {
        case "centre":
          if (b.discs() == 0) {
            return centre;
          }
          break;
        case "stack":
          // as S, the first reply goes on top of F's first disc
          if (b.discs() == 1 && turn == 1) {
            for (int c = 0; c < g.cols; c++) {
              if (b.height(c) == 1 && !b.isFull(c)) {
                return c;
              }
            }
          }
          break;
        case "near":
          // the first K own moves stay in the columns around the centre, the search chooses among them
          if (turn / 2 < k && turn == b.discs()) {
            return nearCentre(g, b, s, me, cdMe, cdOpp, turn, nodes, centre, plain);
          }
          break;
        default:
          throw new IllegalArgumentException("unknown rule " + rule);
      }
      return plain.move(b, me, cdMe, cdOpp, turn);
    };
  }

  /** The search's move if it stays within one column of the centre pair, else the best of those columns by a child search. */
  private static int nearCentre(Geometry g, Board b, Searcher s, int me, int cdMe, int cdOpp, int turn, long nodes, int centre,
      FastGame.Engine plain) {
    int move = plain.move(b, me, cdMe, cdOpp, turn);
    int lo = centre - 1;
    int hi = g.cols - 1 - centre + 1;
    if (move != Tactics.NONE && !Tactics.isSpecial(move) && Tactics.column(move) >= lo && Tactics.column(move) <= hi) {
      return move;
    }
    int best = move;
    int bestScore = Integer.MIN_VALUE;
    Board child = new Board(g);
    for (int c = Math.max(0, lo); c <= Math.min(g.cols - 1, hi); c++) {
      if (b.isFull(c)) {
        continue;
      }
      child.copyFrom(b);
      if (child.make(c, me)) {
        return c;
      }
      int score = -s.run(child, 3 - me, cdOpp, Math.min(cdMe + 1, 10), turn + 1, ch.qc.bot.Clock.nanos(), NO_TIME_LIMIT,
          nodes / 3).score();
      if (score > bestScore) {
        bestScore = score;
        best = c;
      }
    }
    return best;
  }

  private static void ruleArm(Geometry g, Evaluator eval, String rule, int k, long nodes, int games, ExecutorService pool,
      PrintWriter w) throws Exception {
    List<Future<double[]>> done = new ArrayList<>();
    for (int i = 0; i < games / 2; i++) {
      long seed = (g.rows * 1009L + g.cols) * 77_000_001L + i;
      for (int game = 0; game < 2; game++) {
        boolean aFirst = game == 0;
        long gs = seed * 2 + game;
        done.add(pool.submit(() -> {
          FastGame.Engine pa = BookFast.engine(g, eval, nodes, 0.3, gs, null, new AtomicLong());
          FastGame.Engine pb = BookFast.engine(g, eval, nodes, 0.3, gs ^ 0x5DEECE66DL, null, new AtomicLong());
          FastGame.Engine a = ruled(g, pa, eval, nodes, gs, rule, k);
          FastGame.Outcome o = FastGame.play(g, new int[0], aFirst ? a : pb, aFirst ? pb : a, null);
          int aColour = aFirst ? 1 : 2;
          return new double[] {aFirst ? 1 : 0, o.winner() == 0 ? 0.5 : o.winner() == aColour ? 1 : 0};
        }));
      }
    }
    double[] sum = new double[2];
    double[] count = new double[2];
    double[] squares = new double[2];
    for (Future<double[]> f : done) {
      double[] r = f.get();
      int side = (int) r[0];
      sum[side] += r[1];
      squares[side] += r[1] * r[1];
      count[side]++;
    }
    double n = count[0] + count[1];
    double total = sum[0] + sum[1];
    double mean = total / n;
    double var = (squares[0] + squares[1]) / n - mean * mean;
    double h = 1.96 * Math.sqrt(var / n);
    String text = String.format(Locale.ROOT, "%dx%d rule %s k=%d: %.1f%% +-%.1f of %d; as F %.1f%%, as S %.1f%%", g.rows, g.cols, rule,
        k, 100 * mean, 100 * h, (int) n, 100 * sum[1] / count[1], 100 * sum[0] / count[0]);
    System.out.println(text);
    if (w != null) {
      w.printf(Locale.ROOT, "{\"size\":\"%dx%d\",\"mode\":\"rule\",\"rule\":\"%s\",\"k\":%d,\"games\":%d,\"score\":%.2f,\"ci\":%.2f,"
              + "\"asF\":%.2f,\"asS\":%.2f,\"nodes\":%d}%n",
          g.rows, g.cols, rule, k, (int) n, 100 * mean, 100 * h, 100 * sum[1] / count[1], 100 * sum[0] / count[0], nodes);
    }
  }
}
