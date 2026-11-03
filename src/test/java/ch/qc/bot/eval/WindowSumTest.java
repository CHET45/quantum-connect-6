package ch.qc.bot.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.tools.WeightFiles;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * Evaluations keep their window sums per level of the drop stack on the board. On random play with drops, take-backs
 * in and out of stack order, blasts and copies, every value must equal the value on a fresh board with the same cells,
 * which starts from a full sum, for the contexts the evaluation distinguishes.
 */
class WindowSumTest {
  private static final int[][] COUNTERS = {{0, 0}, {3, 9}, {10, 2}, {7, 10}, {10, 10}};

  private static List<Evaluator> evaluators(Geometry g) throws Exception {
    List<Evaluator> out = new ArrayList<>();
    out.add(new HandEval(g));
    Evaluator embedded = Weights.forGeometry(g);
    if (embedded != null) {
      out.add(embedded);
    }
    out.add(GenericTables.fromBlob(WeightFiles.parse(GenericEvalTest.genericBlob(Features.STAGES, 7))).forGeometry(g));
    out.add(GenericTables.fromBlob(WeightFiles.parse(GenericEvalTest.genericBlob(1, 8, 1024))).forGeometry(g));
    return out;
  }

  private static int raw(Evaluator e, Board b, int stm, int[] cd) {
    if (e instanceof LearnedEval l) {
      return l.rawSum(b, stm, cd[0], cd[1]);
    }
    if (e instanceof GenericEval l) {
      return l.rawSum(b, stm, cd[0], cd[1]);
    }
    return e.evaluate(b, stm, cd[0], cd[1]);
  }

  @Test
  void sumsMatchAFreshBoard() throws Exception {
    SplittableRandom rnd = new SplittableRandom(20261104L);
    int[][] sizes = {{8, 12}, {15, 11}, {15, 22}, {7, 9}, {20, 20}};
    long checks = 0;
    for (int[] size : sizes) {
      Geometry g = new Geometry(size[0], size[1]);
      List<Evaluator> evals = evaluators(g);
      Board fresh = new Board(g);
      Board copy = new Board(g);
      for (int game = 0; game < 30; game++) {
        Board b = new Board(g);
        int[] stack = new int[g.cells + 1];
        int n = 0;
        for (int step = 0; step < 2 * g.cells; step++) {
          int r = rnd.nextInt(12);
          int col = rnd.nextInt(g.cols);
          int colour = 1 + (step & 1);
          if (r == 0) {
            b.blast(col);
            n = 0;
          } else if (r == 1 && n > 1) {
            // out of stack order: the drop under the top of the stack, if nothing lies on its disc
            int c = stack[n - 2];
            if (c != stack[n - 1]) {
              b.unmake(c);
              stack[n - 2] = stack[n - 1];
              n--;
            }
          } else if (r <= 4 && n > 0) {
            b.unmake(stack[--n]);
          } else if (r == 5) {
            copy.copyFrom(b);
            Board t = b;
            b = copy;
            copy = t;
            n = 0;
          } else if (!b.isFull(col) && b.threats(colour, g.index(col, b.height(col))) == 0) {
            b.make(col, colour);
            stack[n++] = col;
          }
          if (b.hasLine(0)) {
            break;
          }
          assertTrue(fresh.load(b.cells()));
          // a sum per evaluation and context, in random order: the board forgets the sums of the previous evaluation
          for (int i = 0; i < 3; i++) {
            Evaluator e = evals.get(rnd.nextInt(evals.size()));
            int stm = 1 + rnd.nextInt(2);
            int[] cd = COUNTERS[rnd.nextInt(COUNTERS.length)];
            assertEquals(raw(e, fresh, stm, cd), raw(e, b, stm, cd),
                g.rows + "x" + g.cols + " " + e.getClass().getSimpleName() + " step " + step);
            checks++;
          }
        }
      }
    }
    System.out.println("window sums: " + checks + " evaluations compared");
  }

  /** The same evaluation many times on one board: the memory of per-level sums must never serve a stale value. */
  @Test
  void repeatedDropsAtOneLevelDoNotReuseSums() {
    Geometry g = new Geometry(15, 11);
    HandEval e = new HandEval(g);
    Board b = new Board(g);
    Board fresh = new Board(g);
    for (int c = 0; c < g.cols; c++) {
      b.make(c, 1 + (c & 1));
      for (int d = 0; d < g.cols; d++) {
        b.make(d, 1);
        fresh.load(b.cells());
        assertEquals(e.evaluate(fresh, 2, 0, 0), e.evaluate(b, 2, 0, 0));
        b.unmake(d);
        fresh.load(b.cells());
        assertEquals(e.evaluate(fresh, 1, 0, 0), e.evaluate(b, 1, 0, 0));
      }
    }
  }
}
