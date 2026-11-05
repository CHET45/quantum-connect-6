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

  /** Blast half sizes tried when a special shows in the history: 0..MAX_TRY each way. */
  static final int MAX_TRY = 3;
  /** Every candidate blast size: bit (w * (MAX_TRY + 1) + h). */
  static final int ALL_BLASTS = (1 << ((MAX_TRY + 1) * (MAX_TRY + 1))) - 1;

  /** The rules to play by: the base rules corrected by what this game proved. */
  static Rules adjust(Rules base, int kAtLeast, int cooldownAtLeast, int cooldownAtMost) {
    return adjust(base, kAtLeast, cooldownAtLeast, cooldownAtMost, ALL_BLASTS);
  }

  /**
   * As above, and the blast size: the base size while the replays of seen specials agree with it, else the agreeing
   * size nearest to it (ties to the larger block).
   */
  static Rules adjust(Rules base, int kAtLeast, int cooldownAtLeast, int cooldownAtMost, int blastMask) {
    int k = Math.max(base.k, kAtLeast);
    int cd = base.cooldown;
    if (cooldownAtMost >= 0 && cd > cooldownAtMost) {
      cd = cooldownAtMost;
    }
    if (cd < cooldownAtLeast) {
      cd = cooldownAtLeast;
    }
    int bw = base.blastW;
    int bh = base.blastH;
    if (blastMask != ALL_BLASTS && blastMask != 0 && !(bw <= MAX_TRY && bh <= MAX_TRY && (blastMask & bit(bw, bh)) != 0)) {
      int best = Integer.MAX_VALUE;
      for (int w = 0; w <= MAX_TRY; w++) {
        for (int h = 0; h <= MAX_TRY; h++) {
          if ((blastMask & bit(w, h)) == 0) {
            continue;
          }
          // distance first, then the larger block
          int d = (Math.abs(w - base.blastW) + Math.abs(h - base.blastH)) * 64 - (w + h);
          if (d < best) {
            best = d;
            bw = w;
            bh = h;
          }
        }
      }
    }
    if (k == base.k && cd == base.cooldown && bw == base.blastW && bh == base.blastH) {
      return base;
    }
    return Rules.of(k, bw, bh, cd);
  }

  static int bit(int w, int h) {
    return 1 << (w * (MAX_TRY + 1) + h);
  }

  /**
   * The blast sizes under which the moves history[from..] turn the board before into the board after: each move is
   * replayed on a copy for each candidate size. Cells in Geometry order (col * rows + y, y = 0 bottom).
   */
  static int blastCandidates(byte[] before, byte[] after, int rows, int cols,
      java.util.List<ch.cern.quantumconnect.core.Move> history, int from) {
    int mask = 0;
    byte[] work = new byte[before.length];
    for (int w = 0; w <= MAX_TRY; w++) {
      for (int h = 0; h <= MAX_TRY; h++) {
        for (int i = 0; i < before.length; i++) {
          work[i] = before[i];
        }
        boolean ok = true;
        for (int m = from; m < history.size() && ok; m++) {
          ch.cern.quantumconnect.core.Move mv = history.get(m);
          int col = mv.column();
          if (col < 0 || col >= cols) {
            ok = false;
          } else if (mv.isSpecialMove()) {
            blast(work, rows, cols, col, w, h);
          } else {
            ok = drop(work, rows, col, Snapshot.encode(mv.player()));
          }
        }
        if (ok && same(work, after)) {
          mask |= bit(w, h);
        }
      }
    }
    return mask;
  }

  private static boolean same(byte[] a, byte[] b) {
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) {
        return false;
      }
    }
    return true;
  }

  private static int height(byte[] cells, int rows, int col) {
    int base = col * rows;
    int y = 0;
    while (y < rows && cells[base + y] != Board.EMPTY) {
      y++;
    }
    return y;
  }

  static boolean drop(byte[] cells, int rows, int col, int colour) {
    int h = height(cells, rows, col);
    if (h >= rows) {
      return false;
    }
    cells[col * rows + h] = (byte) colour;
    return true;
  }

  /** Board.blast on bare cells: the disc lands at height h, the block around it is cleared, columns compact. */
  static void blast(byte[] cells, int rows, int cols, int col, int bw, int bh) {
    int h = height(cells, rows, col);
    if (h >= rows) {
      return;
    }
    int lo = Math.max(h - bh, 0);
    for (int x = Math.max(col - bw, 0); x <= Math.min(col + bw, cols - 1); x++) {
      int top = x == col ? h : height(cells, rows, x);
      int hi = Math.min(h + bh, top - 1);
      if (lo > hi) {
        continue;
      }
      int cleared = hi - lo + 1;
      int base = x * rows;
      for (int y = lo; y < top; y++) {
        int src = y + cleared;
        cells[base + y] = src < top ? cells[base + src] : (byte) Board.EMPTY;
      }
    }
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
