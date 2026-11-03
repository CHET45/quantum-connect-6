package ch.qc.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.cern.quantumconnect.bots.CheatingBot;
import ch.cern.quantumconnect.bots.LinearBot;
import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.Experiment;
import ch.cern.quantumconnect.core.QuantumField;
import ch.qc.starter.SpecialMoveBot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * TurnTracker against the engine's private counters (read by reflection while the bot is being called): own and
 * opponent cooldown counters, ply index and colour, over opponents that use specials, forfeit, clear the history,
 * and with one tracker instance reused across games and colours (design 2.7, suite T6).
 */
class TurnTrackerTest {
  private final List<String> errors = Collections.synchronizedList(new ArrayList<>());
  private final AtomicInteger checks = new AtomicInteger();

  /** Requests specials at random when its tracker says they are ready; checks the tracker on every call. */
  final class TrackedBot implements Algorithm {
    final TurnTracker tracker = new TurnTracker();
    final SplittableRandom rnd;
    final double pSpecial;
    volatile Experiment exp;
    volatile boolean first;
    volatile boolean exactOpponent = true;
    private boolean wantSpecial;

    TrackedBot(long seed, double pSpecial) {
      this.rnd = new SplittableRandom(seed);
      this.pSpecial = pSpecial;
    }

    @Override
    public int accelerateQuark(QuantumField field) {
      Snapshot s = Snapshot.read(field);
      tracker.beginTurn(s);
      String ctx = (first ? "first" : "second") + " own turn " + tracker.turn();
      expect(counter(exp, first), tracker.myCooldownCounter(), ctx + ": own counter");
      if (tracker.myColour() != 0) {
        expect(first ? 1 : 2, tracker.myColour(), ctx + ": colour");
        expect(iterations(exp), tracker.plyIndex(), ctx + ": ply index");
        int opp = counter(exp, !first);
        if (exactOpponent) {
          expect(opp, tracker.opponentCooldownCounter(), ctx + ": opponent counter");
        } else if (tracker.opponentCooldownCounter() < opp) {
          errors.add(ctx + ": opponent counter " + tracker.opponentCooldownCounter() + " below engine " + opp);
        }
      }
      checks.incrementAndGet();
      wantSpecial = tracker.ownSpecialReady() && rnd.nextDouble() < pSpecial;
      int cols = field.getNumberOfColumns();
      int c = rnd.nextInt(cols);
      for (int i = 0; i < cols && field.isColumnFull(c); i++) {
        c = (c + 1) % cols;
      }
      return c;
    }

    @Override
    public boolean useSpecialMove(QuantumField field) {
      if (wantSpecial) {
        tracker.noteSpecialRequested();
      }
      return wantSpecial;
    }
  }

  /** Clears the live move history at the start of every own call, then plays as the wrapped bot. */
  static final class HistoryClearer implements Algorithm {
    private final Algorithm inner;

    HistoryClearer(Algorithm inner) {
      this.inner = inner;
    }

    @Override
    public int accelerateQuark(QuantumField field) {
      field.clearMoveHistory();
      return inner.accelerateQuark(field);
    }

    @Override
    public boolean useSpecialMove(QuantumField field) {
      return inner.useSpecialMove(field);
    }
  }

  private void expect(int engine, int tracked, String what) {
    if (engine != tracked) {
      errors.add(what + ": engine " + engine + ", tracker " + tracked);
    }
  }

  private static int counter(Experiment exp, boolean first) {
    return intField(exp, first ? "movesSinceLastSpecialFirstPlayer" : "movesSinceLastSpecialSecondPlayer");
  }

  private static int iterations(Experiment exp) {
    try {
      java.lang.reflect.Field f = Experiment.class.getDeclaredField("iterations");
      f.setAccessible(true);
      return ((AtomicInteger) f.get(exp)).get();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static int intField(Experiment exp, String name) {
    try {
      java.lang.reflect.Field f = Experiment.class.getDeclaredField(name);
      f.setAccessible(true);
      return f.getInt(exp);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private void play(TrackedBot me, boolean meFirst, Algorithm opponent, int rows, int cols) throws Exception {
    me.first = meFirst;
    Experiment exp = meFirst
        ? new Experiment(me, opponent, false, false, rows, cols)
        : new Experiment(opponent, me, false, false, rows, cols);
    me.exp = exp;
    if (opponent instanceof TrackedBot t) {
      t.first = !meFirst;
      t.exp = exp;
    }
    exp.runExperiment();
  }

  @Test
  void trackerMatchesEngineCounters() throws Exception {
    int[][] sizes = {{8, 12}, {6, 7}, {12, 8}, {8, 12}, {25, 25}, {3, 3}};
    for (int g = 0; g < 60; g++) {
      int[] size = sizes[g % sizes.length];
      play(new TrackedBot(g, 0.5), g % 2 == 0, new TrackedBot(1000 + g, 0.3), size[0], size[1]);
      play(new TrackedBot(g, 0.7), g % 2 == 1, new SpecialMoveBot(), size[0], size[1]);
      play(new TrackedBot(g, 0.7), g % 2 == 0, new LinearBot(), size[0], size[1]);
    }
    assertNoErrors();
  }

  @Test
  void trackerSurvivesForfeitingOpponents() throws Exception {
    play(new TrackedBot(1, 1.0), true, new CheatingBot(), 8, 12);
    play(new TrackedBot(2, 1.0), false, new CheatingBot(), 8, 12);
    assertNoErrors();
  }

  @Test
  void trackerSurvivesClearedHistory() throws Exception {
    for (int g = 0; g < 20; g++) {
      TrackedBot me = new TrackedBot(g, 0.8);
      me.exactOpponent = false;
      play(me, g % 2 == 0, new HistoryClearer(new SpecialMoveBot(0.5)), 8, 12);
    }
    assertNoErrors();
  }

  @Test
  void oneInstanceAcrossGamesAndColours() throws Exception {
    TrackedBot me = new TrackedBot(7, 0.6);
    for (int g = 0; g < 12; g++) {
      play(me, g % 3 != 0, new SpecialMoveBot(), 8, 12);
    }
    assertNoErrors();
  }

  private void assertNoErrors() {
    assertTrue(checks.get() > 0, "no checks ran");
    assertEquals(List.of(), errors.subList(0, Math.min(10, errors.size())), errors.size() + " mismatches");
  }
}
