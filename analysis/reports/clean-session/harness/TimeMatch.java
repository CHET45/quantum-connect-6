package ch.qc.tools;

import ch.qc.bot.Clock;
import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import ch.qc.bot.search.TranspositionTable;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/** Real-time match of two search configurations in one JVM, hot code: wd=blastWinMinDepth,gen=blastGenDepth,r=blastReduce,ebf=N,ms=N. */
public final class TimeMatch {
  static final class Cfg {
    int wd = -1000;
    int gen = 3;
    int r = 0;
    int ebf = 3;
    long ms = 45;
    long ponder = 0;
    String text;

    static Cfg parse(String text) {
      Cfg c = new Cfg();
      c.text = text;
      if (!text.equals("base")) {
        for (String p : text.split(",")) {
          String[] kv = p.split("=");
          int v = Integer.parseInt(kv[1]);
          switch (kv[0]) {
            case "wd" -> c.wd = v;
            case "gen" -> c.gen = v;
            case "r" -> c.r = v;
            case "ebf" -> c.ebf = v;
            case "ms" -> c.ms = v;
            case "ponder" -> c.ponder = v;
            default -> throw new IllegalArgumentException(p);
          }
        }
      }
      return c;
    }

    Searcher.Options options() {
      Searcher.Options o = new Searcher.Options(true, true, false, true, false);
      o.blastWinMinDepth = wd;
      o.blastGenDepth = gen;
      o.blastReduce = r;
      o.ebf = ebf;
      return o;
    }
  }

  /** Per side totals: calls, nodes, depth sum, nanos, overshoot calls beyond 60 ms. */
  static final AtomicLongArray[] STATS = {new AtomicLongArray(5), new AtomicLongArray(5)};
  static final AtomicLong BLAST_WINS = new AtomicLong();

  static FastGame.Engine engine(Geometry g, Evaluator e, Cfg c, int side) {
    Searcher s = new Searcher(g, e, new TranspositionTable(18), c.options());
    long hard = c.ms * 1_000_000L;
    return (b, me, cdMe, cdOpp, turn) -> {
      long t0 = Clock.nanos();
      Searcher.Result r = s.run(b, me, cdMe, cdOpp, turn, t0, hard);
      long dt = Clock.nanos() - t0;
      AtomicLongArray st = STATS[side];
      st.incrementAndGet(0);
      st.addAndGet(1, r.nodes());
      st.addAndGet(2, r.depth());
      st.addAndGet(3, dt);
      if (dt > 60_000_000L) {
        st.incrementAndGet(4);
      }
      if (c.ponder > 0 && r.move() != ch.qc.bot.search.Tactics.NONE) {
        // the second engine call: the column is committed, so search the position after it to fill the table
        ch.qc.bot.rules.Board p = new ch.qc.bot.rules.Board(g);
        p.copyFrom(b);
        int col = ch.qc.bot.search.Tactics.column(r.move());
        boolean blast = ch.qc.bot.search.Tactics.isSpecial(r.move());
        boolean over = blast ? p.blast(col) : p.make(col, me);
        if (!over && !p.topRowFull()) {
          s.run(p, 3 - me, cdOpp, blast ? 1 : Math.min(cdMe + 1, 10), turn + 1, Clock.nanos(), c.ponder * 1_000_000L);
        }
      }
      return r.move();
    };
  }

  public static void main(String[] a) throws Exception {
    int rows = Integer.parseInt(a[0]);
    int cols = Integer.parseInt(a[1]);
    Cfg ca = Cfg.parse(a[2]);
    Cfg cb = Cfg.parse(a[3]);
    int pairs = Integer.parseInt(a[4]);
    int threads = Integer.parseInt(a[5]);
    String weights = a[6];
    List<int[]> pool = MatchMain.readOpenings(Path.of(a[7]));
    Geometry g = new Geometry(rows, cols);
    java.util.function.Function<Geometry, Evaluator> ef = Evaluators.factory(weights);
    ExecutorService ex = Executors.newFixedThreadPool(threads);
    var done = new ExecutorCompletionService<int[]>(ex);
    for (int p = 0; p < pairs; p++) {
      int id = p % pool.size();
      done.submit(() -> {
        int[] line = pool.get(id);
        int[] r = new int[3];
        for (int game = 0; game < 2; game++) {
          boolean aFirst = game == 0;
          FastGame.Engine ea = engine(g, ef.apply(g), ca, 0);
          FastGame.Engine eb = engine(g, ef.apply(g), cb, 1);
          FastGame.Outcome o = FastGame.play(g, line, aFirst ? ea : eb, aFirst ? eb : ea, null);
          int ac = aFirst ? 1 : 2;
          r[o.winner() == 0 ? 1 : o.winner() == ac ? 0 : 2]++;
          if ("blast".equals(o.reason())) {
            BLAST_WINS.incrementAndGet();
          }
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
      if (p % 20 == 0 || p == pairs) {
        double n = t[0] + t[1] + t[2];
        double s = (t[0] + 0.5 * t[1]) / n;
        double elo = -400 * Math.log10(1 / Math.max(1e-6, Math.min(1 - 1e-6, s)) - 1);
        double se = Math.sqrt((t[0] * Math.pow(1 - s, 2) + t[1] * Math.pow(0.5 - s, 2) + t[2] * s * s) / n / n);
        System.out.printf(Locale.ROOT, "%dx%d [%s] vs [%s]: pairs %d W %d D %d L %d score %.1f%% (+-%.1f) elo %+.0f, %.1f min%n",
            rows, cols, ca.text, cb.text, p, t[0], t[1], t[2], 100 * s, 196 * se, elo, (System.nanoTime() - t0) / 6e10);
        System.out.flush();
      }
    }
    System.out.printf(Locale.ROOT, "  games decided by a blast: %d of %d%n", BLAST_WINS.get(), t[0] + t[1] + t[2]);
    for (int side = 0; side < 2; side++) {
      AtomicLongArray st = STATS[side];
      double calls = Math.max(1, st.get(0));
      System.out.printf(Locale.ROOT, "  side %s: calls %.0f, nodes/call %.0f, depth %.2f, ms/call %.1f, knps %.0f, calls>60ms %d%n",
          side == 0 ? "A" : "B", calls, st.get(1) / calls, st.get(2) / calls, st.get(3) / calls / 1e6,
          st.get(1) / Math.max(1e-9, st.get(3) / 1e6), st.get(4));
    }
    ex.shutdownNow();
  }

  private TimeMatch() {}
}
