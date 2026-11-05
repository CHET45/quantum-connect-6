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
      // wide=0: this checks the search near the discs, not the far opening drops
      QcBot bot = new QcBot("w=0,wide=0");
      Algorithm other = new QcBot("w=0,wide=0");
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

  private static boolean[] occupied(int cols, int... discs) {
    boolean[] o = new boolean[cols];
    for (int c : discs) {
      o[c] = true;
    }
    return o;
  }

  @Test
  void gapsKeepOnlyTheColumnsNearDiscs() {
    int[] m = Crop.gapMap(occupied(100, 3, 60), 100);
    int reach = Crop.GAP_REACH;
    assertEquals(3 + reach + 1 + 2 * reach + 1, m.length);
    assertEquals(0, m[0]);
    assertEquals(3 + reach, m[3 + reach]);
    assertEquals(60 - reach, m[3 + reach + 1]);
    assertEquals(60 + reach, m[m.length - 1]);
    // a narrow empty stretch is kept, so a cut always hides more than GAP_FILL columns
    int[] near = Crop.gapMap(occupied(100, 40, 40 + 2 * reach + Crop.GAP_FILL + 1), 100);
    assertEquals(near[near.length - 1] - near[0] + 1, near.length);
    int[] apart = Crop.gapMap(occupied(100, 40, 40 + 2 * reach + Crop.GAP_FILL + 2), 100);
    assertEquals(apart[apart.length - 1] - apart[0] + 1, apart.length + Crop.GAP_FILL + 1);
  }

  @Test
  void aGapCropCutsTheRealColumnsAndMapsBothWays() {
    int rows = 15;
    int cols = 100;
    byte[] cells = new byte[rows * cols];
    cells[2 * rows] = 1;
    cells[49 * rows] = 2;
    cells[49 * rows + 1] = 1;
    cells[95 * rows] = 2;
    Crop c = Crop.start(rows, cols, ABOVE).refit(cells, ABOVE, true);
    assertTrue(c.hasGaps());
    assertTrue(c.cols < 3 * (2 * Crop.GAP_REACH + 1));
    byte[] cut = c.cut(cells);
    for (int m = 0; m < c.cols; m++) {
      int r = c.real(m);
      assertEquals(m, c.model(r));
      for (int y = 0; y < rows; y++) {
        assertEquals(cells[r * rows + y], cut[m * rows + y], "model column " + m + " row " + y);
      }
    }
    assertEquals(-1, c.model(25));
    assertEquals(-1, c.model(72));
    assertSame(c, c.refit(cells, ABOVE, true));
    // a disc next to a group stays inside the crop: no rebuild
    cells[(49 + Crop.GAP_REACH - Crop.KEEP) * rows] = 1;
    assertSame(c, c.refit(cells, ABOVE, true));
    // a disc in a left-out stretch makes a new crop that holds it
    cells[25 * rows] = 2;
    Crop d = c.refit(cells, ABOVE, true);
    assertTrue(d.model(25) >= 0);
  }

  @Test
  void withoutGapsTheCropIsTheContiguousOne() {
    byte[] cells = board(15, 100, new int[] {1}, 3);
    cells[95 * 15] = 1;
    Crop c = Crop.start(15, 100, ABOVE);
    Crop plain = c.refit(cells, ABOVE);
    assertSame(plain.getClass(), c.refit(cells, ABOVE, false).getClass());
    Crop again = c.refit(cells, ABOVE, false);
    assertEquals(plain.col0, again.col0);
    assertEquals(plain.cols, again.cols);
    assertTrue(!again.hasGaps());
  }

  /** An opponent that drops far from everything: with gaps the bot still only plays legal columns and searches. */
  @Test
  void withGapsTheBotPlaysAWideGameLegally() {
    int rows = 15;
    int cols = 100;
    QcBot bot = new QcBot("w=0,gap=1");
    int[] far = {0, 97, 25, 70, 3, 88, 12, 60, 40, 99, 33, 80};
    Algorithm other = new Algorithm() {
      int i;

      @Override
      public int accelerateQuark(QuantumField f) {
        for (int k = 0; k < cols; k++) {
          int c = far[i++ % far.length];
          if (!f.isColumnFull(c)) {
            return c;
          }
        }
        return 0;
      }

      @Override
      public boolean useSpecialMove(QuantumField f) {
        return false;
      }
    };
    QuantumField field = FieldFactory.create(rows, cols, bot, other);
    int searched = 0;
    int botTurns = 0;
    boolean botToMove = true;
    for (int ply = 0; ply < 80 && FieldFactory.status(field) == ExperimentStatus.INCOMPLETE; ply++) {
      Algorithm mover = botToMove ? bot : other;
      int col = mover.accelerateQuark(field);
      assertTrue(col >= 0 && col < cols && !field.isColumnFull(col), "ply " + ply + " column " + col);
      if (botToMove) {
        botTurns++;
        searched += bot.lastResult() != null ? 1 : 0;
      }
      FieldFactory.play(field, col, mover.useSpecialMove(field));
      botToMove = !botToMove;
    }
    // the opponent never blocks, so the game is short; every turn after the first few must have searched
    assertTrue(botTurns >= 6 && searched >= botTurns - 3, "searched " + searched + " of " + botTurns + " turns");
  }

  @Test
  void levelTwoLeavesOutSmallFarGroupsUntilOneGrows() {
    int rows = 15;
    int cols = 100;
    byte[] cells = new byte[rows * cols];
    cells[49 * rows] = 1;
    cells[49 * rows + 1] = 1;
    cells[49 * rows + 2] = 1;
    cells[50 * rows] = 2;
    cells[5 * rows] = 2;
    cells[90 * rows] = 1;
    cells[91 * rows] = 2;
    Crop start = Crop.start(rows, cols, ABOVE);
    Crop one = start.refit(cells, ABOVE, 1);
    assertTrue(one.model(5) >= 0 && one.model(90) >= 0);
    Crop two = start.refit(cells, ABOVE, 2);
    assertEquals(-1, two.model(5));
    assertEquals(-1, two.model(90));
    assertTrue(two.model(49 - Crop.KEEP) >= 0 && two.model(50 + Crop.KEEP) >= 0);
    // the far group reaches three discs of one side: now it is modelled
    cells[92 * rows] = 2;
    cells[93 * rows] = 2;
    Crop grown = two.refit(cells, ABOVE, 2);
    assertTrue(grown.model(90) >= 0 && grown.model(93) >= 0);
    // with no large group at all nothing is left out
    byte[] early = new byte[rows * cols];
    early[49 * rows] = 1;
    early[20 * rows] = 2;
    Crop e = start.refit(early, ABOVE, 2);
    assertTrue(e.model(20) >= 0 && e.model(49) >= 0);
  }

  @Test
  void levelTwoPlaysAWideGameLegally() {
    int rows = 15;
    int cols = 100;
    QcBot bot = new QcBot("w=0,gap=2");
    int[] far = {0, 97, 25, 70, 3, 88, 12, 60, 40, 99, 33, 80};
    Algorithm other = new Algorithm() {
      int i;

      @Override
      public int accelerateQuark(QuantumField f) {
        for (int k = 0; k < cols; k++) {
          int c = far[i++ % far.length];
          if (!f.isColumnFull(c)) {
            return c;
          }
        }
        return 0;
      }

      @Override
      public boolean useSpecialMove(QuantumField f) {
        return false;
      }
    };
    QuantumField field = FieldFactory.create(rows, cols, bot, other);
    boolean botToMove = true;
    for (int ply = 0; ply < 80 && FieldFactory.status(field) == ExperimentStatus.INCOMPLETE; ply++) {
      Algorithm mover = botToMove ? bot : other;
      int col = mover.accelerateQuark(field);
      assertTrue(col >= 0 && col < cols && !field.isColumnFull(col), "ply " + ply + " column " + col);
      FieldFactory.play(field, col, mover.useSpecialMove(field));
      botToMove = !botToMove;
    }
  }

  /** wide=2: two early quiet turns drop near an edge, far from the play; the rest of the game is searched as usual. */
  @Test
  void wideDropsFarOnlyEarlyAndLegally() {
    int rows = 15;
    int cols = 100;
    QcBot bot = new QcBot("w=0,gap=2,wide=2");
    Algorithm other = new QcBot("w=0");
    QuantumField field = FieldFactory.create(rows, cols, bot, other);
    int far = 0;
    boolean botToMove = true;
    for (int ply = 0; ply < 60 && FieldFactory.status(field) == ExperimentStatus.INCOMPLETE; ply++) {
      Algorithm mover = botToMove ? bot : other;
      int col = mover.accelerateQuark(field);
      assertTrue(col >= 0 && col < cols && !field.isColumnFull(col), "ply " + ply + " column " + col);
      if (botToMove && (col == 1 || col == cols - 2)) {
        far++;
      }
      FieldFactory.play(field, col, mover.useSpecialMove(field));
      botToMove = !botToMove;
    }
    assertTrue(far >= 1 && far <= 2, "far drops " + far);
  }
}
