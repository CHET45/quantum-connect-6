package ch.qc.bot.eval;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;

/**
 * Learned evaluation for any board size ({@link GenericSpec}): the same sum as {@link LearnedEval}, but a window's
 * parameter index is computed from its class and canonical code instead of read from a per-window table, so the
 * tables stay small (a few hundred kB for all contexts) and one blob covers every board. One instance per board
 * geometry, built from the shared tables in microseconds; immutable, shareable between bots.
 */
public final class GenericEval implements Evaluator {
  private final Geometry g;
  private final short[] swap12;
  private final int codes;
  /** Parameter base (class * codes) and canonical-code table of each window. */
  private final int[] base;
  private final int[][] canonOf;
  private final short[][] w;
  private final boolean staged;
  private final short[] t1;
  private final short[] t2;
  private final short[] t3;

  /** Tables of a generic blob ({@link #fits}): w[stage * cdContexts + cd][class * codes + canonical code]. */
  GenericEval(Geometry g, short[][] w, boolean staged, short[] t1, short[] t2, short[] t3) {
    this.g = g;
    this.swap12 = g.tables.swap12;
    this.codes = g.tables.codes;
    int[][] canon = GenericSpec.canonTables(g.k);
    base = new int[g.windows];
    canonOf = new int[g.windows][];
    for (int win = 0; win < g.windows; win++) {
      base[win] = GenericSpec.windowClass(g, win) * codes;
      canonOf[win] = canon[GenericSpec.kind(g, win)];
    }
    this.w = w;
    this.staged = staged;
    this.t1 = t1;
    this.t2 = t2;
    this.t3 = t3;
  }

  /** Splits the window table of a generic blob into one array per context. */
  static short[][] contexts(WeightBlob blob) {
    int contexts = blob.stages * blob.cdContexts;
    int params = blob.classes * blob.codes;
    short[][] w = new short[contexts][];
    for (int ctx = 0; ctx < contexts; ctx++) {
      short[] dst = new short[params];
      for (int i = 0; i < params; i++) {
        dst[i] = blob.w[ctx * params + i];
      }
      w[ctx] = dst;
    }
    return w;
  }

  public boolean fits(Geometry other) {
    return other == g;
  }

  @Override
  public int evaluate(Board b, int stm, int cdStm, int cdOpp) {
    int s = rawSum(b, stm, cdStm, cdOpp);
    return Math.max(-MAX_EVAL, Math.min(MAX_EVAL, s));
  }

  /** The unclamped integer sum. */
  public int rawSum(Board b, int stm, int cdStm, int cdOpp) {
    int cd = Features.cdContext(cdStm, cdOpp);
    short[] table = w[staged ? Features.stage(b) * Features.CD_CONTEXTS + cd : cd];
    int sum = 0;
    if (stm == Board.FIRST) {
      for (int win = 0; win < g.windows; win++) {
        sum += table[base[win] + canonOf[win][b.windowCode(win)]];
      }
    } else {
      for (int win = 0; win < g.windows; win++) {
        sum += table[base[win] + canonOf[win][swap12[b.windowCode(win)]]];
      }
    }
    int packed = Features.threatIndices(b, stm, cdStm, cdOpp);
    sum += t1[cd * Features.T1_SIZE + Features.t1(packed)];
    sum += t2[cd * Features.T2_SIZE + Features.t2(packed)];
    sum += t3[Features.t3(packed)];
    return sum;
  }
}
