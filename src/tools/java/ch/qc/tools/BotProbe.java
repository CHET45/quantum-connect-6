package ch.qc.tools;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.QuantumField;
import ch.qc.bot.QcBot;
import ch.qc.bot.search.Searcher;
import java.lang.management.ManagementFactory;

/**
 * Wraps a bot to measure call latency and check every answer: the column is in range and not full, and a
 * special request comes only after a valid column that was returned in time. Timeouts are counted after the
 * game from the move history ({@link #expectedPlacements}).
 */
public final class BotProbe implements Algorithm {
  private final Algorithm inner;
  public final LatencyHistogram accelerate = new LatencyHistogram();
  public final LatencyHistogram special = new LatencyHistogram();
  /** Bytes allocated per accelerateQuark call, recorded as bytes * 1000 so that quantileMillis() reads in KB. */
  public final LatencyHistogram allocation = new LatencyHistogram();
  /** depthCounts[d]: completed search depth of QcBot calls. */
  public final long[] depthCounts = new long[65];
  private long searchNodes;
  private long searchNanos;
  /** Search time reported by the bot and call time minus search time, per call. */
  public final LatencyHistogram searchTime = new LatencyHistogram();
  public final LatencyHistogram outsideSearch = new LatencyHistogram();
  private static final com.sun.management.ThreadMXBean THREADS =
      (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

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
    long a0 = THREADS.getCurrentThreadAllocatedBytes();
    try {
      col = inner.accelerateQuark(field);
      allocation.record((THREADS.getCurrentThreadAllocatedBytes() - a0) * 1000);
      if (inner instanceof QcBot q && q.lastResult() != null) {
        Searcher.Result r = q.lastResult();
        synchronized (this) {
          depthCounts[Math.min(64, r.depth())]++;
          searchNodes += r.nodes();
          searchNanos += r.nanos();
        }
        searchTime.record(r.nanos());
        outsideSearch.record(System.nanoTime() - t0 - r.nanos());
      }
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

  public synchronized long searchNodes() {
    return searchNodes;
  }

  public synchronized long searchNanos() {
    return searchNanos;
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
