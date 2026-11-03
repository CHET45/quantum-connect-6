package ch.qc.bot.eval;

import ch.qc.bot.rules.Board;

/** Static evaluation in units of 1/256 logit from the side to move's view, clamped to +-10000. */
public interface Evaluator {
  int MAX_EVAL = 10_000;

  /**
   * @param cdStm engine cooldown counter of the side to move before its turn
   * @param cdOpp the other side's counter before its next turn
   */
  int evaluate(Board b, int stm, int cdStm, int cdOpp);
}
