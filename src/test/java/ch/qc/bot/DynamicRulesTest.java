package ch.qc.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.cern.quantumconnect.core.FieldFactory;
import ch.cern.quantumconnect.core.QuantumField;
import ch.qc.bot.rules.Rules;
import ch.qc.bot.search.Searcher;
import ch.qc.starter.RandomBot;
import org.junit.jupiter.api.Test;

/**
 * The rules object reaches the bot's model: under forced rules of four in a row the bot sees the win in one that does
 * not exist under the engine's six, and a change of rules between games rebuilds the model.
 */
class DynamicRulesTest {

  /** Own discs on the bottom row in columns 4, 5, 6, the opponent's in columns 0, 11, 0; the bot to move. */
  private static QuantumField threeInARow(QcBot bot) {
    QuantumField field = FieldFactory.create(8, 12, bot, new RandomBot());
    int[] own = {4, 5, 6};
    int[] theirs = {0, 11, 0};
    for (int i = 0; i < 3; i++) {
      bot.observeScriptedTurn(field);
      FieldFactory.play(field, own[i], false);
      FieldFactory.play(field, theirs[i], false);
    }
    return field;
  }

  @Test
  void fourInARowIsAWinUnderRulesOfFour() {
    QcBot bot = new QcBot();
    bot.forceRules(Rules.of(4, 1, 10));
    QuantumField field = threeInARow(bot);
    int col = bot.accelerateQuark(field);
    assertEquals(4, bot.rules().k);
    assertTrue(col == 3 || col == 7, "a drop in column 3 or 7 makes four, the bot played " + col);
    assertEquals(Searcher.MATE - 1, bot.lastResult().score(), "the search must see the win in one");
  }

  @Test
  void theSamePositionIsNoWinUnderSixAndRulesChangeBetweenGames() {
    QcBot bot = new QcBot();
    bot.forceRules(Rules.of(4, 1, 10));
    threeInARow(bot);
    bot.forceRules(Rules.DEFAULT);
    QuantumField field = threeInARow(bot);
    bot.accelerateQuark(field);
    assertEquals(Rules.DEFAULT, bot.rules());
    assertNotEquals(Searcher.MATE - 1, bot.lastResult().score(), "three in a row is no win in one under six");
  }

  @Test
  void cooldownOfTheRulesDecidesWhenTheBlastIsReady() {
    TurnTracker t = new TurnTracker();
    t.setCooldown(3);
    assertEquals(3, t.cooldown());
    assertFalse(t.ownSpecialReady());
    assertEquals(Rules.of(6, 1, 3).cooldown, t.cooldown());
  }
}
