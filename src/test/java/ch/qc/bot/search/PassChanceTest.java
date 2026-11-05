package ch.qc.bot.search;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qc.bot.Clock;
import ch.qc.bot.eval.HandEval;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** The lost-turn chance at the root (Searcher#setPassChance): window maths and legal play. */
class PassChanceTest {
  private static final Geometry G = new Geometry(8, 12);

  private static Searcher searcher() {
    return new Searcher(G, new HandEval(G), new TranspositionTable(16));
  }

  /** A move whose adjusted score beats alpha always has a plain score above the window it is searched with. */
  @Test
  void inverseNeverCutsAMoveThatWouldWin() {
    Searcher s = searcher();
    SplittableRandom r = new SplittableRandom(5);
    for (int t = 0; t < 200_000; t++) {
      double p = 0.002 + 0.4 * r.nextDouble();
      double w = r.nextBoolean() ? 1.0 : 0.5 + 0.5 * r.nextDouble();
      s.setPassChance(p, 2, w);
      int a = r.nextInt(-6000, 6000);
      int raw = r.nextInt(-6000, 6000);
      if (t % 50 == 0) {
        raw = r.nextBoolean() ? -(Searcher.MATE - 3) : Searcher.MATE - 3;
      }
      int bound = s.passInverse(a, w);
      if (s.passForward(raw, w) > a) {
        assertTrue(raw > bound, "p=" + p + " w=" + w + " a=" + a + " raw=" + raw + " bound=" + bound);
      }
    }
  }

  /** The adjusted score never falls below the plain one and rises with it. */
  @Test
  void forwardIsAGiftAndMonotone() {
    Searcher s = searcher();
    s.setPassChance(0.1, 2, 1.0);
    int prev = Integer.MIN_VALUE;
    for (int raw = -5000; raw <= 5000; raw += 7) {
      int f = s.passForward(raw, 1.0);
      assertTrue(f >= raw - 1, raw + " -> " + f);
      assertTrue(f >= prev, raw + " -> " + f);
      prev = f;
    }
  }

  /** Random middle-game positions: the search with a lost-turn chance plays a legal move. */
  @Test
  void playsLegalMoves() {
    SplittableRandom r = new SplittableRandom(11);
    for (int game = 0; game < 40; game++) {
      Board b = new Board(G);
      int stm = 1;
      boolean over = false;
      for (int ply = 0; ply < 20 + r.nextInt(30) && !over; ply++) {
        int c = r.nextInt(G.cols);
        if (b.isFull(c)) {
          continue;
        }
        over = b.make(c, stm) || b.topRowFull();
        stm = 3 - stm;
      }
      if (over) {
        continue;
      }
      Searcher s = searcher();
      s.setPassChance(0.2, 2, 0.85);
      int move = s.run(b, stm, 10, 10, 40, Clock.nanos(), 30_000_000L).move();
      assertNotEquals(Tactics.NONE, move);
      assertTrue(!b.isFull(Tactics.column(move)));
    }
  }
}
