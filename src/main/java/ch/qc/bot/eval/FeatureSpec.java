package ch.qc.bot.eval;

import ch.qc.bot.rules.Geometry;
import ch.qc.bot.rules.Tables;

/**
 * Window family of the learned evaluation (design 3.2): windows tied under the left-right mirror (the only symmetry
 * gravity leaves), row identity kept. {@code featMap[w * codes + code]} maps a window's code seen from the side to
 * move (digit 1 = mover, 2 = opponent, digits in the window's Geometry order) to a parameter index
 * {@code class * codes + canonical code}: a mirrored window reads the representative's cells in mirrored order,
 * and a self-mirroring window uses the smaller of a code and its mirror image. Java builds this table once; the
 * training code reads it from the JSON dump, so both sides index parameters identically.
 */
public final class FeatureSpec {
  public final Geometry g;
  public final int codes;
  public final int classes;
  /** windowClass[w]: mirror class of window w. */
  public final int[] windowClass;
  public final int[] featMap;
  public final long specHash;

  public FeatureSpec(Geometry g) {
    this.g = g;
    Tables t = g.tables;
    int k = g.k;
    codes = t.codes;
    int n = g.windows;
    int[] mirror = new int[n];
    // perm[w][i]: own digit position of the cell that is class digit i
    int[][] perm = new int[n][k];
    for (int w = 0; w < n; w++) {
      mirror[w] = findMirror(w);
    }
    windowClass = new int[n];
    java.util.Arrays.fill(windowClass, -1);
    int cls = 0;
    boolean[] selfMirror = new boolean[n];
    for (int w = 0; w < n; w++) {
      int m = mirror[w];
      int rep = Math.min(w, m);
      if (windowClass[rep] < 0) {
        windowClass[rep] = cls++;
      }
      windowClass[w] = windowClass[rep];
      selfMirror[w] = m == w;
      for (int i = 0; i < k; i++) {
        if (w == rep) {
          perm[w][i] = i;
        } else {
          perm[w][i] = digitOf(w, mirrorCell(g.winCells[rep * k + i]));
        }
      }
    }
    classes = cls;
    featMap = new int[n * codes];
    for (int w = 0; w < n; w++) {
      // for a self-mirroring window, the own digit position of the mirror image of each digit's cell
      int[] mirrorPos = new int[k];
      if (selfMirror[w]) {
        for (int i = 0; i < k; i++) {
          mirrorPos[i] = digitOf(w, mirrorCell(g.winCells[w * k + i]));
        }
      }
      for (int code = 0; code < codes; code++) {
        int c = 0;
        for (int i = 0; i < k; i++) {
          c += digit(code, perm[w][i], t) * t.pow3[i];
        }
        if (selfMirror[w]) {
          int mc = 0;
          for (int i = 0; i < k; i++) {
            mc += digit(c, mirrorPos[i], t) * t.pow3[i];
          }
          c = Math.min(c, mc);
        }
        featMap[w * codes + code] = windowClass[w] * codes + c;
      }
    }
    long h = 1125899906842597L;
    h = 31 * h + g.rows;
    h = 31 * h + g.cols;
    h = 31 * h + k;
    for (int x : g.winCells) {
      h = 31 * h + x;
    }
    for (int x : featMap) {
      h = 31 * h + x;
    }
    h = 31 * h + Features.STAGES;
    h = 31 * h + Features.CD_CONTEXTS;
    h = 31 * h + Features.T1_SIZE;
    h = 31 * h + Features.T2_SIZE;
    h = 31 * h + Features.T3_SIZE;
    specHash = h;
  }

  private static int digit(int code, int pos, Tables t) {
    return (code / t.pow3[pos]) % 3;
  }

  private int mirrorCell(int cell) {
    int col = cell / g.rows;
    int y = cell % g.rows;
    return (g.cols - 1 - col) * g.rows + y;
  }

  private int digitOf(int w, int cell) {
    for (int i = 0; i < g.k; i++) {
      if (g.winCells[w * g.k + i] == cell) {
        return i;
      }
    }
    return 0; // unreachable: callers pass a cell of the window
  }

  private int findMirror(int w) {
    int k = g.k;
    boolean[] target = new boolean[g.cells];
    for (int i = 0; i < k; i++) {
      target[mirrorCell(g.winCells[w * k + i])] = true;
    }
    for (int v = 0; v < g.windows; v++) {
      boolean all = true;
      for (int i = 0; i < k && all; i++) {
        all = target[g.winCells[v * k + i]];
      }
      if (all) {
        return v;
      }
    }
    return w; // unreachable: the board's mirror maps every window onto a window
  }

  /** Parameter count of one folded window table (all classes). */
  public int windowParams() {
    return classes * codes;
  }

  /** JSON for the training code: geometry, window cells in digit order, classes, featMap, sizes and hash. */
  public String toJson() {
    StringBuilder sb = new StringBuilder(featMap.length * 7);
    sb.append("{\"version\":1,\"rows\":").append(g.rows).append(",\"cols\":").append(g.cols)
        .append(",\"k\":").append(g.k).append(",\"codes\":").append(codes)
        .append(",\"windows\":").append(g.windows).append(",\"classes\":").append(classes)
        .append(",\"stages\":").append(Features.STAGES).append(",\"cdContexts\":").append(Features.CD_CONTEXTS)
        .append(",\"t1\":").append(Features.T1_SIZE).append(",\"t2\":").append(Features.T2_SIZE)
        .append(",\"t3\":").append(Features.T3_SIZE).append(",\"specHash\":\"").append(Long.toHexString(specHash))
        .append("\",\"winCells\":[");
    for (int i = 0; i < g.winCells.length; i++) {
      sb.append(i == 0 ? "" : ",").append(g.winCells[i]);
    }
    sb.append("],\"windowClass\":[");
    for (int i = 0; i < windowClass.length; i++) {
      sb.append(i == 0 ? "" : ",").append(windowClass[i]);
    }
    sb.append("],\"featMap\":[");
    for (int i = 0; i < featMap.length; i++) {
      sb.append(i == 0 ? "" : ",").append(featMap[i]);
    }
    return sb.append("]}").toString();
  }
}
