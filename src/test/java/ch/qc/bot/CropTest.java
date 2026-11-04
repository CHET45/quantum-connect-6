package ch.qc.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.QuantumField;
import ch.cern.quantumconnect.core.ExperimentStatus;
import ch.cern.quantumconnect.core.FieldFactory;
import org.junit.jupiter.api.Test;

class CropTest {
  private static final int ABOVE = 40;

  private static byte[] board(int rows, int cols, int[] heights, int firstCol) {
    byte[] cells = new byte[rows * cols];
    for (int i = 0; i < heights.length; i++) {
      for (int y = 0; y < heights[i]; y++) {
        cells[(firstCol + i) * rows + y] = (byte) (1 + (y + i) % 2);
      }
    }
    return cells;
  }

  @Test
  void aBoardUpToTheLimitIsNeverCropped() {
    Crop c = Crop.start(25, 40, ABOVE);
    assertTrue(c.whole());
    byte[] cells = board(25, 40, new int[] {20}, 0);
    assertSame(c, c.refit(cells, ABOVE));
    assertSame(cells, c.cut(cells));
  }

  @Test
  void theStartSurroundsTheCentreColumn() {
    Crop c = Crop.start(100, 100, ABOVE);
    int centre = QcBot.centreOrder(100, 0);
    assertTrue(c.col0 + Crop.KEEP <= centre && centre + Crop.KEEP < c.col0 + c.cols);
    assertEquals(Crop.GROW, c.rows);
  }

  @Test
  void itStaysWhileTheMarginHoldsAndGrowsWhenItDoesNot() {
    Crop c = Crop.start(100, 100, ABOVE);
    int centre = QcBot.centreOrder(100, 0);
    assertSame(c, c.refit(board(100, 100, new int[] {1, 2, 1}, centre - 1), ABOVE));
    int near = c.col0 + c.cols - Crop.KEEP;
    Crop wider = c.refit(board(100, 100, new int[] {1}, near), ABOVE);
    assertTrue(near + Crop.KEEP < wider.col0 + wider.cols);
    Crop taller = c.refit(board(100, 100, new int[] {Crop.GROW}, centre), ABOVE);
    assertTrue(taller.rows >= Crop.GROW + Crop.KEEP);
    assertSame(wider, wider.refit(board(100, 100, new int[] {1}, near), ABOVE));
  }

  @Test
  void aSmallCropFollowsANewGameElsewhere() {
    Crop c = Crop.start(100, 100, ABOVE);
    Crop moved = c.refit(board(100, 100, new int[] {1}, 3), ABOVE);
    assertTrue(moved.col0 <= 3 - Crop.KEEP || moved.col0 == 0);
    Crop back = moved.refit(board(100, 100, new int[] {1}, 3), ABOVE);
    assertSame(moved, back);
    Crop shrunk = moved.refit(board(100, 100, new int[] {1}, 97), ABOVE);
    assertTrue(shrunk.col0 + shrunk.cols == 100 || 97 + Crop.KEEP < shrunk.col0 + shrunk.cols);
  }

  @Test
  void aCropNeverLeavesTheRealBoard() {
    for (int col : new int[] {0, 1, 5, 500, 994, 999}) {
      Crop c = Crop.start(1000, 1000, ABOVE).refit(board(1000, 1000, new int[] {1}, col), ABOVE);
      assertTrue(c.col0 >= 0 && c.col0 + c.cols <= 1000, "column " + col);
      assertTrue(c.col0 <= col && col < c.col0 + c.cols, "column " + col);
    }
  }

  @Test
  void discsSpreadTooWideLeaveTheCropOnTheDensestPart() {
    int cols = 1000;
    byte[] cells = new byte[20 * cols];
    cells[0] = 1;
    for (int c = 700; c < 720; c++) {
      cells[c * 20] = 2;
      cells[c * 20 + 1] = 1;
    }
    Crop c = Crop.start(20, cols, ABOVE).refit(cells, ABOVE);
    assertEquals(Crop.MAX_COLS, c.cols);
    assertTrue(c.col0 <= 700 && 720 <= c.col0 + c.cols);
    assertSame(c, c.refit(cells, ABOVE));
  }

  @Test
  void cutKeepsCellsInPlaceRelativeToTheCrop() {
    Crop c = Crop.start(100, 100, ABOVE);
    int centre = QcBot.centreOrder(100, 0);
    byte[] cells = board(100, 100, new int[] {3}, centre);
    byte[] cut = c.cut(cells);
    int m = centre - c.col0;
    assertEquals(cells[centre * 100], cut[m * c.rows]);
    assertEquals(cells[centre * 100 + 2], cut[m * c.rows + 2]);
    assertEquals(0, cut[m * c.rows + 3]);
  }

  @Test
  void whatCannotBeModelledGivesNoCrop() {
    assertNull(Crop.start(300, 300, 1000));
    assertNotNull(Crop.start(300, 300, ABOVE));
    assertNotNull(Crop.start(5000, 1, ABOVE));
    assertNotNull(Crop.start(1, 5000, ABOVE));
  }

  /** The search really runs on very large boards, and every move it gives is a legal real column near the discs. */
  @Test
  void theBotSearchesOnVeryLargeBoards() {
    int[][] sizes = {{100, 100}, {50, 50}, {65, 65}, {200, 200}, {300, 12}, {12, 300}, {500, 500}};
    for (int[] size : sizes) {
      QcBot bot = new QcBot("w=0");
      Algorithm other = new QcBot("w=0");
      QuantumField field = FieldFactory.create(size[0], size[1], bot, other);
      int searched = 0;
      boolean botToMove = true;
      for (int ply = 0; ply < 30 && FieldFactory.status(field) == ExperimentStatus.INCOMPLETE; ply++) {
        Algorithm mover = botToMove ? bot : other;
        int col = mover.accelerateQuark(field);
        String where = size[0] + "x" + size[1] + " ply " + ply;
        assertTrue(col >= 0 && col < size[1] && !field.isColumnFull(col), where + " column " + col);
        if (botToMove && bot.lastResult() != null) {
          searched++;
        }
        assertTrue(Math.abs(col - QcBot.centreOrder(size[1], 0)) <= Crop.MAX_COLS, where + " far column " + col);
        FieldFactory.play(field, col, mover.useSpecialMove(field));
        botToMove = !botToMove;
      }
      assertTrue(searched >= 5, size[0] + "x" + size[1] + " searched only " + searched + " turns");
    }
  }
}
