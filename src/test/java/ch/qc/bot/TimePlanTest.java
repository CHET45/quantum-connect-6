package ch.qc.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.ExperimentStatus;
import ch.cern.quantumconnect.core.FieldFactory;
import ch.cern.quantumconnect.core.QuantumField;
import ch.qc.starter.RandomBot;
import org.junit.jupiter.api.Test;

/** The time plan: the stop schedule, lost-turn tracking, the second-call search, boards of any size. */
class TimePlanTest {
  private static final long MS = 1_000_000L;

  @Test
  void scheduleByOwnTurn() {
    Timing t = Timing.parse("s=55@10/65@30/88");
    assertEquals(55 * MS, t.stop(0));
    assertEquals(55 * MS, t.stop(9));
    assertEquals(65 * MS, t.stop(10));
    assertEquals(65 * MS, t.stop(29));
    assertEquals(88 * MS, t.stop(30));
    assertEquals(88 * MS, t.stop(500));
  }

  @Test
  void clockStopsTheSecondCallFirst() {
    assertTrue(MatchClock.ponderAllowedFor(1_000 * MS, 10, 0, 200));
    // 100 plies at 130 ms: 14 s left before the reserve, about 107 plies
    assertTrue(MatchClock.ponderAllowedFor(13_000 * MS, 100, 0, 50));
    assertFalse(MatchClock.ponderAllowedFor(13_000 * MS, 100, 0, 200));
    // a worse position takes the draw on time
    assertTrue(MatchClock.ponderAllowedFor(13_000 * MS, 100, MatchClock.WORSE, 200));
    assertFalse(MatchClock.ponderAllowedFor(28_000 * MS, 200, 0, 10));
  }

  @Test
  void ponderSpareWhenFirstCallIsFull() {
    // whenever the second call may search, the first call keeps its full stop
    for (long elapsed = 1_000; elapsed < 29_000; elapsed += 500) {
      for (int plies = 20; plies < 300; plies += 10) {
        for (int empty = 0; empty < 330; empty += 30) {
          if (MatchClock.ponderAllowedFor(elapsed * MS, plies, 0, empty)) {
            assertEquals(80 * MS, MatchClock.budgetFor(elapsed * MS, plies, 80 * MS, 0, empty));
          }
        }
      }
    }
  }

  /** Forfeits by returning an invalid column on its second turn; reports the bot's view through a tracker. */
  @Test
  void trackerCountsOwnLostTurns() {
    TurnTracker t = new TurnTracker();
    Algorithm a = new RandomBot();
    Algorithm b = new RandomBot();
    QuantumField f = FieldFactory.create(8, 12, a, b);
    t.beginTurn(Snapshot.read(f));
    assertEquals(0, t.ownForfeits());
    FieldFactory.play(f, 3, false);
    FieldFactory.play(f, 4, false);
    t.beginTurn(Snapshot.read(f));
    assertEquals(0, t.ownForfeits());
    assertFalse(t.lostPreviousTurn());
    FieldFactory.play(f, -1, false);
    FieldFactory.play(f, 4, false);
    t.beginTurn(Snapshot.read(f));
    assertEquals(1, t.ownForfeits());
    assertTrue(t.lostPreviousTurn());
    FieldFactory.play(f, 3, false);
    FieldFactory.play(f, 4, false);
    t.beginTurn(Snapshot.read(f));
    assertEquals(1, t.ownForfeits());
    assertFalse(t.lostPreviousTurn());
  }

  @Test
  void secondCallSearchesThePositionAfterOurMove() {
    QcBot bot = new QcBot("s=20,f=20,p=15");
    Algorithm other = new RandomBot();
    QuantumField f = FieldFactory.create(15, 11, bot, other);
    FieldFactory.play(f, bot.accelerateQuark(f), bot.useSpecialMove(f));
    FieldFactory.play(f, 5, false);
    int col = bot.accelerateQuark(f);
    long cells = 15 * 11 - discs(f);
    assertFalse(bot.useSpecialMove(f));
    assertNotNull(bot.lastPonder());
    assertTrue(bot.lastPonder().nodes() > 0);
    // the field itself is not touched by the search
    assertEquals(cells, 15 * 11 - discs(f));
    assertTrue(col >= 0 && col < 11);
  }

  @Test
  void noSecondCallSearchWhenSwitchedOff() {
    QcBot bot = new QcBot("s=20,f=20,p=0");
    QuantumField f = FieldFactory.create(15, 11, bot, new RandomBot());
    FieldFactory.play(f, bot.accelerateQuark(f), bot.useSpecialMove(f));
    FieldFactory.play(f, 5, false);
    bot.accelerateQuark(f);
    assertFalse(bot.useSpecialMove(f));
    assertNull(bot.lastPonder());
  }

  /** A size without prepared tables: the first call plays without search, the second builds, then search runs. */
  @Test
  void unpreparedSizesBuildInTheSecondCall() {
    for (int[] size : new int[][] {{1, 1}, {1, 12}, {12, 1}, {3, 3}, {5, 5}, {6, 6}, {7, 9}, {10, 10}, {12, 16},
        {20, 20}, {25, 25}, {40, 40}}) {
      QcBot bot = new QcBot("s=20,f=20,p=10");
      Algorithm other = new RandomBot();
      QuantumField f = FieldFactory.create(size[0], size[1], bot, other);
      long t0 = System.nanoTime();
      int col = bot.accelerateQuark(f);
      long took = (System.nanoTime() - t0) / 1_000_000L;
      String ctx = size[0] + "x" + size[1];
      assertTrue(col >= 0 && col < size[1] && !f.isColumnFull(col), ctx + " column " + col);
      assertNull(bot.lastResult(), ctx + ": the first call must not search on an unprepared size");
      assertTrue(took < 50, ctx + ": first call took " + took + " ms");
      assertFalse(bot.useSpecialMove(f), ctx);
      FieldFactory.play(f, col, false);
      if (FieldFactory.status(f) != ExperimentStatus.INCOMPLETE) {
        continue;
      }
      FieldFactory.play(f, 0, false);
      if (FieldFactory.status(f) != ExperimentStatus.INCOMPLETE) {
        continue;
      }
      col = bot.accelerateQuark(f);
      assertTrue(col >= 0 && col < size[1] && !f.isColumnFull(col), ctx + " second column " + col);
      assertNotNull(bot.lastResult(), ctx + ": the tables should be ready on the second turn");
      assertFalse(bot.useSpecialMove(f), ctx);
    }
  }

  @Test
  void centreOnTheEmptyBoardWithoutSearch() {
    QcBot bot = new QcBot("s=20,f=20,c=1");
    QuantumField f = FieldFactory.create(15, 11, bot, new RandomBot());
    assertEquals(5, bot.accelerateQuark(f));
    assertNull(bot.lastResult());
    assertFalse(bot.useSpecialMove(f));
  }

  @Test
  void warmUpFinishesInItsTimeAndLeavesTheBotWorking() {
    long t0 = System.nanoTime();
    QcBot bot = new QcBot("s=20,f=20,w=200");
    long took = (System.nanoTime() - t0) / 1_000_000L;
    assertTrue(took < 5_000, "constructor took " + took + " ms");
    QuantumField f = FieldFactory.create(8, 12, bot, new RandomBot());
    int col = bot.accelerateQuark(f);
    assertTrue(col >= 0 && col < 12);
  }

  private static int discs(QuantumField f) {
    return Snapshot.read(f).discs;
  }
}
