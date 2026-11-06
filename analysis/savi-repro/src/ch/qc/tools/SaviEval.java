package ch.qc.tools;

import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.eval.Features;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A base evaluation plus a small linear correction fitted to a bot's moves. All features are from the side to move's
 * view, do not depend on the board size and are mirror-invariant. Full scan at every call: for analysis and fixed-node
 * matches, not for real time. Immutable after construction.
 */
public final class SaviEval implements Evaluator {
  private static final int BANDS = 3;
  private static final int CLASSES = 3 * BANDS;
  /** Live windows by side, direction group, row band and disc count. */
  private static final int A = 0;
  /** Live windows by side, stage and disc count. */
  private static final int B = A + 2 * CLASSES * 5;
  /** Threat buckets, two thresholds each: imm, imm', one, one', stk, stk', nat, unn, nat', unn'. */
  private static final int C = B + 2 * Features.STAGES * 5;
  /** stm ready, opp ready, stm counter 7-9, opp counter 7-9. */
  private static final int D = C + 20;
  /** Valley columns at least 1, at least 2. */
  private static final int E = D + 4;
  /** Centrality of discs (own - opp) by stage. */
  private static final int F = E + 2;
  /** Height of discs (own - opp) by stage. */
  private static final int G = F + Features.STAGES;
  public static final int N = G + Features.STAGES;

  private final Geometry g;
  private final Evaluator base;
  private final double[] w;
  /** Start of the window's class in a table: class * codes. */
  private final int[] classStart;
  /** table[stm - 1][stage][class * codes + code], in 1/16 of 1/256 logit. */
  private final int[][][] table;
  private final int[][] own;
  private final double[] centre;

  public SaviEval(Geometry g, Evaluator base, double[] weights) {
    this.g = g;
    this.base = base;
    this.w = weights.clone();
    int codes = g.tables.codes;
    classStart = new int[g.windows];
    int[] winClass = new int[g.windows];
    for (int win = 0; win < g.windows; win++) {
      winClass[win] = windowClass(g, win);
      classStart[win] = winClass[win] * codes;
    }
    own = new int[3][codes];
    for (int stm = 1; stm <= 2; stm++) {
      for (int code = 0; code < codes; code++) {
        int a = 0;
        int b = 0;
        for (int i = 0, c = code; i < g.k; i++, c /= 3) {
          a += c % 3 == stm ? 1 : 0;
          b += c % 3 == 3 - stm ? 1 : 0;
        }
        own[stm][code] = Math.min(a, 5) * 16 + Math.min(b, 5);
      }
    }
    table = new int[2][Features.STAGES][CLASSES * codes];
    for (int stm = 1; stm <= 2; stm++) {
      for (int stage = 0; stage < Features.STAGES; stage++) {
        int[] t = table[stm - 1][stage];
        for (int cls = 0; cls < CLASSES; cls++) {
          for (int code = 0; code < codes; code++) {
            int a = own[stm][code] >> 4;
            int b = own[stm][code] & 15;
            double v = 0;
            if (a > 0 && b == 0) {
              v = w[A + cls * 5 + a - 1] + w[B + stage * 5 + a - 1];
            } else if (b > 0 && a == 0) {
              v = w[A + CLASSES * 5 + cls * 5 + b - 1] + w[B + Features.STAGES * 5 + stage * 5 + b - 1];
            }
            t[cls * codes + code] = (int) Math.round(16 * v);
          }
        }
      }
    }
    centre = new double[g.cols];
    for (int c = 0; c < g.cols; c++) {
      centre[c] = g.cols < 2 ? 1 : 1.0 - Math.abs(2.0 * c - (g.cols - 1)) / (g.cols - 1);
    }
  }

  private static int windowClass(Geometry g, int win) {
    int dir = g.winDir[win];
    int group = dir == 0 ? 0 : dir == 1 ? 1 : 2;
    int lowest = g.rows;
    for (int i = 0; i < g.k; i++) {
      lowest = Math.min(lowest, g.winCells[win * g.k + i] % g.rows);
    }
    int band = lowest < 2 ? 0 : lowest < 5 ? 1 : 2;
    return group * BANDS + band;
  }

  public Evaluator base() {
    return base;
  }

  @Override
  public int evaluate(Board b, int stm, int cdStm, int cdOpp) {
    int stage = Features.stage(b);
    int[] t = table[stm - 1][stage];
    int sum = 0;
    for (int win = 0; win < g.windows; win++) {
      sum += t[classStart[win] + b.windowCode(win)];
    }
    double s = base.evaluate(b, stm, cdStm, cdOpp) + sum / 16.0 + other(b, stm, cdStm, cdOpp, stage, null);
    return (int) Math.max(-MAX_EVAL, Math.min(MAX_EVAL, Math.round(s)));
  }

  /** Writes the correction's feature vector for the side to move into out (cleared first). */
  public void features(Board b, int stm, int cdStm, int cdOpp, float[] out) {
    java.util.Arrays.fill(out, 0f);
    int stage = Features.stage(b);
    int codes = g.tables.codes;
    for (int win = 0; win < g.windows; win++) {
      int n = own[stm][b.windowCode(win)];
      int x = n >> 4;
      int y = n & 15;
      int cls = classStart[win] / codes;
      if (x > 0 && y == 0) {
        out[A + cls * 5 + x - 1]++;
        out[B + stage * 5 + x - 1]++;
      } else if (y > 0 && x == 0) {
        out[A + CLASSES * 5 + cls * 5 + y - 1]++;
        out[B + Features.STAGES * 5 + stage * 5 + y - 1]++;
      }
    }
    other(b, stm, cdStm, cdOpp, stage, out);
  }

  private double other(Board b, int stm, int cdStm, int cdOpp, int stage, float[] out) {
    double s = 0;
    int packed = Features.threatIndices(b, stm, cdStm, cdOpp);
    int t1 = Features.t1(packed);
    int t2 = Features.t2(packed);
    int t3 = Features.t3(packed);
    int imm0 = t1 / 27;
    int imm1 = t1 / 9 % 3;
    int one0 = t1 / 3 % 3;
    int one1 = t1 % 3;
    int unn1 = t2 % 3;
    int nat1 = t2 / 3 % 3;
    int unn0 = t2 / 9 % 3;
    int nat0 = t2 / 27 % 3;
    int stk1 = t2 / 81 % 3;
    int stk0 = t2 / 243;
    int valleys = t3 % 3;
    s += bucket(0, imm0, out) + bucket(1, imm1, out) + bucket(2, one0, out) + bucket(3, one1, out)
        + bucket(4, stk0, out) + bucket(5, stk1, out) + bucket(6, nat0, out) + bucket(7, unn0, out)
        + bucket(8, nat1, out) + bucket(9, unn1, out);
    if (cdStm >= 10) {
      s += add(D, 1, out);
    } else if (cdStm >= 7) {
      s += add(D + 2, 1, out);
    }
    if (cdOpp >= 10) {
      s += add(D + 1, 1, out);
    } else if (cdOpp >= 7) {
      s += add(D + 3, 1, out);
    }
    if (valleys >= 1) {
      s += add(E, 1, out);
    }
    if (valleys >= 2) {
      s += add(E + 1, 1, out);
    }
    double central = 0;
    double height = 0;
    int rows = g.rows;
    for (int c = 0; c < g.cols; c++) {
      int h = b.height(c);
      for (int y = 0; y < h; y++) {
        int sign = b.cell(c, y) == stm ? 1 : -1;
        central += sign * centre[c];
        height += sign * (double) y / rows;
      }
    }
    s += add(F + stage, central, out) + add(G + stage, height, out);
    return s;
  }

  private double bucket(int i, int value, float[] out) {
    double s = 0;
    if (value >= 1) {
      s += add(C + 2 * i, 1, out);
    }
    if (value >= 2) {
      s += add(C + 2 * i + 1, 1, out);
    }
    return s;
  }

  private double add(int i, double x, float[] out) {
    if (out != null) {
      out[i] += (float) x;
    }
    return w[i] * x;
  }

  /** Readable name of parameter i. */
  public static String name(int i) {
    String[] group = {"H", "V", "D"};
    String[] band = {"low", "mid", "high"};
    if (i < B) {
      int side = i / (CLASSES * 5);
      int r = i % (CLASSES * 5);
      int cls = r / 5;
      return "win " + (side == 0 ? "own " : "opp ") + group[cls / BANDS] + " " + band[cls % BANDS] + " n=" + (r % 5 + 1);
    }
    if (i < C) {
      int r = i - B;
      int side = r / (Features.STAGES * 5);
      r %= Features.STAGES * 5;
      return "win " + (side == 0 ? "own " : "opp ") + "stage" + r / 5 + " n=" + (r % 5 + 1);
    }
    if (i < D) {
      String[] t = {"imm me", "imm opp", "one me", "one opp", "stk me", "stk opp", "nat me", "unn me", "nat opp", "unn opp"};
      return t[(i - C) / 2] + ((i - C) % 2 == 0 ? " >=1" : " >=2");
    }
    if (i < E) {
      return new String[] {"me ready", "opp ready", "me cd7-9", "opp cd7-9"}[i - D];
    }
    if (i < F) {
      return i == E ? "valley >=1" : "valley >=2";
    }
    if (i < G) {
      return "centrality stage" + (i - F);
    }
    return "height stage" + (i - G);
  }

  /**
   * Turns a feature vector computed for the side to move into the view of the side that just moved: own and opp swap.
   * The parity labels keep their meaning, because they are about who gets the cell in the waiting game.
   */
  public static void moverView(float[] stmView, float[] out) {
    int wins = CLASSES * 5;
    int stages = Features.STAGES * 5;
    for (int i = 0; i < wins; i++) {
      out[A + i] = stmView[A + wins + i];
      out[A + wins + i] = stmView[A + i];
    }
    for (int i = 0; i < stages; i++) {
      out[B + i] = stmView[B + stages + i];
      out[B + stages + i] = stmView[B + i];
    }
    // imm, one, stk come in (me, opp) pairs of two thresholds; nat, unn in (nat me, unn me, nat opp, unn opp)
    for (int t = 0; t < 3; t++) {
      for (int k = 0; k < 2; k++) {
        out[C + 4 * t + k] = stmView[C + 4 * t + 2 + k];
        out[C + 4 * t + 2 + k] = stmView[C + 4 * t + k];
      }
    }
    for (int k = 0; k < 4; k++) {
      out[C + 12 + k] = stmView[C + 16 + k];
      out[C + 16 + k] = stmView[C + 12 + k];
    }
    out[D] = stmView[D + 1];
    out[D + 1] = stmView[D];
    out[D + 2] = stmView[D + 3];
    out[D + 3] = stmView[D + 2];
    out[E] = stmView[E];
    out[E + 1] = stmView[E + 1];
    for (int i = 0; i < Features.STAGES; i++) {
      out[F + i] = -stmView[F + i];
      out[G + i] = -stmView[G + i];
    }
  }

  public static void save(double[] w, Path file) throws IOException {
    List<String> lines = new ArrayList<>();
    for (int i = 0; i < w.length; i++) {
      lines.add(String.format(Locale.ROOT, "%d\t%.4f\t%s", i, w[i], name(i)));
    }
    Files.write(file, lines);
  }

  public static double[] load(Path file) throws IOException {
    double[] w = new double[N];
    for (String line : Files.readAllLines(file)) {
      String[] p = line.split("\t");
      if (p.length >= 2) {
        w[Integer.parseInt(p[0])] = Double.parseDouble(p[1]);
      }
    }
    return w;
  }
}
