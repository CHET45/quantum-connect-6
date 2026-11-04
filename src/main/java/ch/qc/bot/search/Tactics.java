package ch.qc.bot.search;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.rules.Tables;

/**
 * Root tactics of milestone M1. Wins at once with a drop or a blast if it can. Otherwise every normal move is
 * classified by a three-ply check: it loses at once (the opponent then completes a line by a drop or, if its
 * special is ready, by a blast; this covers forced blocks, playing under an opponent threat cell and handing the
 * opponent a winning blast), it lets the opponent force a win (a reply after which none of our moves survives),
 * it is neutral, or it forces our own win (every reply loses to an immediate win of ours). The best class wins;
 * inside a class a window-potential score and then the most central column decide. A blast is played only to
 * win at once or when every normal move loses at once.
 */
public final class Tactics {
  public static final int SPECIAL = 1 << 16;
  public static final int NONE = -1;
  public static final int COOLDOWN = 10;

  private static final int LOSES_NOW = 0;
  private static final int LOSES_IN_TWO = 1;
  private static final int NEUTRAL = 2;
  private static final int WINS_IN_TWO = 3;
  /** The three-ply check costs about cols^3 board updates; wider boards use the one-ply classes only. */
  private static final int DEEP_MAX_COLS = 16;
  private static final int[] WEIGHT = {0, 1, 3, 9, 27, 81, 0, 0, 0, 0};

  private final Geometry g;
  private final Tables t;
  private final Board child;
  private final Board grand;
  private final Board great;
  /** potential[colour][code]: weight of a window that still can become a line of colour. */
  private final int[][] potential;
  private long deadline;

  public Tactics(Geometry g) {
    this.g = g;
    this.t = g.tables;
    this.child = new Board(g);
    this.grand = new Board(g);
    this.great = new Board(g);
    potential = new int[3][t.codes];
    for (int code = 0; code < t.codes; code++) {
      int[] n = new int[3];
      for (int i = 0, c = code; i < t.k; i++, c /= 3) {
        n[c % 3]++;
      }
      potential[1][code] = n[2] == 0 ? WEIGHT[n[1]] : 0;
      potential[2][code] = n[1] == 0 ? WEIGHT[n[2]] : 0;
    }
  }

  public static int column(int move) {
    return move & 0xFFFF;
  }

  public static boolean isSpecial(int move) {
    return (move & SPECIAL) != 0;
  }

  /**
   * Best move for colour me, encoded as {@code col | SPECIAL}; NONE if every column is full.
   *
   * @param myCounter the engine's cooldown counter for me before this turn (special honoured iff at least 10)
   * @param oppCounter the opponent's counter before its next turn
   * @param deadlineNanos time of ch.qc.bot.Clock after which the three-ply check stops; unchecked moves count as neutral
   */
  public int choose(Board b, int me, int myCounter, int oppCounter, long deadlineNanos) {
    deadline = deadlineNanos;
    int opp = 3 - me;
    int cols = g.cols;
    boolean mySp = myCounter >= COOLDOWN;
    boolean oppSp = oppCounter >= COOLDOWN;
    for (int i = 0; i < cols; i++) {
      int c = centre(i);
      if (!b.isFull(c) && b.threats(me, g.index(c, b.height(c))) > 0) {
        return c;
      }
    }
    if (mySp) {
      for (int i = 0; i < cols; i++) {
        int c = centre(i);
        if (b.blastWins(c)) {
          return c | SPECIAL;
        }
      }
    }
    boolean deep = cols <= DEEP_MAX_COLS;
    boolean mySpNext = myCounter + 1 >= COOLDOWN;
    int best = NONE;
    int bestClass = -1;
    int bestScore = Integer.MIN_VALUE;
    for (int i = 0; i < cols; i++) {
      int c = centre(i);
      if (b.isFull(c)) {
        continue;
      }
      int score = moveScore(b, c, me);
      child.copyFrom(b);
      child.make(c, me);
      int cls;
      if (winsNow(child, opp, oppSp)) {
        cls = LOSES_NOW;
      } else if (!deep || ch.qc.bot.Clock.nanos() > deadline) {
        cls = NEUTRAL;
      } else if (forcesWin(child, me, mySpNext, oppCounter)) {
        cls = WINS_IN_TWO;
      } else if (opponentForcesWin(child, me, mySpNext, oppCounter)) {
        cls = LOSES_IN_TWO;
      } else {
        cls = NEUTRAL;
      }
      if (cls > bestClass || (cls == bestClass && score > bestScore)) {
        best = c;
        bestClass = cls;
        bestScore = score;
      }
    }
    if (bestClass == LOSES_NOW && mySp) {
      for (int i = 0; i < cols; i++) {
        int c = centre(i);
        if (b.isFull(c)) {
          continue;
        }
        child.copyFrom(b);
        child.blast(c);
        if (!winsNow(child, opp, oppSp)) {
          return c | SPECIAL;
        }
      }
    }
    if (bestClass == LOSES_NOW) {
      // every move loses at once: at least take away one of the opponent's playable threat cells
      for (int i = 0; i < cols; i++) {
        int c = centre(i);
        if (!b.isFull(c) && b.threats(opp, g.index(c, b.height(c))) > 0) {
          return c;
        }
      }
    }
    return best;
  }

  /**
   * After our move (position a, opponent to move, no immediate opponent win): every opponent reply, drop or ready
   * blast, leaves us an immediate win. False if the opponent has no move at all (the board is full: a draw).
   */
  private boolean forcesWin(Board a, int me, boolean mySpNext, int oppCounter) {
    int opp = 3 - me;
    boolean any = false;
    for (int r = 0; r < g.cols; r++) {
      if (a.isFull(r)) {
        continue;
      }
      any = true;
      grand.copyFrom(a);
      grand.make(r, opp);
      if (!winsNow(grand, me, mySpNext)) {
        return false;
      }
      if (oppCounter >= COOLDOWN) {
        grand.copyFrom(a);
        grand.blast(r);
        if (!winsNow(grand, me, mySpNext)) {
          return false;
        }
      }
    }
    return any;
  }

  /** After our move (position a, opponent to move): some opponent reply leaves us no move that survives it. */
  private boolean opponentForcesWin(Board a, int me, boolean mySpNext, int oppCounter) {
    int opp = 3 - me;
    for (int r = 0; r < g.cols; r++) {
      if (a.isFull(r)) {
        continue;
      }
      if (ch.qc.bot.Clock.nanos() > deadline) {
        return false;
      }
      grand.copyFrom(a);
      grand.make(r, opp);
      if (!survives(grand, me, mySpNext, oppCounter + 1 >= COOLDOWN)) {
        return true;
      }
      if (oppCounter >= COOLDOWN) {
        grand.copyFrom(a);
        grand.blast(r);
        if (!survives(grand, me, mySpNext, false)) {
          return true;
        }
      }
    }
    return false;
  }

  /** Position p with us to move: we win at once, or have a drop (or ready blast) after which the opponent cannot. */
  private boolean survives(Board p, int me, boolean mySp, boolean oppSp) {
    if (winsNow(p, me, mySp)) {
      return true;
    }
    int opp = 3 - me;
    boolean any = false;
    for (int s = 0; s < g.cols; s++) {
      if (p.isFull(s)) {
        continue;
      }
      any = true;
      great.copyFrom(p);
      great.make(s, me);
      if (!winsNow(great, opp, oppSp)) {
        return true;
      }
    }
    if (mySp) {
      for (int s = 0; s < g.cols; s++) {
        if (p.isFull(s)) {
          continue;
        }
        great.copyFrom(p);
        great.blast(s);
        if (!winsNow(great, opp, oppSp)) {
          return true;
        }
      }
    }
    return !any;
  }

  /** True if colour who, to move, completes a line at once by a drop or, if its special is ready, by a blast. */
  public boolean winsNow(Board b, int who, boolean special) {
    for (int c = 0; c < g.cols; c++) {
      if (!b.isFull(c) && b.threats(who, g.index(c, b.height(c))) > 0) {
        return true;
      }
    }
    if (special) {
      for (int c = 0; c < g.cols; c++) {
        if (b.blastWins(c)) {
          return true;
        }
      }
    }
    return false;
  }

  /** Own window potential gained plus opponent potential destroyed by dropping into column c. */
  private int moveScore(Board b, int c, int me) {
    int opp = 3 - me;
    int i = g.index(c, b.height(c));
    int score = 0;
    for (int j = g.cellStart[i]; j < g.cellStart[i + 1]; j++) {
      int old = b.windowCode(g.cellWin[j]);
      int nw = old + me * t.pow3[g.cellPos[j]];
      score += potential[me][nw] - potential[me][old] + potential[opp][old] - potential[opp][nw];
    }
    return score;
  }

  private int centre(int i) {
    int mid = (g.cols - 1) / 2;
    return i % 2 == 0 ? mid - i / 2 : mid + (i + 1) / 2;
  }
}
