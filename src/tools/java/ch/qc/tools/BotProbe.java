package ch.qc.tools;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.QuantumField;
import ch.qc.bot.QcBot;

/**
 * Wraps a bot to measure call latency and check every answer: the column is in range and not full, and a
 * special request comes only after a valid column that was returned in time. Timeouts are counted after the
 * game from the move history ({@link #expectedPlacements}).
 */
public final class BotProbe implements Algorithm {
  private final Algorithm inner;
  public final LatencyHistogram accelerate = new LatencyHistogram();
  public final LatencyHistogram special = new LatencyHistogram();

  private int calls;
  private int invalidColumns;
  private int exceptions;
  private int specialRequests;
  private int invalidSpecials;
  private int scripted;
  private volatile boolean lastAnswerSafe;

  public BotProbe(Algorithm inner) {
    this.inner = inner;
  }

  public Algorithm inner() {
    return inner;
  }

  @Override
  public int accelerateQuark(QuantumField field) {
    long t0 = System.nanoTime();
    lastAnswerSafe = false;
    synchronized (this) {
      calls++;
    }
    int col;
    try {
      col = inner.accelerateQuark(field);
    } catch (RuntimeException | Error e) {
      synchronized (this) {
        exceptions++;
      }
      throw e;
    } finally {
      accelerate.record(System.nanoTime() - t0);
    }
    long took = System.nanoTime() - t0;
    boolean valid = col >= 0 && col < field.getNumberOfColumns() && !field.isColumnFull(col);
    if (!valid) {
      synchronized (this) {
        invalidColumns++;
      }
    }
    lastAnswerSafe = valid && took < 100_000_000L;
    return col;
  }

  @Override
  public boolean useSpecialMove(QuantumField field) {
    long t0 = System.nanoTime();
    try {
      boolean s = inner.useSpecialMove(field);
      if (s) {
        synchronized (this) {
          specialRequests++;
          if (!lastAnswerSafe) {
            invalidSpecials++;
          }
        }
      }
      return s;
    } finally {
      special.record(System.nanoTime() - t0);
    }
  }

  /** Forwarded by {@link ScriptedOpening} for turns the script plays instead of the bot. */
  public void observeScriptedTurn(QuantumField field) {
    synchronized (this) {
      scripted++;
    }
    if (inner instanceof QcBot q) {
      q.observeScriptedTurn(field);
    }
  }

  /** Own discs the engine should have placed if no call timed out (each answered valid column is one disc). */
  public synchronized int expectedPlacements() {
    return calls - invalidColumns - exceptions + scripted;
  }

  public synchronized int calls() {
    return calls;
  }

  public synchronized int invalidColumns() {
    return invalidColumns;
  }

  public synchronized int exceptions() {
    return exceptions;
  }

  public synchronized int specialRequests() {
    return specialRequests;
  }

  public synchronized int invalidSpecials() {
    return invalidSpecials;
  }
}
