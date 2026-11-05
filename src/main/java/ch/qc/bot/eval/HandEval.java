package ch.qc.bot.eval;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.rules.Tables;

/**
 * Hand-written evaluation of design 3.9, in units of 1/256 logit from the side to move's view. Window family:
 * a live window with a mover discs scores +VAL[a], a live opponent window -0.9 VAL[b], mixed windows 0. Threat
 * family from the threat cells: discs-below distance d of each threat cell, stacked threats and the waiting-game
 * parity of the lowest latent threat per column. Immutable after construction, shareable between threads.
 */
public final class HandEval extends WindowSumEval {

  private static final int[] VAL = {0, 3, 13, 50, 150, 384};

  private final int rows;
  /** windowScore[stm][code]: contribution of one window seen by side to move stm. */
  private final short[][] windowScore = new short[3][];

  /**
   * Value of a live window with a discs, by the discs it still misses: K - 1 discs score VAL[5], one fewer VAL[4] and so
   * on, a window missing more than five scores 1. For K = 6 this is VAL[min(a, 5)], the table as it was made.
   */
  static int val(int a, int k) {
    if (a <= 0) {
      return 0;
    }
    int missing = Math.max(1, k - a);
    return missing >= VAL.length ? 1 : VAL[VAL.length - missing];
  }

  public HandEval(Geometry g) {
    super(g);
    this.rows = g.rows;
    Tables t = g.tables;
    for (int stm = 1; stm <= 2; stm++) {
      short[] s = new short[t.codes];
      for (int code = 0; code < t.codes; code++) {
        int[] n = new int[3];
        for (int i = 0, c = code; i < t.k; i++, c /= 3) {
          n[c % 3]++;
        }
        int a = n[stm];
        int b = n[3 - stm];
        if (a > 0 && b > 0) {
          s[code] = 0;
        } else if (b == 0) {
          s[code] = (short) val(a, t.k);
        } else {
          s[code] = (short) -Math.round(val(b, t.k) * 0.9);
        }
      }
      windowScore[stm] = s;
    }
  }

  /**
   * @param cdStm engine cooldown counter of the side to move before its turn
   * @param cdOpp the other side's counter before its next turn
   */
  @Override
  public int evaluate(Board b, int stm, int cdStm, int cdOpp) {
    int opp = 3 - stm;
    int sum = windowSum(b, stm - 1, windowScore[stm], stm);

    int one0 = 0;
    int one1 = 0;
    int imm1 = 0;
    int stk0 = 0;
    int stk1 = 0;
    int par0 = 0;
    int par1 = 0;
    int empty = g.cells - b.discs();
    for (int c = b.nextThreatColumn(0); c >= 0; c = b.nextThreatColumn(c + 1)) {
      int h = b.height(c);
      boolean latent0 = false;
      boolean latent1 = false;
      for (int y = h; y < rows; y++) {
        int cell = c * rows + y;
        int d = y - h;
        boolean t0 = b.threats(stm, cell) > 0;
        boolean t1 = b.threats(opp, cell) > 0;
        if (!t0 && !t1) {
          continue;
        }
        boolean above0 = y + 1 < rows && b.threats(stm, cell + 1) > 0;
        boolean above1 = y + 1 < rows && b.threats(opp, cell + 1) > 0;
        // a threat cell owned by the side to move in the waiting game iff empty + rows + (y + 1) is odd
        boolean stmOwnsRow = ((empty + rows + y + 1) & 1) == 1;
        if (t0) {
          if (d == 1) {
            one0++;
          }
          if (above0) {
            stk0 = Math.max(stk0, d <= 1 ? 2 : 1);
          }
          if (d >= 2 && !latent0) {
            latent0 = true;
            par0 += stmOwnsRow ? 150 : 30;
          }
        }
        if (t1) {
          if (d == 0) {
            imm1++;
          } else if (d == 1) {
            one1++;
          }
          if (above1) {
            stk1 = Math.max(stk1, d <= 1 ? 2 : 1);
          }
          if (d >= 2 && !latent1) {
            latent1 = true;
            par1 += stmOwnsRow ? 30 : 150;
          }
        }
      }
    }
    boolean stmReady = cdStm >= 10;
    boolean oppReady = cdOpp >= 10;
    if (imm1 >= 2) {
      sum -= 3000;
    }
    sum += 60 * (one0 - one1);
    sum += stk0 == 2 ? 3000 : stk0 == 1 ? 200 : 0;
    sum -= stk1 == 2 ? 3000 : stk1 == 1 ? 200 : 0;
    sum += oppReady ? par0 * 2 / 5 : par0;
    sum -= stmReady ? par1 * 2 / 5 : par1;
    sum += stmReady ? 40 : cdStm >= 7 ? 15 : 0;
    sum -= oppReady ? 40 : cdOpp >= 7 ? 15 : 0;
    return Math.max(-MAX_EVAL, Math.min(MAX_EVAL, sum));
  }

  @Override
  int fullSum(Board b, short[] ws, int stm) {
    int sum = 0;
    for (int w = 0; w < g.windows; w++) {
      sum += ws[b.windowCode(w)];
    }
    return sum;
  }

  @Override
  int dropChange(Board b, int level, short[] ws, int stm) {
    int cell = b.levelCell(level);
    int colour = b.levelColour(level);
    int[] pow = g.cellPow;
    int log = b.levelLog(level);
    int end = g.cellStart[cell + 1];
    int sum = 0;
    for (int j = g.cellStart[cell]; j < end; j++) {
      int old = b.loggedCode(log++);
      sum += ws[old + colour * pow[j]] - ws[old];
    }
    return sum;
  }
}
