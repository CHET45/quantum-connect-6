package ch.qc.tools;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.QuantumField;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Harness only, never uploaded: tests the engine loophole of the second call. The engine asks useSpecialMove every
 * turn and ignores the answer while the special is not ready, waits at most 100 ms, then moves on without stopping the
 * thread. This wrapper answers like the wrapped bot, and when the answer is "no special" keeps the thread busy until
 * the engine calls the bot again (or a second has passed): it burns CPU during the opponent's whole turn.
 */
public final class Vampire implements Algorithm {
  private static final long MAX_SPIN_NANOS = 1_000_000_000L;
  private static volatile double sink;

  private final Algorithm inner;
  private volatile long calls;
  /** CPU time burned and spins that outlived the engine's wait (stats). */
  public static final AtomicLong SPUN_NANOS = new AtomicLong();
  public static final AtomicLong SPINS = new AtomicLong();

  public Vampire(Algorithm inner) {
    this.inner = inner;
  }

  @Override
  public int accelerateQuark(QuantumField field) {
    calls++;
    return inner.accelerateQuark(field);
  }

  @Override
  public boolean useSpecialMove(QuantumField field) {
    boolean special = inner.useSpecialMove(field);
    if (special) {
      return true;
    }
    long seen = calls;
    long t0 = System.nanoTime();
    double x = 1.0001;
    // the engine gave up on this call after 100 ms; this loop runs on through the opponent's turn
    while (calls == seen && System.nanoTime() - t0 < MAX_SPIN_NANOS) {
      for (int i = 0; i < 10_000; i++) {
        x = x * 1.0000001 + 1e-9;
      }
      sink = x;
    }
    SPUN_NANOS.addAndGet(System.nanoTime() - t0);
    SPINS.incrementAndGet();
    return false;
  }
}
