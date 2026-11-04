package ch.qc.bot.eval;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;

/**
 * Context and threat-family features of the learned evaluation (design 3.3, 3.4), computed only here so that the
 * training records and the engine use the same game logic. The window family is in {@link FeatureSpec}.
 */
public final class Features {
  public static final int STAGES = 4;
  public static final int CD_CONTEXTS = 4;
  public static final int T1_SIZE = 81;
  public static final int T2_SIZE = 729;
  public static final int T3_SIZE = 11 * 11 * 3;
  private static final int COOLDOWN = 10;

  private Features() {}

  /** min(3, 4 * discs / cells): 0-23, 24-47, 48-71, 72+ discs on 8x12. */
  public static int stage(Board b) {
    return Math.min(STAGES - 1, STAGES * b.discs() / b.g.cells);
  }

  /** Bit 0: the side to move's special is ready; bit 1: the other side's special is ready. */
  public static int cdContext(int cdStm, int cdOpp) {
    return (cdStm >= COOLDOWN ? 1 : 0) | (cdOpp >= COOLDOWN ? 2 : 0);
  }

  /** Threat-family indices written into out[0..2] (see {@link #threatIndices(Board, int, int, int)}). */
  public static void threatIndices(Board b, int stm, int cdStm, int cdOpp, int[] out) {
    int p = threatIndices(b, stm, cdStm, cdOpp);
    out[0] = t1(p);
    out[1] = t2(p);
    out[2] = t3(p);
  }

  public static int t1(int packed) {
    return packed & 0x7F;
  }

  public static int t2(int packed) {
    return (packed >>> 7) & 0x3FF;
  }

  public static int t3(int packed) {
    return packed >>> 17;
  }

  /**
   * Threat-family indices for the side to move (design 3.4), packed as t1 | t2 << 7 | t3 << 17:
   * t1 = (imm me, imm opp, one me, one opp), t2 = (stk me, stk opp, nat me, unn me, nat opp, unn opp), each in
   * buckets 0, 1, 2+; t3 = (min(cdStm, 10), min(cdOpp, 10), valley columns 0, 1, 2+).
   */
  public static int threatIndices(Board b, int stm, int cdStm, int cdOpp) {
    Geometry g = b.g;
    int rows = g.rows;
    int opp = 3 - stm;
    int empty = g.cells - b.discs();
    int imm0 = 0;
    int imm1 = 0;
    int one0 = 0;
    int one1 = 0;
    int stk0 = 0;
    int stk1 = 0;
    int nat0 = 0;
    int unn0 = 0;
    int nat1 = 0;
    int unn1 = 0;
    int valleys = 0;
    int last = g.cols - 1;
    int left = 0;
    int h = b.height(0);
    for (int c = 0; c <= last; c++) {
      int here = h;
      int right = c < last ? b.height(c + 1) : 0;
      if (((c > 0 && left >= here + 3) || right >= here + 3) && here < rows) {
        valleys++;
      }
      left = here;
      h = right;
    }
    for (int c = b.nextThreatColumn(0); c >= 0; c = b.nextThreatColumn(c + 1)) {
      h = b.height(c);
      boolean latent0 = false;
      boolean latent1 = false;
      for (int y = h; y < rows; y++) {
        int cell = c * rows + y;
        boolean t0 = b.threats(stm, cell) > 0;
        boolean t1 = b.threats(opp, cell) > 0;
        if (!t0 && !t1) {
          continue;
        }
        int d = y - h;
        boolean above0 = y + 1 < rows && b.threats(stm, cell + 1) > 0;
        boolean above1 = y + 1 < rows && b.threats(opp, cell + 1) > 0;
        // the side to move gets this cell in the waiting game iff empty + rows + (y + 1) is odd
        boolean stmOwnsRow = ((empty + rows + y + 1) & 1) == 1;
        if (t0) {
          if (d == 0) {
            imm0++;
          } else if (d == 1) {
            one0++;
          }
          if (above0) {
            stk0 = Math.max(stk0, d <= 1 ? 2 : 1);
          }
          if (d >= 2 && !latent0) {
            latent0 = true;
            if (stmOwnsRow) {
              nat0++;
            } else {
              unn0++;
            }
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
            if (stmOwnsRow) {
              unn1++;
            } else {
              nat1++;
            }
          }
        }
      }
    }
    int t1 = ((b3(imm0) * 3 + b3(imm1)) * 3 + b3(one0)) * 3 + b3(one1);
    int t2 = ((((stk0 * 3 + stk1) * 3 + b3(nat0)) * 3 + b3(unn0)) * 3 + b3(nat1)) * 3 + b3(unn1);
    int t3 = (Math.min(cdStm, COOLDOWN) * 11 + Math.min(cdOpp, COOLDOWN)) * 3 + b3(valleys);
    return t1 | t2 << 7 | t3 << 17;
  }

  private static int b3(int n) {
    return Math.min(n, 2);
  }
}
