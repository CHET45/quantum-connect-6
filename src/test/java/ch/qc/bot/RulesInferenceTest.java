package ch.qc.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.Experiment;
import ch.cern.quantumconnect.core.FieldFactory;
import ch.cern.quantumconnect.core.QuantumField;
import ch.qc.bot.rules.Rules;
import ch.qc.starter.RandomBot;
import ch.qc.starter.SpecialMoveBot;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** The bot corrects wrong rules from what the core-2.0 engine (six, 3x3, cooldown 10) does, and only safely. */
class RulesInferenceTest {

  @Test
  void longestLineInAllDirections() {
    int rows = 6;
    int cols = 7;
    byte[] cells = new byte[rows * cols];
    assertEquals(0, RulesInference.longestLine(cells, rows, cols));
    for (int c = 1; c <= 4; c++) {
      cells[c * rows] = 1; // bottom row, columns 1-4
    }
    assertEquals(4, RulesInference.longestLine(cells, rows, cols));
    for (int i = 0; i < 5; i++) {
      cells[(1 + i) * rows + 1 + i] = 2; // diagonal up-right from (1, 1)
    }
    assertEquals(5, RulesInference.longestLine(cells, rows, cols));
    for (int y = 0; y < 6; y++) {
      cells[6 * rows + y] = 2; // full last column
    }
    assertEquals(6, RulesInference.longestLine(cells, rows, cols));
  }

  @Test
  void adjustOnlyMovesTheSafeWayAndKeepsTheInstance() {
    Rules base = Rules.DEFAULT;
    assertSame(base, RulesInference.adjust(base, 0, 0, -1));
    assertSame(base, RulesInference.adjust(base, 6, 10, 10));
    assertEquals(7, RulesInference.adjust(base, 7, 0, -1).k);
    assertEquals(12, RulesInference.adjust(Rules.of(6, 1, 10), 0, 12, -1).cooldown);
    assertEquals(15, RulesInference.adjust(Rules.of(6, 1, 15), 0, 12, -1).cooldown);
    assertEquals(8, RulesInference.adjust(Rules.of(6, 1, 15), 0, 0, 8).cooldown);
    // conflicting proofs: keep the higher (safer) cooldown
    assertEquals(9, RulesInference.adjust(Rules.of(6, 1, 15), 0, 9, 7).cooldown);
  }

  /** Told k = 4, the bot sees its own four in a row while the engine (six) plays on, and learns k = 5. */
  @Test
  void aLongLineThatDoesNotEndTheGameRaisesK() {
    QcBot bot = new QcBot("s=10,p=0");
    bot.forceRules(Rules.of(4, 1, 10));
    QuantumField field = FieldFactory.create(8, 12, bot, new RandomBot());
    int[] own = {4, 5, 6, 7};
    int[] theirs = {0, 11, 0, 11};
    for (int i = 0; i < 4; i++) {
      bot.observeScriptedTurn(field);
      FieldFactory.play(field, own[i], false);
      FieldFactory.play(field, theirs[i], false);
    }
    bot.accelerateQuark(field);
    assertEquals(5, bot.rules().k);
    bot.useSpecialMove(field);
  }

  /** With the right rules nothing is learnt: the rules object stays the engine's. */
  @Test
  void rightRulesStayUntouched() throws Exception {
    QcBot bot = new QcBot("s=5,p=0");
    ch.qc.tools.BotProbe probe = new ch.qc.tools.BotProbe(bot);
    new Experiment(probe, new SpecialMoveBot(), false, false, 8, 12).runExperiment();
    assertSame(Rules.DEFAULT, bot.rules());
    assertEquals(0, probe.invalidSpecials());
  }

  /** Asks for every special its tracker allows, with a cooldown it was told wrongly, and corrects it like QcBot. */
  static final class EagerBot implements Algorithm {
    final TurnTracker tracker = new TurnTracker();
    final SplittableRandom rnd = new SplittableRandom(3);
    final int told;
    int refusedRequests;
    private boolean want;

    EagerBot(int told) {
      this.told = told;
    }

    @Override
    public int accelerateQuark(QuantumField field) {
      tracker.beginTurn(Snapshot.read(field));
      tracker.setCooldown(RulesInference.adjust(Rules.of(6, 1, told), 0, tracker.cooldownAtLeast(),
          tracker.cooldownAtMost()).cooldown);
      want = tracker.ownSpecialReady();
      int cols = field.getNumberOfColumns();
      int c = rnd.nextInt(cols);
      for (int i = 0; i < cols && field.isColumnFull(c); i++) {
        c = (c + 1) % cols;
      }
      return c;
    }

    @Override
    public boolean useSpecialMove(QuantumField field) {
      if (want) {
        tracker.noteSpecialRequested();
      }
      return want;
    }
  }

  /** Told 4, refused at 4..9, honoured at 10: it learns exactly 10, never more. */
  @Test
  void refusedSpecialsRaiseTheCooldownToTheEngines() throws Exception {
    EagerBot bot = new EagerBot(4);
    new Experiment(bot, new RandomBot(), false, false, 15, 30).runExperiment();
    assertTrue(bot.tracker.turn() >= 11, "game too short: " + bot.tracker.turn());
    assertEquals(10, bot.tracker.cooldownAtLeast());
  }

  /** Told 20, it sees the opponent blast at 10 and learns at most 10, never less. */
  @Test
  void opponentSpecialsLowerTheCooldownToTheEngines() throws Exception {
    EagerBot bot = new EagerBot(20);
    new Experiment(new SpecialMoveBot(), bot, false, false, 15, 30).runExperiment();
    assertTrue(bot.tracker.cooldownAtMost() >= 10, "learnt " + bot.tracker.cooldownAtMost());
    assertTrue(bot.tracker.cooldownAtMost() > 0, "the opponent never blasted");
    assertTrue(bot.tracker.cooldown() >= 10, "plays by " + bot.tracker.cooldown());
  }
}
