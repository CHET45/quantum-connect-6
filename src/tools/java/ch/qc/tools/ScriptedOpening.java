package ch.qc.tools;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.QuantumField;

/** Plays scripted columns for the first own turns, then hands over to the wrapped bot (design 5.2). */
public final class ScriptedOpening implements Algorithm {
  private final BotProbe bot;
  private final int[] columns;
  private int turn;
  private boolean inScript;

  public ScriptedOpening(BotProbe bot, int[] columns) {
    this.bot = bot;
    this.columns = columns;
  }

  @Override
  public int accelerateQuark(QuantumField field) {
    int t = turn++;
    inScript = t < columns.length;
    if (inScript) {
      bot.observeScriptedTurn(field);
      return columns[t];
    }
    return bot.accelerateQuark(field);
  }

  @Override
  public boolean useSpecialMove(QuantumField field) {
    return !inScript && bot.useSpecialMove(field);
  }
}
