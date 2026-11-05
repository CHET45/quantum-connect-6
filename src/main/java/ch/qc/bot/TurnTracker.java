package ch.qc.bot;

import ch.cern.quantumconnect.core.Move;
import ch.qc.bot.rules.Board;
import java.util.List;

/**
 * Counts own turns and derives both special-move cooldown counters (design 2.7, rules R11-R12, R21-R23).
 * The engine calls accelerateQuark exactly once per own turn, so the own call count is the own turn index.
 */
public final class TurnTracker {
  /** The cooldown of the default rules. */
  public static final int COOLDOWN = ch.qc.bot.rules.Rules.COOLDOWN_DEFAULT;

  /** A special is honoured iff the counter is at least this (the match rules, {@link #setCooldown}). */
  private int cooldown = COOLDOWN;
  private int turn;
  private int myColour;
  private int lastMySpecial = -1;
  private int lastOppSpecial = -1;
  private int historySeen;
  private Move lastSeen;
  private boolean requestedLast;
  private boolean emptyAtFirstCall;
  private int ownPlaced;
  private int forfeits;
  private boolean lostPrevious;
  private boolean opponentMoved;
  /** What this game proved about the cooldown ({@link RulesInference}): at least / at most this; -1 at most = unknown. */
  private int cooldownAtLeast;
  private int cooldownAtMost = -1;
  /** The history continued the one seen at the previous own turn (nobody cleared it). */
  private boolean continuousHistory;

  /** Starts own turn k + 1 from the snapshot read at the start of accelerateQuark. */
  public void beginTurn(Snapshot s) {
    List<Move> history = s.history;
    if (turn > 0 && s.discs <= 1 && history.size() <= 1 && history.size() <= historySeen) {
      reset();
    }
    turn++;
    if (turn == 1) {
      emptyAtFirstCall = s.discs == 0;
    }
    if (myColour == 0 && s.myColour != 0) {
      myColour = s.myColour;
    }
    // getMoveHistory copies the list but not the Move records, so identity shows whether the history continues
    boolean continuous = historySeen == 0
        || (history.size() >= historySeen && history.get(historySeen - 1) == lastSeen);
    continuousHistory = continuous && historySeen > 0;
    if (!continuous) {
      // someone cleared the history: keep the local belief that a requested special went through
      if (requestedLast) {
        lastMySpecial = turn - 1;
      }
      ownPlaced = turn - 1 - forfeits;
      opponentMoved = false;
    } else if (myColour != 0) {
      opponentMoved = false;
      for (int i = historySeen; i < history.size(); i++) {
        Move m = history.get(i);
        boolean mine = Snapshot.encode(m.player()) == myColour;
        if (mine) {
          ownPlaced++;
        } else {
          opponentMoved = true;
        }
        if (!m.isSpecialMove()) {
          if (mine && requestedLast) {
            // the engine refused the special we asked for: the cooldown is above our counter of that turn
            int before = lastMySpecial < 0 ? turn - 2 : turn - 1 - lastMySpecial;
            cooldownAtLeast = Math.max(cooldownAtLeast, before + 1);
          }
          continue;
        }
        if (mine) {
          lastMySpecial = turn - 1;
        } else {
          int done = opponentTurnsDone();
          int before = lastOppSpecial < 0 ? done - 1 : done - lastOppSpecial;
          cooldownAtMost = cooldownAtMost < 0 ? before : Math.min(cooldownAtMost, before);
          lastOppSpecial = done;
        }
      }
    }
    int lost = Math.max(0, turn - 1 - ownPlaced);
    lostPrevious = lost > forfeits;
    forfeits = lost;
    historySeen = history.size();
    lastSeen = historySeen > 0 ? history.get(historySeen - 1) : null;
    requestedLast = false;
  }

  /** The cooldown of the match; the counters themselves do not depend on it. */
  public void setCooldown(int cooldown) {
    this.cooldown = cooldown;
  }

  public int cooldown() {
    return cooldown;
  }

  /** True if the history this turn continued the one of the previous own turn, so the moves between are known. */
  public boolean continuousHistory() {
    return continuousHistory;
  }

  /** The cooldown is above every counter at which the engine refused our special in this game; 0 if none was. */
  public int cooldownAtLeast() {
    return cooldownAtLeast;
  }

  /** The cooldown is at most the opponent's counter at its specials in this game; -1 before the first. */
  public int cooldownAtMost() {
    return cooldownAtMost;
  }

  public void noteSpecialRequested() {
    requestedLast = true;
  }

  private void reset() {
    turn = 0;
    myColour = 0;
    lastMySpecial = -1;
    lastOppSpecial = -1;
    historySeen = 0;
    lastSeen = null;
    requestedLast = false;
    emptyAtFirstCall = false;
    ownPlaced = 0;
    forfeits = 0;
    lostPrevious = false;
    cooldownAtLeast = 0;
    cooldownAtMost = -1;
  }

  /** Own turns of this game the engine forfeited so far: the ones with no move of ours in the history. */
  public int ownForfeits() {
    return forfeits;
  }

  /** True if the opponent got a move on the board since the previous own turn, so it did not lose that turn. */
  public boolean opponentMoved() {
    return opponentMoved;
  }

  /** True if the history shows a turn of ours lost since the previous own turn. */
  public boolean lostPreviousTurn() {
    return lostPrevious;
  }

  /** Own turn index of the current turn, 1-based. */
  public int turn() {
    return turn;
  }

  public int myColour() {
    return myColour;
  }

  /** Unknown until the first non-empty board; an empty board at the first call means we move first. */
  public boolean iAmFirst() {
    return myColour == 0 ? emptyAtFirstCall : myColour == Board.FIRST;
  }

  public int opponentTurnsDone() {
    return iAmFirst() ? turn - 1 : turn;
  }

  /** Engine counter before the current own turn; a special is honoured iff it is at least the cooldown. */
  public int myCooldownCounter() {
    return lastMySpecial < 0 ? turn - 1 : turn - lastMySpecial;
  }

  /** Engine counter before the opponent's next turn; deliberately early if a wasted special was invisible. */
  public int opponentCooldownCounter() {
    int done = opponentTurnsDone();
    return lastOppSpecial < 0 ? done : done + 1 - lastOppSpecial;
  }

  public boolean ownSpecialReady() {
    return myCooldownCounter() >= cooldown;
  }

  /** Engine iteration index (both players, forfeits included) of the current own turn, 0-based. */
  public int plyIndex() {
    return 2 * (turn - 1) + (iAmFirst() ? 0 : 1);
  }
}
