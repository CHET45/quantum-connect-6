package ch.qc.tools;

import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import ch.qc.bot.search.TranspositionTable;
import ch.qc.bot.search.Vcf;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fixed-node in-process matches (design 5.2): two search configurations play colour-swapped pairs from the opening
 * pool through {@link FastGame}, with a node budget per move instead of a clock, so results are reproducible and
 * every core can be used. Output lines have the {@link MatchMain} format, so {@link ReportMain} reads them.
 *
 * <pre>
 * --a SPEC --b SPEC       search options as for qcbot: specs ("" for the defaults, "+razor,-rootVcf", "noVcf"),
 *                         optionally followed by @WEIGHTS (a .qcw file, or @hand), e.g. "@data/models/gen001/eval.qcw"
 * --nodes N               node budget per move (default 20000)
 * --ms N                  real time per move instead of nodes (run it through rtrun.py)
 * --openings FILE         opening pool (default suites/openings-ROWSxCOLS.txt)
 * --rows R --cols C       board size (default 8x12)
 * --pairs N --sprt E0,E1 --threads N --seed S --out FILE   as in MatchMain
 * --forfeitA P            A loses each own turn with probability P (what a lost turn costs)
 * --forfeitTurns N        only A's own turns below N can be lost (default all)
 * --ponderA N              after each move A searches the position after it for N nodes (the bot's second call)
 * --verifyA M              on turns with A's blast ready, those N nodes instead compare the drop and the blast in the
 *                          chosen column: M = 1 may only cancel a blast, M = 2 may also start one
 * </pre>
 */
public final class FastMatch {
  private FastMatch() {}

  /** The time limit of each search, far away unless --ms is given. */
  private static long hardNanos = Long.MAX_VALUE / 4;

  /** A search configuration as a FastGame engine with its own tables, one per game and side. */
  public static FastGame.Engine engine(Geometry g, Searcher.Options options, Evaluator eval, long nodes) {
    Searcher s = new Searcher(g, eval, new TranspositionTable(18), options, new Vcf.Table(16));
    return (Board b, int me, int cdMe, int cdOpp, int turn) ->
        s.run(b, me, cdMe, cdOpp, turn, ch.qc.bot.Clock.nanos(), hardNanos, nodes).move();
  }

  /**
   * A search configuration that also spends a second budget after its move: on the position after it (filling the
   * table for the next turn), or, with verify set, comparing the drop and the blast in the chosen column.
   */
  static FastGame.Engine pondering(Geometry g, Searcher.Options options, Evaluator eval, long nodes, long ponder,
      int verify) {
    Searcher s = new Searcher(g, eval, new TranspositionTable(18), options, new Vcf.Table(16));
    Board after = new Board(g);
    long far = Long.MAX_VALUE / 4;
    return (Board b, int me, int cdMe, int cdOpp, int turn) -> {
      int m = s.run(b, me, cdMe, cdOpp, turn, ch.qc.bot.Clock.nanos(), far, nodes).move();
      if (m == ch.qc.bot.search.Tactics.NONE) {
        return m;
      }
      int c = ch.qc.bot.search.Tactics.column(m);
      boolean special = ch.qc.bot.search.Tactics.isSpecial(m);
      int opp = 3 - me;
      int next = Math.min(cdMe + 1, 10);
      if (verify > 0 && cdMe >= 10 && !b.isFull(c) && (special || verify == 2)) {
        after.copyFrom(b);
        if (after.make(c, me)) {
          return c;
        }
        int normal = -s.run(after, opp, cdOpp, next, turn + 1, ch.qc.bot.Clock.nanos(), far, ponder / 2).score();
        after.copyFrom(b);
        if (after.blast(c)) {
          return m | ch.qc.bot.search.Tactics.SPECIAL;
        }
        int blast = -s.run(after, opp, cdOpp, 1, turn + 1, ch.qc.bot.Clock.nanos(), far, ponder / 2).score();
        return blast >= normal ? c | ch.qc.bot.search.Tactics.SPECIAL : c;
      }
      if (ponder > 0 && !special && !b.isFull(c)) {
        after.copyFrom(b);
        if (!after.make(c, me) && !after.topRowFull()) {
          s.run(after, opp, cdOpp, next, turn + 1, ch.qc.bot.Clock.nanos(), far, ponder);
        }
      }
      return m;
    };
  }

  /** The engine, but each own turn below the limit is lost with probability p (move NONE: an invalid column). */
  static FastGame.Engine forfeiting(FastGame.Engine e, double p, int turns, java.util.SplittableRandom rnd) {
    if (p <= 0) {
      return e;
    }
    int[] own = new int[1];
    return (Board b, int me, int cdMe, int cdOpp, int turn) -> {
      int t = own[0]++;
      int m = e.move(b, me, cdMe, cdOpp, turn);
      return t < turns && rnd.nextDouble() < p ? ch.qc.bot.search.Tactics.NONE : m;
    };
  }

  public static void main(String[] args) throws Exception {
    String a = "";
    String b = "";
    long nodes = 20_000;
    String openings = null;
    int rows = 8;
    int cols = 12;
    int pairs = 200;
    double[] bounds = null;
    int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    long seed = 1;
    String out = null;
    double forfeit = 0;
    int forfeitTurns = Integer.MAX_VALUE;
    long ponderA = 0;
    int verifyA = 0;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--a" -> a = args[++i];
        case "--b" -> b = args[++i];
        case "--nodes" -> nodes = Long.parseLong(args[++i]);
        case "--ms" -> {
          hardNanos = Long.parseLong(args[++i]) * 1_000_000L;
          nodes = Long.MAX_VALUE;
        }
        case "--openings" -> openings = args[++i];
        case "--rows" -> rows = Integer.parseInt(args[++i]);
        case "--cols" -> cols = Integer.parseInt(args[++i]);
        case "--pairs" -> pairs = Integer.parseInt(args[++i]);
        case "--sprt" -> {
          String[] e = args[++i].split(",");
          bounds = new double[] {Double.parseDouble(e[0]), Double.parseDouble(e[1])};
        }
        case "--threads" -> threads = Integer.parseInt(args[++i]);
        case "--seed" -> seed = Long.parseLong(args[++i]);
        case "--out" -> out = args[++i];
        case "--forfeitA" -> forfeit = Double.parseDouble(args[++i]);
        case "--forfeitTurns" -> forfeitTurns = Integer.parseInt(args[++i]);
        case "--ponderA" -> ponderA = Long.parseLong(args[++i]);
        case "--verifyA" -> verifyA = Integer.parseInt(args[++i]);
        default -> throw new IllegalArgumentException("unknown argument " + args[i]);
      }
    }
    String[] sa = (a + "@hand").split("@", 3);
    String[] sb = (b + "@hand").split("@", 3);
    Searcher.Options oa = SearchOptions.parse(sa[0]);
    Searcher.Options ob = SearchOptions.parse(sb[0]);
    java.util.function.Function<Geometry, Evaluator> fa = Evaluators.factory(sa[1]);
    java.util.function.Function<Geometry, Evaluator> fb = Evaluators.factory(sb[1]);
    if (openings == null) {
      openings = "suites/openings-" + rows + "x" + cols + ".txt";
    }
    List<int[]> pool = MatchMain.readOpenings(Path.of(openings));
    Geometry g = new Geometry(rows, cols);
    Sprt sprt = bounds == null ? new Sprt(0, 10) : new Sprt(bounds[0], bounds[1]);
    PrintWriter w = out == null ? null : new PrintWriter(Files.newBufferedWriter(Path.of(out), StandardCharsets.UTF_8));
    int[] reasons = new int[4];
    double firstScore = 0;
    int games = 0;
    long t0 = System.nanoTime();
    ExecutorService ex = Executors.newFixedThreadPool(threads);
    try {
      CompletionService<MatchMain.Result[]> done = new ExecutorCompletionService<>(ex);
      final long n = nodes;
      final long sd = seed;
      final double fp = forfeit;
      final int ft = forfeitTurns;
      final long pa = ponderA;
      final int va = verifyA;
      for (int p = 0; p < pairs; p++) {
        int pair = p;
        done.submit(() -> {
          int id = (int) ((pair + sd) % pool.size());
          int[] line = pool.get(id);
          MatchMain.Result[] r = new MatchMain.Result[2];
          for (int game = 0; game < 2; game++) {
            boolean aFirst = game == 0;
            FastGame.Engine ea = pa > 0 ? pondering(g, oa, fa.apply(g), n, pa, va)
                : forfeiting(engine(g, oa, fa.apply(g), n), fp, ft,
                    new java.util.SplittableRandom(sd * 1_000_003L + 2L * pair + game));
            FastGame.Engine eb = engine(g, ob, fb.apply(g), n);
            FastGame.Outcome o = FastGame.play(g, line, aFirst ? ea : eb, aFirst ? eb : ea, null);
            int aColour = aFirst ? 1 : 2;
            int score2 = o.winner() == 0 ? 1 : o.winner() == aColour ? 2 : 0;
            r[game] = new MatchMain.Result(pair, game, id, g.rows, g.cols, aFirst, score2, o.reason(), o.plies(),
                0, 0, 0, 0, 0, 0);
          }
          return r;
        });
      }
      for (int p = 0; p < pairs; p++) {
        MatchMain.Result[] r = done.take().get();
        sprt.add(r[0].scoreA2() + r[1].scoreA2());
        for (MatchMain.Result x : r) {
          games++;
          reasons[switch (x.reason()) {
            case "six" -> 0;
            case "blast" -> 1;
            case "full" -> 2;
            default -> 3;
          }]++;
          firstScore += x.aFirst() ? x.scoreA2() / 2.0 : 1 - x.scoreA2() / 2.0;
          if (w != null) {
            w.println(x.json());
          }
        }
        if (bounds != null && sprt.verdict() != Sprt.Verdict.CONTINUE) {
          break;
        }
      }
    } finally {
      ex.shutdownNow();
      if (w != null) {
        w.close();
      }
    }
    System.out.printf(java.util.Locale.ROOT,
        "fixed %d nodes: [%s] vs [%s]: %d games, %s%n  decided by six %d, blast %d; draws full %d, cap %d;"
            + " draw share %.1f%%, first player scores %.1f%%; %.1f min on %d threads%n",
        nodes, a, b, games, sprt.summary(), reasons[0], reasons[1], reasons[2], reasons[3],
        100.0 * (reasons[2] + reasons[3]) / games, 100 * firstScore / games, (System.nanoTime() - t0) / 6e10, threads);
  }
}
