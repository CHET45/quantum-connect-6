package ch.qc.bot.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Mirror tying of the window family (design 3.2) and the threat-family index ranges. */
class FeatureSpecTest {
  @Test
  void classesOfTheDefaultBoard() {
    FeatureSpec s = new FeatureSpec(new Geometry(8, 12));
    assertEquals(71, s.classes); // 32 horizontal + 18 vertical + 21 diagonal
    for (int x : s.featMap) {
      assertTrue(x >= 0 && x < s.windowParams());
    }
  }

  /** A random parameter vector gives a position and its left-right mirror the same window sum. */
  @Test
  void mirrorImagesScoreTheSame() {
    for (int[] size : new int[][] {{8, 12}, {6, 7}, {9, 9}}) {
      Geometry g = new Geometry(size[0], size[1]);
      FeatureSpec s = new FeatureSpec(g);
      SplittableRandom rnd = new SplittableRandom(3);
      long[] theta = new long[s.windowParams()];
      for (int i = 0; i < theta.length; i++) {
        theta[i] = rnd.nextLong(1_000_000);
      }
      for (int trial = 0; trial < 2000; trial++) {
        Board b = new Board(g);
        int plies = rnd.nextInt(g.cells);
        for (int i = 0; i < plies; i++) {
          int c = rnd.nextInt(g.cols);
          if (!b.isFull(c)) {
            b.make(c, 1 + (i & 1));
          }
        }
        byte[] cells = b.cells();
        byte[] mirrored = new byte[cells.length];
        for (int c = 0; c < g.cols; c++) {
          System.arraycopy(cells, c * g.rows, mirrored, (g.cols - 1 - c) * g.rows, g.rows);
        }
        Board m = new Board(g);
        m.load(mirrored);
        assertEquals(sum(s, b, theta), sum(s, m, theta), "mirror sum differs, " + size[0] + "x" + size[1]);
      }
    }
  }

  @Test
  void threatIndicesStayInRange() {
    Geometry g = new Geometry(8, 12);
    SplittableRandom rnd = new SplittableRandom(4);
    int[] t = new int[3];
    for (int trial = 0; trial < 20_000; trial++) {
      Board b = new Board(g);
      for (int i = 0; i < rnd.nextInt(90); i++) {
        int c = rnd.nextInt(g.cols);
        if (!b.isFull(c) && b.make(c, 1 + (i & 1))) {
          break;
        }
      }
      Features.threatIndices(b, 1 + rnd.nextInt(2), rnd.nextInt(14), rnd.nextInt(14), t);
      assertTrue(t[0] >= 0 && t[0] < Features.T1_SIZE && t[1] >= 0 && t[1] < Features.T2_SIZE && t[2] >= 0
          && t[2] < Features.T3_SIZE);
    }
  }

  private static long sum(FeatureSpec s, Board b, long[] theta) {
    long total = 0;
    for (int w = 0; w < s.g.windows; w++) {
      total += theta[s.featMap[w * s.codes + b.windowCode(w)]];
    }
    return total;
  }
}
