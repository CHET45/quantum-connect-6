package ch.qc.bot.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeometryTest {
  @Test
  void windowsOfDefaultBoard() {
    Geometry g = new Geometry(8, 12);
    int[] perDir = new int[4];
    for (int w = 0; w < g.windows; w++) {
      perDir[g.winDir[w]]++;
    }
    assertEquals(134, g.windows);
    assertEquals(56, perDir[Geometry.DIR_H]);
    assertEquals(36, perDir[Geometry.DIR_V]);
    assertEquals(21, perDir[Geometry.DIR_D1]);
    assertEquals(21, perDir[Geometry.DIR_D2]);
    int maxPerCell = 0;
    for (int i = 0; i < g.cells; i++) {
      maxPerCell = Math.max(maxPerCell, g.cellStart[i + 1] - g.cellStart[i]);
    }
    assertEquals(15, maxPerCell); // 6 horizontal + 3 vertical + 3 + 3 diagonal (design 2.2 says 21)
  }

  @Test
  void boardsTooSmallForALineHaveNoWindows() {
    assertEquals(0, new Geometry(5, 5).windows);
    assertEquals(0, new Geometry(1, 1).windows);
    assertEquals(7, new Geometry(1, 12).windows);
    assertEquals(25 * 20 * 2 + 20 * 20 * 2, new Geometry(25, 25).windows);
  }

  @Test
  void windowCellsAreAdjacentAndInsideTheBoard() {
    Geometry g = new Geometry(7, 9, 4);
    for (int w = 0; w < g.windows; w++) {
      for (int i = 0; i < g.k; i++) {
        int c = g.winCells[w * g.k + i];
        assertTrue(c >= 0 && c < g.cells);
        boolean found = false;
        for (int j = g.cellStart[c]; j < g.cellStart[c + 1]; j++) {
          found |= g.cellWin[j] == w && g.cellPos[j] == i;
        }
        assertTrue(found, "cell index missing for window " + w);
      }
    }
  }

  @Test
  void threatTables() {
    Tables t = new Tables(6);
    assertEquals(729, t.codes);
    assertEquals(364, t.six[1]);
    assertEquals(728, t.six[2]);
    int fiveOfFirstGapAt2 = t.six[1] - t.pow3[2];
    assertEquals(2, t.thrPos[1][fiveOfFirstGapAt2]);
    assertEquals(-1, t.thrPos[2][fiveOfFirstGapAt2]);
    assertEquals(t.six[2], t.swap12[t.six[1]]);
    assertTrue(t.live3[1][3 * 1 + 9 * 1 + 1]);
    assertTrue(!t.live3[1][3 * 1 + 9 * 1 + 1 + 2 * t.pow3[5]]);
  }
}
