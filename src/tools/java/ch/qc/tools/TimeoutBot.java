package ch.qc.tools;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.QuantumField;
import java.util.SplittableRandom;

/**
 * An opponent like the worst on the server: most of its first calls run past any limit up to 200 ms, some of its second
 * calls too, so the bot sees forfeited turns and long turns of varying length.
 */
public final class TimeoutBot implements Algorithm {
  private final SplittableRandom rnd = new SplittableRandom(System.nanoTime());

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public int accelerateQuark(QuantumField field) {
    if (rnd.nextInt(100) < 70) {
      sleep(250);
    } else {
      sleep(rnd.nextLong(1, 95));
    }
    int cols = field.getNumberOfColumns();
    int c = rnd.nextInt(cols);
    for (int i = 0; i < cols && field.isColumnFull(c); i++) {
      c = (c + 1) % cols;
    }
    return c;
  }

  @Override
  public boolean useSpecialMove(QuantumField field) {
    if (rnd.nextInt(100) < 20) {
      sleep(250);
    }
    return false;
  }
}
