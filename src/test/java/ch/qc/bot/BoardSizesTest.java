package ch.qc.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.ExperimentStatus;
import ch.cern.quantumconnect.core.FieldFactory;
import ch.cern.quantumconnect.core.QuantumField;
import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.eval.GenericEval;
import ch.qc.bot.eval.GenericTables;
import ch.qc.bot.eval.HandEval;
import ch.qc.bot.eval.WeightBlob;
import ch.qc.bot.eval.Features;
import ch.qc.bot.eval.GenericSpec;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.rules.NaiveRules;
import ch.qc.bot.search.Searcher;
import ch.qc.bot.search.Tactics;
import ch.qc.bot.search.TranspositionTable;
import ch.qc.bot.search.Vcf;
import ch.qc.starter.SpecialMoveBot;
import ch.qc.tools.WeightFiles;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.SplittableRandom;
import java.util.zip.DeflaterOutputStream;
import org.junit.jupiter.api.Test;

/**
 * Limits tied to the board size: more columns than one word of the threat-column bit set holds, far more rows than
 * columns and the reverse, more drops than the drop stack holds, window sums at the largest table values on the largest
 * board with a model, and boards too large or too wide for a search.
 */
class BoardSizesTest {
  private static final int[][] SIZES = {{1, 1}, {1, 7}, {7, 1}, {5, 5}, {6, 6}, {6, 7}, {7, 6}, {9, 20}, {20, 9}, {8, 63},
      {8, 64}, {8, 65}, {8, 128}, {8, 129}, {130, 8}, {25, 25}, {40, 40}, {64, 64}, {2, 255}, {255, 2}, {1, 255}, {255, 1},
      {1, 4096}, {4096, 1}};

  /** Drops with one colour favoured, so threats appear everywhere; never a move that completes a line. */
  private static boolean randomDrop(Board b, SplittableRandom rnd) {
    Geometry g = b.g;
    for (int tries = 0; tries < 20; tries++) {
      int col = rnd.nextInt(g.cols);
      int colour = rnd.nextInt(4) == 0 ? 2 : 1;
      if (b.isFull(col) || b.threats(colour, g.index(col, b.height(col))) > 0) {
        continue;
      }
      b.make(col, colour);
      return true;
    }
    return false;
  }

  @Test
  void boardStateHoldsOnAnySize() {
    SplittableRandom rnd = new SplittableRandom(20261104L);
    long threatsPastFirstWord = 0;
    long winningBlasts = 0;
    for (int[] size : SIZES) {
      Geometry g = new Geometry(size[0], size[1]);
      Board b = new Board(g);
      Board child = new Board(g);
      String ctx = size[0] + "x" + size[1];
      int steps = Math.min(250, 2 * g.cells);
      for (int step = 0; step < steps; step++) {
        if (rnd.nextInt(25) == 0) {
          int col = rnd.nextInt(g.cols);
          if (b.blast(col)) {
            break;
          }
        } else if (!randomDrop(b, rnd)) {
          break;
        }
        assertTrue(b.consistent(), ctx + " incremental state at step " + step);
        int next = b.nextThreatColumn(0);
        for (int c = 0; c < g.cols; c++) {
          int sum = 0;
          for (int y = 0; y < g.rows; y++) {
            sum += b.threats(1, g.index(c, y)) + b.threats(2, g.index(c, y));
          }
          assertEquals(sum, b.columnThreats(c), ctx + " threats of column " + c);
          if (sum > 0) {
            assertEquals(c, next, ctx + " next column with threats");
            next = b.nextThreatColumn(c + 1);
            threatsPastFirstWord += c >= 64 ? 1 : 0;
          }
        }
        assertEquals(-1, next, ctx + " no column with threats left");
        if (step % 16 == 0 && g.cols <= 300) {
          boolean any = false;
          NaiveRules n = NaiveRules.fromCells(g.rows, g.cols, g.k, b.cells());
          for (int c = 0; c < g.cols; c++) {
            child.copyFrom(b);
            boolean won = child.blast(c);
            any |= won;
            winningBlasts += won ? 1 : 0;
            assertEquals(won, b.blastWins(c), ctx + " blast win test in column " + c);
            if (c % 8 == 0 && !b.isFull(c)) {
              NaiveRules m = n.copy();
              m.special(c, 1);
              assertEquals(m.hasWinner(), won, ctx + " naive rules, blast in column " + c);
            }
          }
          assertEquals(any, b.anyBlastWins(), ctx + " blast win test over all columns");
        }
      }
    }
    assertTrue(threatsPastFirstWord > 0, "no threat ever in a column of the second word of the bit set");
    System.out.println("sizes: " + threatsPastFirstWord + " threat columns past the first word, " + winningBlasts
        + " winning blasts");
  }

  /** The drop stack is much shorter than a game on a large board: going past it must only cost a full sum. */
  @Test
  void moreDropsThanTheStackHolds() throws Exception {
    Geometry g = new Geometry(40, 40);
    SplittableRandom rnd = new SplittableRandom(5);
    Evaluator[] evals = {new HandEval(g), genericEval(g, 1, 0)};
    Board b = new Board(g);
    Board fresh = new Board(g);
    int[] cols = new int[g.cells];
    int n = 0;
    for (int i = 0; i < 700; i++) {
      int col = rnd.nextInt(g.cols);
      if (b.isFull(col) || b.threats(1 + (i & 1), g.index(col, b.height(col))) > 0) {
        continue;
      }
      b.make(col, 1 + (i & 1));
      cols[n++] = col;
      if (i % 5 == 0) {
        fresh.load(b.cells());
        for (Evaluator e : evals) {
          assertEquals(e.evaluate(fresh, 1, 3, 4), e.evaluate(b, 1, 3, 4), "going up, drop " + n);
        }
      }
    }
    assertTrue(n > 400, "the test wanted more drops: " + n);
    assertTrue(b.consistent());
    while (n > 0) {
      b.unmake(cols[--n]);
      if (n % 5 == 0) {
        fresh.load(b.cells());
        for (Evaluator e : evals) {
          assertEquals(e.evaluate(fresh, 2, 3, 4), e.evaluate(b, 2, 3, 4), "going down, drop " + n);
        }
      }
    }
    assertTrue(b.consistent());
    assertEquals(0, b.discs());
  }

  /** A generic blob with every value the same: scale 256 or 1024. */
  private static GenericEval genericEval(Geometry g, int value, int shift) throws Exception {
    int codes = 729;
    int stages = 1;
    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(raw))) {
      out.writeBytes("QCW1");
      for (int x : new int[] {1, 0, 0, 6}) {
        out.writeInt(x);
      }
      out.writeLong(GenericSpec.hash(6));
      for (int x : new int[] {shift == 0 ? 256 : 1024, stages, Features.CD_CONTEXTS, GenericSpec.CLASSES, codes,
          Features.T1_SIZE, Features.T2_SIZE, Features.T3_SIZE}) {
        out.writeInt(x);
      }
      int n = stages * Features.CD_CONTEXTS * GenericSpec.CLASSES * codes
          + Features.CD_CONTEXTS * (Features.T1_SIZE + Features.T2_SIZE) + Features.T3_SIZE;
      for (int i = 0; i < n; i++) {
        out.writeShort(value);
      }
    }
    WeightBlob blob = WeightFiles.parse(raw.toByteArray());
    return GenericTables.fromBlob(blob).forGeometry(g);
  }

  /** The sums are ints: the largest table values on the board with the most windows must not wrap. */
  @Test
  void windowSumsDoNotWrapAtTheLargestValues() throws Exception {
    Geometry g = new Geometry(64, 64);
    SplittableRandom rnd = new SplittableRandom(6);
    Board b = new Board(g);
    for (int i = 0; i < 300; i++) {
      randomDrop(b, rnd);
    }
    for (int value : new int[] {Short.MAX_VALUE, Short.MIN_VALUE}) {
      long expected = (g.windows + 3L) * value;
      assertEquals(expected, genericEval(g, value, 0).rawSum(b, 1, 3, 4));
      assertEquals(expected, genericEval(g, value, 0).rawSum(b, 2, 3, 4));
      assertEquals(expected >> 2, genericEval(g, value, 2).rawSum(b, 1, 3, 4));
      int clamped = genericEval(g, value, 0).evaluate(b, 1, 3, 4);
      assertEquals(value > 0 ? Evaluator.MAX_EVAL : -Evaluator.MAX_EVAL, clamped);
    }
  }

  /** A legal move on every size a search or the fallback tactics can get: from any position, with blasts ready. */
  @Test
  void searchAndTacticsPlayLegalMoves() throws Exception {
    SplittableRandom rnd = new SplittableRandom(7);
    for (int[] size : SIZES) {
      Geometry g = new Geometry(size[0], size[1]);
      String ctx = size[0] + "x" + size[1];
      Board b = new Board(g);
      for (int i = 0; i < g.cells * 2 / 5; i++) {
        if (!randomDrop(b, rnd)) {
          break;
        }
      }
      if (b.topRowFull() || b.hasLine(0)) {
        continue;
      }
      int me = 1 + (b.discs() & 1);
      if (g.cols <= QcBot.MAX_SEARCH_COLS) {
        for (Evaluator e : new Evaluator[] {new HandEval(g), genericEval(g, 3, 0)}) {
          Searcher s = new Searcher(g, e, new TranspositionTable(14), Searcher.Options.DEFAULT, new Vcf.Table(12));
          for (int round = 0; round < 2; round++) {
            Board root = new Board(g);
            root.load(b.cells());
            int m = s.run(root, me, 10, 10, b.discs(), Clock.nanos(), 3_600_000_000_000L, 3000).move();
            int col = Tactics.column(m);
            assertTrue(m != Tactics.NONE && col >= 0 && col < g.cols && !b.isFull(col), ctx + " search move " + m);
            assertTrue(root.consistent(), ctx + " root unchanged");
            assertEquals(b.hash(), root.hash(), ctx + " root unchanged");
          }
        }
      }
      int m = new Tactics(g).choose(b, me, 10, 10, Clock.nanos() + 50_000_000L);
      assertTrue(m != Tactics.NONE && !b.isFull(Tactics.column(m)), ctx + " tactics move " + m);
    }
  }

  /** The bot's answers on boards with no search (too wide), no board model (too large) and at the edge of both. */
  @Test
  void botAnswersOnBoardsBeyondItsLimits() {
    int[][] sizes = {{8, 130}, {130, 8}, {2, 255}, {2, 256}, {1, 300}, {64, 64}, {65, 65}, {1, 5000}, {5000, 1}};
    for (int[] size : sizes) {
      QcBot bot = new QcBot();
      Algorithm other = new SpecialMoveBot();
      QuantumField field = FieldFactory.create(size[0], size[1], bot, other);
      SplittableRandom rnd = new SplittableRandom(size[0] * 100003L + size[1]);
      boolean botToMove = true;
      for (int ply = 0; ply < 40 && FieldFactory.status(field) == ExperimentStatus.INCOMPLETE; ply++) {
        int col;
        boolean special;
        if (botToMove) {
          col = bot.accelerateQuark(field);
          assertTrue(col >= 0 && col < size[1] && !field.isColumnFull(col),
              size[0] + "x" + size[1] + " bad column " + col);
          special = bot.useSpecialMove(field);
        } else {
          col = rnd.nextInt(size[1]);
          if (field.isColumnFull(col)) {
            col = QcBot.safeColumn(field);
          }
          special = false;
        }
        FieldFactory.play(field, col, special);
        botToMove = !botToMove;
      }
    }
  }
}
