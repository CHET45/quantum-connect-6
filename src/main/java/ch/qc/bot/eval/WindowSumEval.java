package ch.qc.bot.eval;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;

/**
 * Window sum of an evaluation that adds one table value per window. The sum is kept per level of the board's drop
 * stack ({@link Board#level}) and per table, so a position costs the windows of the drops since the nearest level
 * with a known sum, usually one drop, instead of all windows. The sums live on the board, so the evaluation stays
 * immutable and shareable between bots.
 */
abstract class WindowSumEval implements Evaluator {
  final Geometry g;

  WindowSumEval(Geometry g) {
    this.g = g;
  }

  /**
   * The sum over all windows.
   *
   * @param key small number below {@link Board#SUM_KEYS} that identifies table and stm for this evaluation
   */
  final int windowSum(Board b, int key, short[] table, int stm) {
    if (b.sumOwner() != this) {
      b.bindSums(this);
    }
    int top = b.level();
    int l = top;
    while (l >= 0 && !b.sumKnown(l, key)) {
      l--;
    }
    int s;
    if (l >= 0) {
      s = b.sum(l, key);
    } else {
      s = fullSum(b, table, stm);
      for (int x = top; x > 0; x--) {
        s -= dropChange(b, x, table, stm);
      }
      l = 0;
      b.rememberSum(0, key, s);
    }
    for (int x = l + 1; x <= top; x++) {
      s += dropChange(b, x, table, stm);
      b.rememberSum(x, key, s);
    }
    return s;
  }

  /** The sum over the board's current window codes. */
  abstract int fullSum(Board b, short[] table, int stm);

  /** What the drop at this level of the stack changed in the sum, from the logged window codes. */
  abstract int dropChange(Board b, int level, short[] table, int stm);
}
