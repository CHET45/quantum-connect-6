package ch.qc.bot.eval;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;

/**
 * Learned evaluation for any board size ({@link GenericSpec}): the same sum as {@link LearnedEval}, but a window's
 * table is chosen by its class and the direction it is read in instead of being its own, so the tables stay small and one
 * blob covers every board. The tables are shared by all boards ({@link GenericTables}) and indexed by the raw window code,
 * so a window costs one array read. One instance per board geometry; immutable, shareable between bots.
 */
public final class GenericEval extends WindowSumEval {
  /** Start of each window's part of a table: (class * kinds + canonical-code kind) * codes. */
  private final int[] slot;
  /** direct[2 * context + stm - 1][slot + raw window code]. */
  private final short[][] direct;
  private final boolean staged;
  private final boolean heightStage;
  /** The tables are in 1/(256 * 2^shift) logit; the result is always in 1/256 logit. */
  private final int shift;
  private final short[] t1;
  private final short[] t2;
  private final short[] t3;

  /** Tables of a generic blob ({@link #fits}), see {@link #direct}. */
  GenericEval(Geometry g, short[][] direct, boolean staged, boolean heightStage, int bands, int shift, short[] t1,
      short[] t2, short[] t3) {
    super(g);
    int codes = g.tables.codes;
    slot = new int[g.windows];
    for (int win = 0; win < g.windows; win++) {
      slot[win] = (GenericSpec.windowClass(g, win, bands) * GenericSpec.KINDS + GenericSpec.kind(g, win)) * codes;
    }
    this.direct = direct;
    this.staged = staged;
    this.heightStage = heightStage;
    this.shift = shift;
    this.t1 = t1;
    this.t2 = t2;
    this.t3 = t3;
  }

  /** Expands the window table of a generic blob: see {@link #direct}. */
  static short[][] directTables(WeightBlob blob) {
    int contexts = blob.stages * blob.cdContexts;
    int params = blob.classes * blob.codes;
    int[][] canon = GenericSpec.canonTables(blob.k);
    int[] swap = new int[blob.codes];
    for (int code = 0; code < blob.codes; code++) {
      int swapped = 0;
      int unit = 1;
      for (int i = 0, c = code; i < blob.k; i++, c /= 3, unit *= 3) {
        int digit = c % 3;
        swapped += (digit == 0 ? 0 : 3 - digit) * unit;
      }
      swap[code] = swapped;
    }
    short[][] direct = new short[2 * contexts][];
    for (int ctx = 0; ctx < contexts; ctx++) {
      for (int side = 0; side < 2; side++) {
        short[] dst = new short[blob.classes * GenericSpec.KINDS * blob.codes];
        int at = 0;
        for (int cls = 0; cls < blob.classes; cls++) {
          for (int kind = 0; kind < GenericSpec.KINDS; kind++) {
            int[] c = canon[kind];
            for (int code = 0; code < blob.codes; code++) {
              dst[at++] = blob.w[ctx * params + cls * blob.codes + c[side == 0 ? code : swap[code]]];
            }
          }
        }
        direct[2 * ctx + side] = dst;
      }
    }
    return direct;
  }

  public boolean fits(Geometry other) {
    return other == g;
  }

  @Override
  public int evaluate(Board b, int stm, int cdStm, int cdOpp) {
    int s = rawSum(b, stm, cdStm, cdOpp);
    return Math.max(-MAX_EVAL, Math.min(MAX_EVAL, s));
  }

  /** The unclamped integer sum, in 1/256 logit. */
  public int rawSum(Board b, int stm, int cdStm, int cdOpp) {
    int cd = Features.cdContext(cdStm, cdOpp);
    int ctx = staged ? (heightStage ? Features.heightStage(b) : Features.stage(b)) * Features.CD_CONTEXTS + cd : cd;
    int key = 2 * ctx + stm - 1;
    int sum = windowSum(b, key, direct[key], stm);
    int packed = Features.threatIndices(b, stm, cdStm, cdOpp);
    sum += t1[cd * Features.T1_SIZE + Features.t1(packed)];
    sum += t2[cd * Features.T2_SIZE + Features.t2(packed)];
    sum += t3[Features.t3(packed)];
    return sum >> shift;
  }

  @Override
  int fullSum(Board b, short[] table, int stm) {
    int sum = 0;
    for (int win = 0; win < g.windows; win++) {
      sum += table[slot[win] + b.windowCode(win)];
    }
    return sum;
  }

  @Override
  int dropChange(Board b, int level, short[] table, int stm) {
    int cell = b.levelCell(level);
    int colour = b.levelColour(level);
    int[] wins = g.cellWin;
    int[] pow = g.cellPow;
    int log = b.levelLog(level);
    int end = g.cellStart[cell + 1];
    int sum = 0;
    for (int j = g.cellStart[cell]; j < end; j++) {
      int s = slot[wins[j]];
      int old = s + b.loggedCode(log++);
      sum += table[old + colour * pow[j]] - table[old];
    }
    return sum;
  }
}
