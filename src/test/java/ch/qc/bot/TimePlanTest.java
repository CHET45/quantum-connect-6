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
    QcBot bot = new QcBot("s=20,p=15,pf=0");
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
  void secondCallSearchesAfterAFirstCallThatUsedItsWholeStop() {
    QcBot bot = new QcBot("s=80,b=70@75,p=5,pf=0");
    QuantumField f = FieldFactory.create(15, 11, bot, new RandomBot());
    FieldFactory.play(f, bot.accelerateQuark(f), bot.useSpecialMove(f));
    FieldFactory.play(f, 5, false);
    long t0 = System.nanoTime();
    bot.accelerateQuark(f);
    long took = (System.nanoTime() - t0) / 1_000_000L;
    assertFalse(bot.lastDecision().finishedInTime(), "first call took " + took + " ms");
    assertFalse(bot.useSpecialMove(f));
    assertNotNull(bot.lastPonder());
  }

  @Test
  void noSecondCallSearchWhenSwitchedOff() {
    QcBot bot = new QcBot("s=20,p=0");
    QuantumField f = FieldFactory.create(15, 11, bot, new RandomBot());
    FieldFactory.play(f, bot.accelerateQuark(f), bot.useSpecialMove(f));
    FieldFactory.play(f, 5, false);
    bot.accelerateQuark(f);
    assertFalse(bot.useSpecialMove(f));
    assertNull(bot.lastPonder());
  }

  /** Any size: the first call plays without search, the second builds, then search runs. */
  @Test
  void everySizeBuildsInTheSecondCall() {
    for (int[] size : new int[][] {{1, 1}, {1, 12}, {12, 1}, {3, 3}, {5, 5}, {6, 6}, {7, 9}, {10, 10}, {12, 16},
        {8, 12}, {15, 11}, {15, 22}, {20, 20}, {25, 25}, {40, 40}}) {
      QcBot bot = new QcBot("s=20,p=10");
      Algorithm other = new RandomBot();
      QuantumField f = FieldFactory.create(size[0], size[1], bot, other);
      long t0 = System.nanoTime();
      int col = bot.accelerateQuark(f);
      long took = (System.nanoTime() - t0) / 1_000_000L;
      String ctx = size[0] + "x" + size[1];
      assertTrue(col >= 0 && col < size[1] && !f.isColumnFull(col), ctx + " column " + col);
      assertNull(bot.lastResult(), ctx + ": the first call must not search");
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
    QcBot bot = new QcBot("s=20");
    QuantumField f = FieldFactory.create(15, 11, bot, new RandomBot());
    assertEquals(5, bot.accelerateQuark(f));
    assertNull(bot.lastResult());
    assertFalse(bot.useSpecialMove(f));
  }

  @Test
  void warmUpFinishesInItsTimeAndLeavesTheBotWorking() {
    long t0 = System.nanoTime();
    QcBot bot = new QcBot("s=20,w=200");
    long took = (System.nanoTime() - t0) / 1_000_000L;
    assertTrue(took < 5_000, "constructor took " + took + " ms");
    QuantumField f = FieldFactory.create(8, 12, bot, new RandomBot());
    int col = bot.accelerateQuark(f);
    assertTrue(col >= 0 && col < 12);
  }

  @Test
  void aBlastTooLateFallsBackToTheBestDrop() {
    ch.qc.bot.rules.Geometry g = new ch.qc.bot.rules.Geometry(15, 11);
    ch.qc.bot.search.Searcher s = new ch.qc.bot.search.Searcher(g, new ch.qc.bot.eval.HandEval(g),
        new ch.qc.bot.search.TranspositionTable(16));
    ch.qc.bot.rules.Board b = new ch.qc.bot.rules.Board(g);
    for (int i = 0; i < 20; i++) {
      assertFalse(b.make(i % 11, 1 + i % 2));
    }
    s.run(b, 1, 10, 10, 20, Clock.nanos(), 20 * MS);
    int drop = s.lastBestDrop();
    assertTrue(drop != ch.qc.bot.search.Tactics.NONE && !ch.qc.bot.search.Tactics.isSpecial(drop), "drop " + drop);
    assertFalse(b.isFull(ch.qc.bot.search.Tactics.column(drop)));
  }

  @Test
  void secondCallThatStartsLateNeverAsksForABlast() {
    long limit = CallLimit.PRIOR_NANOS;
    assertTrue(QcBot.secondCallInTime(1_000 * MS, 1_089 * MS, limit));
    assertFalse(QcBot.secondCallInTime(1_000 * MS, 1_090 * MS, limit));
    assertFalse(QcBot.secondCallInTime(1_000 * MS, 1_100 * MS, limit));
    assertTrue(Timing.DEFAULT.safeFinishNanos < QcBot.SECOND_CALL_LATE_NANOS);
    assertTrue(Timing.DEFAULT.blastStopNanos < Timing.DEFAULT.safeFinishNanos);
  }

  /**
   * Random positions with the blast ready, driven by hand so the second call can start late: whenever the bot has
   * chosen a blast, a prompt second call asks for it and one after a pause as long as an engine timeout does not.
   */
  @Test
  void aChosenBlastIsDroppedIfTheFirstCallMayHaveTimedOut() throws Exception {
    java.util.SplittableRandom rnd = new java.util.SplittableRandom(5);
    int prompt = 0;
    int late = 0;
    for (int game = 0; game < 400 && (prompt < 3 || late < 3); game++) {
      QcBot bot = new QcBot("s=4,p=0");
      QuantumField f = FieldFactory.create(8, 12, bot, new RandomBot());
      for (int ply = 0; ply < 70 && FieldFactory.status(f) == ExperimentStatus.INCOMPLETE; ply++) {
        int col = rnd.nextInt(12);
        for (int i = 0; i < 12 && f.isColumnFull(col); i++) {
          col = (col + 1) % 12;
        }
        if (ply % 2 == 0) {
          int chosen = bot.accelerateQuark(f);
          Decision d = bot.lastDecision();
          if (d.special() && bot.tracker().ownSpecialReady()) {
            if (late <= prompt) {
              Thread.sleep(95);
              assertFalse(bot.useSpecialMove(f), "a late second call must not ask for the blast");
              late++;
            } else {
              assertTrue(bot.useSpecialMove(f), "a prompt second call asks for the chosen blast");
              prompt++;
            }
            break;
          }
          assertFalse(bot.useSpecialMove(f));
          assertTrue(chosen >= 0 && chosen < 12);
        }
        FieldFactory.play(f, col, false);
      }
    }
    assertTrue(prompt >= 3 && late >= 3, "blasts seen: prompt " + prompt + ", late " + late);
  }

  @Test
  void budgetsAtTheLimitOf100MsAreTheirOwnValues() {
    for (long ms : new long[] {25, 70, 75, 80, 85, 90, 96}) {
      assertEquals(ms * MS, CallLimit.scaled(ms * MS, 25, CallLimit.PRIOR_NANOS));
    }
  }

  @Test
  void budgetsKeepAnAbsoluteDistanceFromTheLimit() {
    // 80 ms at 100 is 20 ms from the limit
    assertEquals(30 * MS, CallLimit.scaled(80 * MS, 25, 50 * MS));
    assertEquals(180 * MS, CallLimit.scaled(80 * MS, 25, 200 * MS));
    // short limits: the floor; long limits: the distance grows slowly
    assertEquals(25 * MS / 4, CallLimit.scaled(80 * MS, 25, 25 * MS));
    assertEquals(475 * MS, CallLimit.scaled(80 * MS, 25, 500 * MS));
    assertEquals(950 * MS, CallLimit.scaled(80 * MS, 25, 1000 * MS));
  }

  @Test
  void aMeasurementIsRoundedDown() {
    assertEquals(100 * MS, CallLimit.round(100 * MS));
    assertEquals(100 * MS, CallLimit.round(125 * MS));
    assertEquals(25 * MS, CallLimit.round(26 * MS));
    assertEquals(50 * MS, CallLimit.round(53 * MS));
    assertEquals(500 * MS, CallLimit.round(530 * MS));
    assertEquals(200 * MS, CallLimit.round(199 * MS));
    assertEquals(140 * MS / 10 * 9, CallLimit.round(140 * MS));
  }

  @Test
  void aLimitBelowThePriorIsTheMedianOfThreeMeasurements() {
    CallLimit l = new CallLimit();
    l.measured(52 * MS);
    assertFalse(l.known());
    assertEquals(50 * MS, l.nanos());
    l.measured(90 * MS);
    assertTrue(l.known(), "a measurement near the prior settles it");
    assertEquals(CallLimit.PRIOR_NANOS, l.nanos());
    CallLimit m = new CallLimit();
    m.measured(30 * MS);
    m.measured(26 * MS);
    assertFalse(m.known());
    m.measured(70 * MS);
    assertTrue(m.known());
    assertEquals(30 * MS, m.nanos());
  }

  /** The engine's timer, a late second call: the field changes while the bot is in its second call. */
  @Test
  void theProbeReadsTheLimitFromTheField() throws Exception {
    QcBot bot = new QcBot("s=20,p=0");
    QuantumField f = FieldFactory.create(15, 11, bot, new RandomBot());
    int col = bot.accelerateQuark(f);
    Thread engine = new Thread(() -> {
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        return;
      }
      FieldFactory.play(f, col, false);
    });
    engine.start();
    assertFalse(bot.useSpecialMove(f));
    engine.join();
    // the sleep above can be late on a loaded machine, so only the order of magnitude is sure
    assertTrue(bot.callLimitNanos() >= 40 * MS && bot.callLimitNanos() <= 75 * MS, "limit " + bot.callLimitNanos());
  }

  @Test
  void noProbeResultMeansThePrior() {
    QcBot bot = new QcBot("s=20,p=0");
    QuantumField f = FieldFactory.create(15, 11, bot, new RandomBot());
    bot.accelerateQuark(f);
    long t0 = System.nanoTime();
    assertFalse(bot.useSpecialMove(f));
    long took = (System.nanoTime() - t0) / 1_000_000L;
    assertEquals(CallLimit.PRIOR_NANOS, bot.callLimitNanos());
    assertTrue(took >= 80 && took < 100, "the probe took " + took + " ms");
  }

  /** The real engine, its real limit, a bot that looks for the limit from the first turn on: nothing else changes. */
  @Test
  void theUpwardProbeInTheRealEngine() throws Exception {
    QcBot bot = new QcBot("up=1,s=10,p=0");
    ch.qc.tools.BotProbe probe = new ch.qc.tools.BotProbe(bot);
    ch.cern.quantumconnect.core.Experiment exp =
        new ch.cern.quantumconnect.core.Experiment(probe, new RandomBot(), false, false, 8, 12);
    exp.runExperiment();
    assertEquals(CallLimit.PRIOR_NANOS, bot.callLimitNanos());
    assertEquals(0, probe.invalidColumns());
    assertEquals(0, probe.exceptions());
    assertEquals(0, probe.invalidSpecials());
    assertTrue(exp.getExperimentStatus() != ExperimentStatus.INCOMPLETE);
  }

  private static int discs(QuantumField f) {
    return Snapshot.read(f).discs;
  }
}
