package ch.qc.tools;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.QuantumField;
import java.util.SplittableRandom;

/**
 * Plays scripted columns for the first own turns, then hands over to the wrapped bot (design 5.2). With a drop chance,
 * each own turn after the script is lost with that chance, as on a slow server: the bot is asked and searches, but the
 * engine gets an invalid column and no second call reaches the bot.
 */
public final class ScriptedOpening implements Algorithm {
  private final BotProbe bot;
  private final int[] columns;
  private final double drop;
  private final SplittableRandom rnd;
  private int turn;
  private boolean inScript;
  private boolean dropped;

  public ScriptedOpening(BotProbe bot, int[] columns) {
    this(bot, columns, 0, 0);
  }

  public ScriptedOpening(BotProbe bot, int[] columns, double drop, long seed) {
    this.bot = bot;
    this.columns = columns;
    this.drop = drop;
    this.rnd = new SplittableRandom(seed);
  }

  @Override
  public int accelerateQuark(QuantumField field) {
    int t = turn++;
    inScript = t < columns.length;
    dropped = false;
    if (inScript) {
      bot.observeScriptedTurn(field);
      return columns[t];
    }
    int col = bot.accelerateQuark(field);
    if (drop > 0 && rnd.nextDouble() < drop) {
      dropped = true;
      return -1;
    }
    return col;
  }

  @Override
  public boolean useSpecialMove(QuantumField field) {
    return !inScript && !dropped && bot.useSpecialMove(field);
  }
}
