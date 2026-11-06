package ch.qc.tools;

import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Fits a small correction of the evaluation so that a shallow search picks the moves a bot really played (the idea of
 * minimax tree optimisation: search with the current weights, keep the leaf of each root move's main line, take
 * gradient steps of a softmax choice model on those leaves, search again). Only positions without a one-move win or a
 * forced block count. Games with id hash modulo --mod equal to --rem are held out.
 *
 * <pre>
 * SaviFit games.jsonl --bots "Savi8|Savi9" --sizes 15x11 --base hand --depth 4 --outer 10 --out fit.txt
 * </pre>
 */
public final class SaviFit {
  private static final int COOLDOWN = 10;
  private static final int MATE = 30_000;
  private static final int MATE_BOUND = 29_000;
  private static final int INF = 32_000;
  private static final int SPECIAL = 1 << 16;
  private static final int MAX_PLY = 32;
  /** Score used for a proven win or loss in the choice model. */
  private static final float MATE_VALUE = 3000f;

  private record Pos(Geometry geo, byte[] cells, int stm, int cdS, int cdO, int truth) {}

  /** A position's root moves after one search: signed base value and signed leaf features. */
  private static final class Item {
    int truth;
    float[] base;
    float[][] phi;
  }

  private SaviFit() {}

  public static void main(String[] args) throws Exception {
    String botsRe = "Savi8|Savi9";
    String sizes = "15x11";
    String baseName = "hand";
    String out = "fit.txt";
    String init = null;
    int depth = 4;
    int outer = 10;
    int inner = 60;
    int mod = 5;
    int rem = 0;
    int threads = 2;
    int minPly = 4;
    int stride = 1;
    double lr = 0.5;
    double l2 = 1e-3;
    for (int i = 1; i < args.length; i++) {
      switch (args[i]) {
        case "--bots" -> botsRe = args[++i];
        case "--sizes" -> sizes = args[++i];
        case "--base" -> baseName = args[++i];
        case "--out" -> out = args[++i];
        case "--init" -> init = args[++i];
        case "--depth" -> depth = Integer.parseInt(args[++i]);
        case "--outer" -> outer = Integer.parseInt(args[++i]);
        case "--inner" -> inner = Integer.parseInt(args[++i]);
        case "--mod" -> mod = Integer.parseInt(args[++i]);
        case "--rem" -> rem = Integer.parseInt(args[++i]);
        case "--threads" -> threads = Integer.parseInt(args[++i]);
        case "--min-ply" -> minPly = Integer.parseInt(args[++i]);
        case "--stride" -> stride = Integer.parseInt(args[++i]);
        case "--lr" -> lr = Double.parseDouble(args[++i]);
        case "--l2" -> l2 = Double.parseDouble(args[++i]);
        default -> throw new IllegalArgumentException(args[i]);
      }
    }
    Pattern bots = Pattern.compile(botsRe);
    List<Pos> train = new ArrayList<>();
    List<Pos> val = new ArrayList<>();
    for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
      if (line.isBlank()) {
        continue;
      }
      ServerGames.Game g = ServerGames.game(line);
      if (g.rows() <= 0 || !List.of(sizes.split(",")).contains(g.rows() + "x" + g.cols())) {
        continue;
      }
      Geometry geo = ServerGames.geometry(g.rows(), g.cols());
      ServerGames.Replay check = ServerGames.replay(geo, g, false);
      if (check.problem != null) {
        continue;
      }
      boolean held = Math.floorMod(g.id().hashCode(), mod) == rem;
      collect(geo, g, check.plies, bots, minPly, stride, held ? val : train);
    }
    System.out.printf(Locale.ROOT, "train %d, held out %d free positions, %d parameters, base %s, depth %d%n", train.size(), val.size(), SaviEval.N, baseName, depth);

    var baseFactory = Evaluators.factory(baseName);
    double[] w = init == null ? new double[SaviEval.N] : SaviEval.load(Path.of(init));
    double logBeta = Math.log(4.0);
    double bestVal = Double.MAX_VALUE;
    for (int round = 0; round < outer; round++) {
      long t0 = ch.qc.bot.Clock.nanos();
      List<Item> trainItems = searchAll(train, w, baseFactory, depth, threads);
      List<Item> valItems = searchAll(val, w, baseFactory, depth, threads);
      double seconds = (ch.qc.bot.Clock.nanos() - t0) / 1e9;
      double beta = Math.exp(logBeta);
      double[] tr = measure(trainItems, w, beta);
      double[] va = measure(valItems, w, beta);
      System.out.printf(Locale.ROOT, "round %2d  train loss %.4f top1 %.1f%%   held-out loss %.4f top1 %.1f%%   uniform %.4f  beta %.2f  %.0f s%n",
          round, tr[0], 100 * tr[1], va[0], 100 * va[1], va[2], beta, seconds);
      if (va[0] < bestVal) {
        bestVal = va[0];
        SaviEval.save(w, Path.of(out));
      }
      logBeta = adam(trainItems, w, logBeta, inner, lr, l2);
      System.out.flush();
    }
    System.out.println("best held-out loss " + bestVal + ", saved to " + out);
    double[] best = SaviEval.load(Path.of(out));
    Integer[] order = new Integer[best.length];
    for (int i = 0; i < order.length; i++) {
      order[i] = i;
    }
    java.util.Arrays.sort(order, (x, y) -> Double.compare(Math.abs(best[y]), Math.abs(best[x])));
    for (int i = 0; i < 25; i++) {
      System.out.printf(Locale.ROOT, "%8.2f  %s%n", best[order[i]], SaviEval.name(order[i]));
    }
  }

  private static void collect(Geometry geo, ServerGames.Game game, int total, Pattern bots, int minPly, int stride, List<Pos> out) {
    Board b = new Board(geo);
    int[] counter = new int[3];
    String s = game.moves();
    int expected = 1;
    int ply = 0;
    int i = 0;
    while (i < s.length()) {
      int side = s.charAt(i) == 'F' ? 1 : 2;
      int col = Character.digit(s.charAt(i + 1), 36);
      boolean star = i + 2 < s.length() && s.charAt(i + 2) == '*';
      i += star ? 3 : 2;
      if (side != expected) {
        counter[expected]++;
        expected = side;
      }
      if (bots.matcher(game.name()[side - 1]).find() && !game.name()[side - 1].contains("beater") && ply >= minPly
          && ply % stride == 0 && free(b, side, counter[side])) {
        out.add(new Pos(geo, b.cells().clone(), side, counter[side], counter[3 - side], star ? col | SPECIAL : col));
      }
      boolean win;
      if (star) {
        win = b.blast(col);
        counter[side] = 0;
      } else {
        win = b.make(col, side);
      }
      counter[side]++;
      ply++;
      expected = 3 - side;
      if (win || ply >= total) {
        return;
      }
    }
  }

  private static boolean free(Board b, int stm, int cdS) {
    for (int c = 0; c < b.g.cols; c++) {
      if (!b.isFull(c)) {
        int cell = c * b.g.rows + b.height(c);
        if (b.threats(stm, cell) > 0 || b.threats(3 - stm, cell) > 0) {
          return false;
        }
      }
    }
    return !(cdS >= COOLDOWN && b.anyBlastWins());
  }

  private static List<Item> searchAll(List<Pos> list, double[] w, java.util.function.Function<Geometry, Evaluator> baseFactory, int depth, int threads) throws InterruptedException {
    Item[] items = new Item[list.size()];
    int[] next = {0};
    Thread[] workers = new Thread[threads];
    for (int t = 0; t < threads; t++) {
      workers[t] = new Thread(() -> {
        java.util.Map<Geometry, Mini> minis = new java.util.HashMap<>();
        while (true) {
          int idx;
          synchronized (next) {
            idx = next[0]++;
          }
          if (idx >= items.length) {
            return;
          }
          Pos p = list.get(idx);
          Mini m = minis.computeIfAbsent(p.geo, g -> new Mini(g, new SaviEval(g, baseFactory.apply(g), w)));
          items[idx] = m.root(p, depth);
        }
      });
      workers[t].start();
    }
    for (Thread t : workers) {
      t.join();
    }
    List<Item> out = new ArrayList<>();
    for (Item it : items) {
      if (it != null && it.truth >= 0) {
        out.add(it);
      }
    }
    return out;
  }

  /** Mean loss, top-1 share and the loss of a uniform choice. */
  private static double[] measure(List<Item> items, double[] w, double beta) {
    double loss = 0;
    double hit = 0;
    double uniform = 0;
    for (Item it : items) {
      double[] s = scores(it, w, beta);
      int top = 0;
      double norm = logSum(s);
      for (int m = 1; m < s.length; m++) {
        if (s[m] > s[top]) {
          top = m;
        }
      }
      loss += norm - s[it.truth];
      hit += s[top] == s[it.truth] ? 1 : 0;
      uniform += Math.log(s.length);
    }
    int n = Math.max(1, items.size());
    return new double[] {loss / n, hit / n, uniform / n};
  }

  private static double[] scores(Item it, double[] w, double beta) {
    double[] s = new double[it.base.length];
    for (int m = 0; m < s.length; m++) {
      double v = it.base[m];
      float[] f = it.phi[m];
      if (f != null) {
        for (int i = 0; i < f.length; i++) {
          if (f[i] != 0) {
            v += w[i] * f[i];
          }
        }
      }
      s[m] = beta * Math.max(-MATE_VALUE, Math.min(MATE_VALUE, v)) / 256.0;
    }
    return s;
  }

  private static double logSum(double[] s) {
    double top = Double.NEGATIVE_INFINITY;
    for (double v : s) {
      top = Math.max(top, v);
    }
    double sum = 0;
    for (double v : s) {
      sum += Math.exp(v - top);
    }
    return top + Math.log(sum);
  }

  /** Adam on the weights and log beta with the leaves fixed; returns the new log beta. */
  private static double adam(List<Item> items, double[] w, double logBeta, int steps, double lr, double l2) {
    int n = w.length;
    double[] m1 = new double[n + 1];
    double[] m2 = new double[n + 1];
    double[] g = new double[n + 1];
    for (int step = 1; step <= steps; step++) {
      java.util.Arrays.fill(g, 0);
      double beta = Math.exp(logBeta);
      for (Item it : items) {
        double[] s = scores(it, w, beta);
        double norm = logSum(s);
        double mean = 0;
        for (int m = 0; m < s.length; m++) {
          double p = Math.exp(s[m] - norm);
          double y = m == it.truth ? 1 : 0;
          double d = p - y;
          // d loss / d s[m] = p - y; s[m] = beta * value / 256
          mean += d * s[m];
          float[] f = it.phi[m];
          if (f != null && Math.abs(d) > 1e-5) {
            double c = d * beta / 256.0;
            for (int i = 0; i < n; i++) {
              if (f[i] != 0) {
                g[i] += c * f[i];
              }
            }
          }
        }
        g[n] += mean;
      }
      double count = Math.max(1, items.size());
      for (int i = 0; i <= n; i++) {
        g[i] /= count;
      }
      for (int i = 0; i < n; i++) {
        g[i] += l2 * w[i] / 256.0;
      }
      for (int i = 0; i <= n; i++) {
        m1[i] = 0.9 * m1[i] + 0.1 * g[i];
        m2[i] = 0.999 * m2[i] + 0.001 * g[i] * g[i];
        double a = m1[i] / (1 - Math.pow(0.9, step));
        double b = m2[i] / (1 - Math.pow(0.999, step));
        double delta = a / (Math.sqrt(b) + 1e-9);
        if (i < n) {
          w[i] -= lr * delta;
        } else {
          logBeta -= 0.01 * delta;
        }
      }
    }
    return logBeta;
  }

  /** Plain alpha-beta with a main line. */
  private static final class Mini {
    final Geometry g;
    final SaviEval eval;
    final Board[] stack = new Board[MAX_PLY + 2];
    final int[][] pv = new int[MAX_PLY + 2][MAX_PLY + 2];
    final int[] pvLen = new int[MAX_PLY + 2];
    final int[] order;
    final float[] tmp = new float[SaviEval.N];

    Mini(Geometry g, SaviEval eval) {
      this.g = g;
      this.eval = eval;
      for (int i = 0; i < stack.length; i++) {
        stack[i] = new Board(g);
      }
      Integer[] o = new Integer[g.cols];
      for (int c = 0; c < g.cols; c++) {
        o[c] = c;
      }
      java.util.Arrays.sort(o, (x, y) -> Integer.compare(Math.abs(2 * x - (g.cols - 1)), Math.abs(2 * y - (g.cols - 1))));
      order = new int[g.cols];
      for (int c = 0; c < g.cols; c++) {
        order[c] = o[c];
      }
    }

    Item root(Pos p, int depth) {
      Board start = new Board(g);
      start.load(p.cells);
      List<Integer> moves = new ArrayList<>();
      for (int c : order) {
        if (!start.isFull(c)) {
          moves.add(c);
          if (p.cdS >= COOLDOWN) {
            moves.add(c | SPECIAL);
          }
        }
      }
      Item it = new Item();
      it.truth = moves.indexOf(p.truth);
      it.base = new float[moves.size()];
      it.phi = new float[moves.size()][];
      Board r = new Board(g);
      Board leaf = new Board(g);
      for (int mi = 0; mi < moves.size(); mi++) {
        int move = moves.get(mi);
        r.copyFrom(start);
        boolean special = (move & SPECIAL) != 0;
        int col = move & 0xFF;
        boolean win = special ? r.blast(col) : r.make(col, p.stm);
        if (win) {
          it.base[mi] = MATE_VALUE;
          continue;
        }
        int opp = 3 - p.stm;
        int score = -nega(r, depth - 1, 1, -INF, INF, opp, p.cdO, special ? 1 : p.cdS + 1);
        if (Math.abs(score) > MATE_BOUND) {
          it.base[mi] = score > 0 ? MATE_VALUE : -MATE_VALUE;
          continue;
        }
        // walk the main line to its leaf
        leaf.copyFrom(r);
        int stm = opp;
        int cdStm = p.cdO;
        int cdOther = special ? 1 : p.cdS + 1;
        for (int i = 0; i < pvLen[1]; i++) {
          int m = pv[1][i];
          if ((m & SPECIAL) != 0) {
            leaf.blast(m & 0xFF);
            cdStm = 1;
          } else {
            leaf.make(m & 0xFF, stm);
            cdStm++;
          }
          int t = cdStm;
          cdStm = cdOther;
          cdOther = t;
          stm = 3 - stm;
        }
        int sign = stm == p.stm ? 1 : -1;
        float[] f = new float[SaviEval.N];
        eval.features(leaf, stm, cdStm, cdOther, f);
        for (int i = 0; i < f.length; i++) {
          f[i] *= sign;
        }
        it.phi[mi] = f;
        it.base[mi] = sign * eval.base().evaluate(leaf, stm, cdStm, cdOther);
      }
      return it;
    }

    int nega(Board b, int depth, int ply, int alpha, int beta, int stm, int cdS, int cdO) {
      pvLen[ply] = 0;
      if (depth <= 0) {
        return eval.evaluate(b, stm, cdS, cdO);
      }
      int rows = g.rows;
      int opp = 3 - stm;
      int blocks = 0;
      boolean any = false;
      for (int c = 0; c < g.cols; c++) {
        if (b.isFull(c)) {
          continue;
        }
        any = true;
        int cell = c * rows + b.height(c);
        if (b.threats(stm, cell) > 0) {
          return MATE - ply - 1;
        }
        if (b.threats(opp, cell) > 0) {
          blocks++;
        }
      }
      if (!any) {
        return 0;
      }
      boolean ready = cdS >= COOLDOWN;
      if (ready && b.anyBlastWins()) {
        return MATE - ply - 1;
      }
      int best = -INF;
      for (int c : order) {
        if (b.isFull(c)) {
          continue;
        }
        if (blocks > 0 && b.threats(opp, c * rows + b.height(c)) == 0) {
          continue;
        }
        b.make(c, stm);
        int score = -nega(b, depth - 1, ply + 1, -beta, -alpha, opp, cdO, cdS + 1);
        b.unmake(c);
        if (score > best) {
          best = score;
          pv[ply][0] = c;
          System.arraycopy(pv[ply + 1], 0, pv[ply], 1, pvLen[ply + 1]);
          pvLen[ply] = pvLen[ply + 1] + 1;
          if (score > alpha) {
            alpha = score;
            if (alpha >= beta) {
              return best;
            }
          }
        }
      }
      if (ready) {
        Board tmpBoard = stack[ply + 1];
        for (int c : order) {
          if (b.isFull(c)) {
            continue;
          }
          tmpBoard.copyFrom(b);
          tmpBoard.blast(c);
          int score = -nega(tmpBoard, depth - 1, ply + 1, -beta, -alpha, opp, cdO, 1);
          if (score > best) {
            best = score;
            pv[ply][0] = c | SPECIAL;
            System.arraycopy(pv[ply + 1], 0, pv[ply], 1, pvLen[ply + 1]);
            pvLen[ply] = pvLen[ply + 1] + 1;
            if (score > alpha) {
              alpha = score;
              if (alpha >= beta) {
                return best;
              }
            }
          }
        }
      }
      return best;
    }
  }
}
