package ch.qc.bot;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.QuantumField;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;

/**
 * Quantum Connect bot. M0 baseline: plays the most central free column and never requests a special move,
 * but already runs the full per-call pipeline (snapshot, turn tracking, board load, guarded two-call protocol).
 */
public final class QcBot implements Algorithm {
  /** A special is never raised if the first call took longer than this (it may have timed out, R10, R15). */
  static final long SAFE_FINISH_NANOS = 85_000_000L;
  /** Boards above this many cells skip the board model and use the field directly (design 5.6). */
  static final int MAX_MODEL_CELLS = 4096;

  private final TurnTracker tracker = new TurnTracker();
  private Geometry geometry;
  private Board board;
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
      int col = choose(s);
      if (col < 0 || col >= s.cols || field.isColumnFull(col)) {
        col = safeColumn(field);
      }
      boolean special = false;
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

  private int choose(Snapshot s) {
    if (s.rows * s.cols > MAX_MODEL_CELLS) {
      return -1;
    }
    if (geometry == null || !geometry.sameSize(s.rows, s.cols)) {
      geometry = new Geometry(s.rows, s.cols);
      board = new Board(geometry);
    }
    board.load(s.cells);
    return centreFirst(board);
  }

  private static int centreFirst(Board b) {
    int cols = b.g.cols;
    for (int i = 0; i < cols; i++) {
      int c = centreOrder(cols, i);
      if (!b.isFull(c)) {
        return c;
      }
    }
    return -1;
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
