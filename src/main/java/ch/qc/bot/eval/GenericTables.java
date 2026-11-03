package ch.qc.bot.eval;

import ch.qc.bot.rules.Geometry;

/**
 * The decoded tables of a generic blob ({@link GenericSpec}), shared by the {@link GenericEval} of every board size.
 * The evaluator of the last geometry asked for is kept, so a bot rebuilds it only when the board size changes.
 */
public final class GenericTables {
  private final int k;
  private final short[][] w;
  private final boolean staged;
  private final int shift;
  private final short[] t1;
  private final short[] t2;
  private final short[] t3;
  private volatile GenericEval last;

  private GenericTables(WeightBlob blob) {
    k = blob.k;
    w = GenericEval.contexts(blob);
    staged = blob.stages == Features.STAGES;
    shift = blob.scale == 1024 ? 2 : 0;
    t1 = blob.t1;
    t2 = blob.t2;
    t3 = blob.t3;
  }

  /** The tables of a generic blob, or null for any other blob. */
  public static GenericTables fromBlob(WeightBlob blob) {
    return blob != null && blob.generic() ? new GenericTables(blob) : null;
  }

  /** The evaluation for boards of geometry g (windows of k cells), or null for another window length. */
  public GenericEval forGeometry(Geometry g) {
    if (g.k != k) {
      return null;
    }
    GenericEval e = last;
    if (e == null || !e.fits(g)) {
      e = new GenericEval(g, w, staged, shift, t1, t2, t3);
      last = e;
    }
    return e;
  }
}
