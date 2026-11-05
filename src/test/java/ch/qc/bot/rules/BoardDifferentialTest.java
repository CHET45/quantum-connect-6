package ch.qc.bot.rules;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * Board against the naive engine re-implementation on random positions (design 5.1): cells, heights and win
 * flags after normal moves and blasts, incremental window codes / threat cells / hash against a rebuild, and
 * soundness of the valley prefilter. Size with -Pqc.diff.positions=10000000.
 */
class BoardDifferentialTest {
  private static final int POSITIONS = Integer.getInteger("qc.diff.positions", 300_000);

  @Test
  void boardMatchesNaiveRules() {
    SplittableRandom rnd = new SplittableRandom(20261103L);
    long positions = 0;
    long blastWins = 0;
    long prefilterPassed = 0;
    long blastsChecked = 0;
    while (positions < POSITIONS) {
      int[] size = pickSize(rnd);
      int rows = size[0];
      int cols = size[1];
      int k = size[2];
      Geometry g = new Geometry(rows, cols, k);
      Board b = new Board(g);
      Board child = new Board(g);
      Board saved = new Board(g);
      NaiveRules n = new NaiveRules(rows, cols, k);
      int favoured = rnd.nextInt(cols);
      String ctx = rows + "x" + cols + " K=" + k;
      for (int step = 0; step < 4 * rows * cols + 20 && positions < POSITIONS; step++) {
        positions++;
        assertArrayEquals(n.cells(), b.cells(), ctx + " cells at step " + step);
        assertTrue(b.consistent(), ctx + " incremental state at step " + step);
        boolean anyBlastWon = false;
        for (int c = 0; c < cols; c++) {
          assertEquals(heightOf(n, c), b.height(c), ctx + " height of column " + c);
          if (b.isFull(c)) {
            continue;
          }
          child.copyFrom(b);
          blastsChecked++;
          boolean blastWon = child.blast(c);
          anyBlastWon |= blastWon;
          assertEquals(blastWon, b.blastWins(c), ctx + " blast win test in column " + c);
          if (blastWon) {
            blastWins++;
            assertTrue(b.blastMayWin(c), ctx + " prefilter rejected a winning blast in column " + c);
          }
          if (b.blastMayWin(c)) {
            prefilterPassed++;
          }
        }
        assertEquals(anyBlastWon, b.anyBlastWins(), ctx + " blast win test over all columns");
        int nextThreat = b.nextThreatColumn(0);
        for (int c = 0; c < cols; c++) {
          if (b.columnThreats(c) > 0) {
            assertEquals(c, nextThreat, ctx + " next column with threats");
            nextThreat = b.nextThreatColumn(c + 1);
          }
        }
        assertEquals(-1, nextThreat, ctx + " no column with threats left");
        int probe = rnd.nextInt(cols);
        child.copyFrom(b);
        boolean childWin = child.blast(probe);
        NaiveRules nb = n.copy();
        nb.special(probe, 1);
        assertArrayEquals(nb.cells(), child.cells(), ctx + " blast cells in column " + probe);
        assertEquals(nb.hasWinner(), childWin, ctx + " blast win flag in column " + probe);
        assertTrue(child.consistent(), ctx + " incremental state after blast");

        int colour = 1 + (step & 1);
        int col = rnd.nextInt(4) == 0 ? favoured : rnd.nextInt(cols);
        boolean special = rnd.nextInt(5) == 0;
        boolean win;
        if (special) {
          win = b.blast(col);
          n.special(col, colour);
        } else {
          if (b.isFull(col)) {
            continue;
          }
          saved.copyFrom(b);
          win = b.make(col, colour);
          if (rnd.nextInt(8) == 0) {
            b.unmake(col);
            assertArrayEquals(saved.cells(), b.cells(), ctx + " unmake cells");
            assertEquals(saved.hash(), b.hash(), ctx + " unmake hash");
            assertTrue(b.consistent(), ctx + " unmake state");
            win = b.make(col, colour);
          }
          n.drop(col, colour);
        }
        assertEquals(n.hasWinner(), win, ctx + " win flag, special=" + special + " col=" + col);
        assertEquals(n.hasWinner(), b.hasLine(0), ctx + " full line scan");
        if (win) {
          break;
        }
        if (!special && !anyBlastWon) {
          assertEquals(b.anyBlastWins(), b.anyBlastWinsNear(col), ctx + " blast win test near a drop in column " + col);
        }
      }
    }
    System.out.printf("differential: %d positions, %d blasts simulated, %d winning blasts, prefilter passed %d%n",
        positions, blastsChecked, blastWins, prefilterPassed);
    assertTrue(blastWins > 0, "random play never produced a winning blast");
  }

  private static int heightOf(NaiveRules n, int c) {
    int h = 0;
    for (int r = n.rows - 1; r >= 0 && n.f[r][c] != 0; r--) {
      h++;
    }
    return h;
  }

  private static int[] pickSize(SplittableRandom rnd) {
    int r = rnd.nextInt(10);
    if (r < 4) {
      return new int[] {8, 12, 6};
    }
    if (r == 4) {
      return new int[] {6, 7, 4};
    }
    if (r == 5) {
      return new int[] {9 + rnd.nextInt(8), 6 + rnd.nextInt(10), 6};
    }
    return new int[] {1 + rnd.nextInt(14), 1 + rnd.nextInt(16), 4 + rnd.nextInt(3)};
  }
}
