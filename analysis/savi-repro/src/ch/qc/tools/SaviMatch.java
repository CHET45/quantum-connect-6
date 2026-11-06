package ch.qc.tools;

import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Colour-swapped fixed-node pairs where each side has its own options, evaluation and node budget.
 *
 * <pre>SaviMatch --a "lmr=0@hand@30000" --b "lmr=0@hand@150000" --size 15x11 --openings FILE --pairs 50 --threads 2 --seed 1</pre>
 */
public final class SaviMatch {
  private record Side(Searcher.Options options, Function<Geometry, Evaluator> eval, long nodes) {
    static Side parse(String s) {
      String[] p = s.split("@", 3);
      return new Side(SearchOptions.parse(p[0]), Evaluators.factory(p[1]), Long.parseLong(p[2]));
    }
  }

  private SaviMatch() {}

  public static void main(String[] args) throws Exception {
    String a = null;
    String b = null;
    String size = "15x11";
    String openings = null;
    int pairs = 50;
    int threads = 2;
    int seed = 1;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--a" -> a = args[++i];
        case "--b" -> b = args[++i];
        case "--size" -> size = args[++i];
        case "--openings" -> openings = args[++i];
        case "--pairs" -> pairs = Integer.parseInt(args[++i]);
        case "--threads" -> threads = Integer.parseInt(args[++i]);
        case "--seed" -> seed = Integer.parseInt(args[++i]);
        default -> throw new IllegalArgumentException(args[i]);
      }
    }
    String[] rc = size.split("x");
    Geometry g = new Geometry(Integer.parseInt(rc[0]), Integer.parseInt(rc[1]));
    Side sa = Side.parse(a);
    Side sb = Side.parse(b);
    List<int[]> pool = MatchMain.readOpenings(Path.of(openings));
    int[] next = {0};
    double[] scores = new double[pairs];
    int[] wdl = new int[3];
    int[] seat = new int[2];
    long[] pliesSum = {0};
    int total = pairs;
    int sd = seed;
    Thread[] workers = new Thread[threads];
    for (int t = 0; t < threads; t++) {
      workers[t] = new Thread(() -> {
        while (true) {
          int p;
          synchronized (next) {
            p = next[0]++;
          }
          if (p >= total) {
            return;
          }
          int[] line = pool.get(Math.floorMod(p + sd, pool.size()));
          double s = 0;
          for (int game = 0; game < 2; game++) {
            boolean aFirst = game == 0;
            FastGame.Engine ea = FastMatch.engine(g, sa.options, sa.eval.apply(g), sa.nodes);
            FastGame.Engine eb = FastMatch.engine(g, sb.options, sb.eval.apply(g), sb.nodes);
            FastGame.Outcome o = FastGame.play(g, line, aFirst ? ea : eb, aFirst ? eb : ea, null);
            int aColour = aFirst ? 1 : 2;
            double r = o.winner() == 0 ? 0.5 : o.winner() == aColour ? 1 : 0;
            s += r;
            synchronized (wdl) {
              wdl[o.winner() == 0 ? 1 : o.winner() == aColour ? 0 : 2]++;
              pliesSum[0] += o.plies();
              if (aFirst) {
                seat[0] += r > 0.5 ? 1 : 0;
              } else {
                seat[1] += r > 0.5 ? 1 : 0;
              }
            }
          }
          scores[p] = s / 2;
        }
      });
      workers[t].start();
    }
    for (Thread t : workers) {
      t.join();
    }
    double mean = 0;
    for (double s : scores) {
      mean += s;
    }
    mean /= pairs;
    double var = 0;
    for (double s : scores) {
      var += (s - mean) * (s - mean);
    }
    double se = Math.sqrt(var / Math.max(1, pairs - 1) / pairs);
    double elo = elo(mean);
    System.out.printf(Locale.ROOT, "%s vs %s, %s, %d pairs: W %d D %d L %d, score %.1f%% +-%.1f, Elo %+.0f [%+.0f, %+.0f], A wins as F %d, as S %d, mean plies %.0f%n", a, b, size, pairs, wdl[0], wdl[1], wdl[2], 100 * mean, 196 * se, elo, elo(mean - 1.96 * se), elo(mean + 1.96 * se), seat[0], seat[1], pliesSum[0] / (double) (2 * pairs));
  }

  private static double elo(double p) {
    double x = Math.max(0.001, Math.min(0.999, p));
    return 400 * Math.log10(x / (1 - x));
  }
}
