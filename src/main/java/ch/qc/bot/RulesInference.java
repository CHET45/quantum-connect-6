package ch.qc.bot;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Rules;

/**
 * What the engine's behaviour in this game proves about rules the bot was told wrongly (or not at all). Only safe
 * conclusions are drawn, each of which can only make the bot more careful:
 *
 * <ul>
 *   <li>a line of L discs on the board while the game still runs proves k &gt; L (the engine checks the whole board
 *       after every move, rules R3/R4), so k rises to L + 1; a smaller k cannot be seen, as the game would be over;
 *   <li>an own special the engine refused at counter c proves a cooldown above c (R11: a refused request becomes a
 *       normal drop in the history);
 *   <li>an opponent special at our estimate c of its counter proves a cooldown of at most c (the estimate is never
 *       below the true counter, R12, so the bound is never too low).
 * </ul>
 *
 * With rules that are right none of this ever fires, and the bot plays exactly as without it. Everything learnt is
 * dropped at the start of each game (rules may change from match to match).
 */
final class RulesInference {
  private RulesInference() {}

  /** The rules to play by: the base rules corrected by what this game proved. */
  static Rules adjust(Rules base, int kAtLeast, int cooldownAtLeast, int cooldownAtMost) {
    int k = Math.max(base.k, kAtLeast);
    int cd = base.cooldown;
    if (cooldownAtMost >= 0 && cd > cooldownAtMost) {
      cd = cooldownAtMost;
    }
    if (cd < cooldownAtLeast) {
      cd = cooldownAtLeast;
    }
    if (k == base.k && cd == base.cooldown) {
      return base;
    }
    return Rules.of(k, base.blastW, base.blastH, cd);
  }

  /** Longest run of one colour in any of the four directions; cells in Geometry order (col * rows + y). */
  static int longestLine(byte[] cells, int rows, int cols) {
    int best = 0;
    for (int c = 0; c < cols; c++) {
      for (int y = 0; y < rows; y++) {
        int v = cells[c * rows + y];
        if (v == Board.EMPTY) {
          continue;
        }
        // count each run once, from its first cell
        best = Math.max(best, run(cells, rows, cols, c, y, v, 1, 0));
        best = Math.max(best, run(cells, rows, cols, c, y, v, 0, 1));
        best = Math.max(best, run(cells, rows, cols, c, y, v, 1, 1));
        best = Math.max(best, run(cells, rows, cols, c, y, v, 1, -1));
      }
    }
    return best;
  }

  private static int run(byte[] cells, int rows, int cols, int c, int y, int v, int dc, int dy) {
    int pc = c - dc;
    int py = y - dy;
    if (pc >= 0 && pc < cols && py >= 0 && py < rows && cells[pc * rows + py] == v) {
      return 0;
    }
    int n = 0;
    while (c >= 0 && c < cols && y >= 0 && y < rows && cells[c * rows + y] == v) {
      n++;
      c += dc;
      y += dy;
    }
    return n;
  }
}
