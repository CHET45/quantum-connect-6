package ch.qc.bot;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.QuantumField;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Tactics;

/**
 * Quantum Connect bot. M1: root tactics ({@link Tactics}: immediate wins by drop or blast, no move that hands the
 * opponent an immediate or forced win, forced wins of our own, blasts only to win or as the last defence) on top of
 * the per-call pipeline (snapshot, turn tracking, board load, guarded two-call protocol).
 */
public final class QcBot implements Algorithm {
  /** A special is never raised if the first call took longer than this (it may have timed out, R10, R15). */
  static final long SAFE_FINISH_NANOS = 85_000_000L;
  /** Boards above this many cells skip the board model and use the field directly (design 5.6). */
  static final int MAX_MODEL_CELLS = 4096;
  /** Budget of the three-ply tactical check, measured from the entry of accelerateQuark. */
  static final long TACTICS_NANOS = 25_000_000L;

  private final TurnTracker tracker = new TurnTracker();
  private Geometry geometry;
  private Board board;
  private Tactics tactics;
  private int calls;
  private volatile Decision decision;

  public QcBot() {}

  @Override
  public int accelerateQuark(QuantumField field) {
    long t0 = System.nanoTime();
    int callId = ++calls;
    decision = null;
    try {
      Snapshot s = Snapshot.read(field);
      tracker.beginTurn(s);
      int move = choose(s, t0 + TACTICS_NANOS);
      int col = move == Tactics.NONE ? -1 : Tactics.column(move);
      boolean special = move != Tactics.NONE && Tactics.isSpecial(move);
      if (col < 0 || col >= s.cols || field.isColumnFull(col)) {
        col = safeColumn(field);
        special = false;
      }
      decision = new Decision(callId, s.fingerprint, col, special, System.nanoTime() - t0 <= SAFE_FINISH_NANOS);
      return col;
    } catch (Throwable e) {
      return safeColumn(field);
    }
  }

  @Override
  public boolean useSpecialMove(QuantumField field) {
    try {
      Decision d = decision;
      if (d == null || !d.special() || !d.finishedInTime() || d.callId() != calls || !tracker.ownSpecialReady()) {
        return false;
      }
      int col = d.column();
      if (col < 0 || col >= field.getNumberOfColumns() || field.isColumnFull(col)) {
        return false;
      }
      if (Snapshot.fingerprint(field) != d.fingerprint()) {
        return false;
      }
      tracker.noteSpecialRequested();
      return true;
    } catch (Throwable e) {
      return false;
    }
  }

  /**
   * Advances the turn tracker for a turn played by an opening script instead of this bot, so the own turn
   * count and cooldowns stay exact (match harness only).
   */
  public void observeScriptedTurn(QuantumField field) {
    ++calls;
    decision = null;
    tracker.beginTurn(Snapshot.read(field));
  }

  TurnTracker tracker() {
    return tracker;
  }

  private int choose(Snapshot s, long deadline) {
    if (s.rows * s.cols > MAX_MODEL_CELLS) {
      return Tactics.NONE;
    }
    if (geometry == null || !geometry.sameSize(s.rows, s.cols)) {
      geometry = new Geometry(s.rows, s.cols);
      board = new Board(geometry);
      tactics = new Tactics(geometry);
    }
    board.load(s.cells);
    // on an empty board the colour is unknown, but then no tactic applies to either colour
    int me = tracker.myColour() != 0 ? tracker.myColour() : tracker.iAmFirst() ? Board.FIRST : Board.SECOND;
    return tactics.choose(board, me, tracker.myCooldownCounter(), tracker.opponentCooldownCounter(), deadline);
  }

  /** Never returns a full column while one is free; works on the field alone. */
  static int safeColumn(QuantumField field) {
    int cols = field.getNumberOfColumns();
    for (int i = 0; i < cols; i++) {
      int c = centreOrder(cols, i);
      if (!field.isColumnFull(c)) {
        return c;
      }
    }
    return 0;
  }

  /** i-th column in centre-first order: for 12 columns 5, 6, 4, 7, 3, 8, ... */
  static int centreOrder(int cols, int i) {
    int mid = (cols - 1) / 2;
    return i % 2 == 0 ? mid - i / 2 : mid + (i + 1) / 2;
  }
}
