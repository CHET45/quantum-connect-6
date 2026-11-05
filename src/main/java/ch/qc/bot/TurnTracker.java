package ch.qc.bot;

import ch.cern.quantumconnect.core.Move;
import ch.qc.bot.rules.Board;
import java.util.List;

/**
 * Counts own turns and derives both special-move cooldown counters (design 2.7, rules R11-R12, R21-R23).
 * The engine calls accelerateQuark exactly once per own turn, so the own call count is the own turn index.
 */
public final class TurnTracker {
  public static final int COOLDOWN = 10;

  private int turn;
  private int myColour;
  private int lastMySpecial = -1;
  private int lastOppSpecial = -1;
  private int historySeen;
  private Move lastSeen;
  private boolean requestedLast;
  private boolean emptyAtFirstCall;

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
    if (!continuous) {
      // someone cleared the history: keep the local belief that a requested special went through
      if (requestedLast) {
        lastMySpecial = turn - 1;
      }
    } else if (myColour != 0) {
      for (int i = historySeen; i < history.size(); i++) {
        Move m = history.get(i);
        if (!m.isSpecialMove()) {
          continue;
        }
        if (Snapshot.encode(m.player()) == myColour) {
          lastMySpecial = turn - 1;
        } else {
          lastOppSpecial = opponentTurnsDone();
        }
      }
    }
    historySeen = history.size();
    lastSeen = historySeen > 0 ? history.get(historySeen - 1) : null;
    requestedLast = false;
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

  /** Engine counter before the current own turn; a special is honoured iff it is at least 10. */
  public int myCooldownCounter() {
    return lastMySpecial < 0 ? turn - 1 : turn - lastMySpecial;
  }

  /** Engine counter before the opponent's next turn; deliberately early if a wasted special was invisible. */
  public int opponentCooldownCounter() {
    int done = opponentTurnsDone();
    return lastOppSpecial < 0 ? done : done + 1 - lastOppSpecial;
  }

  public boolean ownSpecialReady() {
    return myCooldownCounter() >= COOLDOWN;
  }

  /** Engine iteration index (both players, forfeits included) of the current own turn, 0-based. */
  public int plyIndex() {
    return 2 * (turn - 1) + (iAmFirst() ? 0 : 1);
  }
}
