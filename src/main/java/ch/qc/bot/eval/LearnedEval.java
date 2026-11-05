package ch.qc.bot.eval;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.rules.Tables;

/**
 * Learned pattern-table evaluation (design 3.1): the sum of int16 window values for the context (stage, cooldowns)
 * plus the threat-family tables, from the side to move's view, in 1/256 logit. The window tables are expanded per
 * window and per context at construction, so a window costs one short read. Immutable, shareable between bots.
 */
public final class LearnedEval implements Evaluator {
  private final Geometry g;
  private final short[] swap12;
  private final int codes;
  /** w[stage * cdContexts + cd][window * codes + relative code]; stage 0 only for a one-stage blob. */
  private final short[][] w;
  private final boolean staged;
  private final short[] t1;
  private final short[] t2;
  private final short[] t3;

  /** The blob must match the spec ({@link WeightBlob#matches}); callers check that first. */
  public LearnedEval(FeatureSpec spec, WeightBlob blob) {
    this.g = spec.g;
    Tables t = g.tables;
    this.swap12 = t.swap12;
    this.codes = spec.codes;
    this.staged = blob.stages == Features.STAGES;
    int contexts = blob.stages * Features.CD_CONTEXTS;
    int params = spec.windowParams();
    w = new short[contexts][g.windows * codes];
    for (int ctx = 0; ctx < contexts; ctx++) {
      short[] dst = w[ctx];
      int base = ctx * params;
      for (int i = 0; i < dst.length; i++) {
        dst[i] = blob.w[base + spec.featMap[i]];
      }
    }
    t1 = blob.t1;
    t2 = blob.t2;
    t3 = blob.t3;
  }

  /** True for a board of the size these weights were trained on. */
  public boolean fits(Geometry other) {
    return other.rows == g.rows && other.cols == g.cols && other.k == g.k;
  }

  @Override
  public int evaluate(Board b, int stm, int cdStm, int cdOpp) {
    int s = rawSum(b, stm, cdStm, cdOpp);
    return Math.max(-MAX_EVAL, Math.min(MAX_EVAL, s));
  }

  /** The unclamped integer sum (the quantity the Java-Python parity test compares). */
  public int rawSum(Board b, int stm, int cdStm, int cdOpp) {
    int cd = Features.cdContext(cdStm, cdOpp);
    short[] table = w[staged ? Features.stage(b) * Features.CD_CONTEXTS + cd : cd];
    int sum = 0;
    int off = 0;
    if (stm == Board.FIRST) {
      for (int win = 0; win < g.windows; win++, off += codes) {
        sum += table[off + b.windowCode(win)];
      }
    } else {
      for (int win = 0; win < g.windows; win++, off += codes) {
        sum += table[off + swap12[b.windowCode(win)]];
      }
    }
    int packed = Features.threatIndices(b, stm, cdStm, cdOpp);
    sum += t1[cd * Features.T1_SIZE + Features.t1(packed)];
    sum += t2[cd * Features.T2_SIZE + Features.t2(packed)];
    sum += t3[Features.t3(packed)];
    return sum;
  }
}
