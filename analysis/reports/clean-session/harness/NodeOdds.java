package ch.qc.tools;

import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fixed-node match with different budgets per side: how much is more thinking time worth on a board size. */
public final class NodeOdds {
  public static void main(String[] a) throws Exception {
    int rows = Integer.parseInt(a[0]);
    int cols = Integer.parseInt(a[1]);
    long na = Long.parseLong(a[2]);
    long nb = Long.parseLong(a[3]);
    int pairs = Integer.parseInt(a[4]);
    int threads = Integer.parseInt(a[5]);
    String weights = a[6];
    List<int[]> pool = MatchMain.readOpenings(Path.of(a[7]));
    Geometry g = new Geometry(rows, cols);
    java.util.function.Function<Geometry, Evaluator> ef = Evaluators.factory(weights);
    Searcher.Options opt = Searcher.Options.DEFAULT;
    ExecutorService ex = Executors.newFixedThreadPool(threads);
    var done = new ExecutorCompletionService<int[]>(ex);
    for (int p = 0; p < pairs; p++) {
      int id = p % pool.size();
      done.submit(() -> {
        int[] line = pool.get(id);
        int[] r = new int[3]; // a wins, draws, b wins
        for (int game = 0; game < 2; game++) {
          boolean aFirst = game == 0;
          FastGame.Engine ea = FastMatch.engine(g, opt, ef.apply(g), na);
          FastGame.Engine eb = FastMatch.engine(g, opt, ef.apply(g), nb);
          FastGame.Outcome o = FastGame.play(g, line, aFirst ? ea : eb, aFirst ? eb : ea, null);
          int ac = aFirst ? 1 : 2;
          r[o.winner() == 0 ? 1 : o.winner() == ac ? 0 : 2]++;
        }
        return r;
      });
    }
    int[] t = new int[3];
    long t0 = System.nanoTime();
    for (int p = 1; p <= pairs; p++) {
      int[] r = done.take().get();
      for (int i = 0; i < 3; i++) {
        t[i] += r[i];
      }
      if (p % 25 == 0 || p == pairs) {
        double n = t[0] + t[1] + t[2];
        double s = (t[0] + 0.5 * t[1]) / n;
        double elo = -400 * Math.log10(1 / Math.max(1e-6, Math.min(1 - 1e-6, s)) - 1);
        System.out.printf(java.util.Locale.ROOT, "%dx%d %d vs %d nodes (%s): pairs %d W %d D %d L %d score %.1f%% elo %+.0f, %.1f min%n",
            rows, cols, na, nb, weights, p, t[0], t[1], t[2], 100 * s, elo, (System.nanoTime() - t0) / 6e10);
        System.out.flush();
      }
    }
    ex.shutdownNow();
  }

  private NodeOdds() {}
}
