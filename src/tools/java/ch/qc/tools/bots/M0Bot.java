package ch.qc.tools.bots;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.QuantumField;

/** Frozen M0 behaviour for the ladder: the most central free column, never a special. */
public final class M0Bot implements Algorithm {
  @Override
  public int accelerateQuark(QuantumField field) {
    int cols = field.getNumberOfColumns();
    int mid = (cols - 1) / 2;
    for (int i = 0; i < cols; i++) {
      int c = i % 2 == 0 ? mid - i / 2 : mid + (i + 1) / 2;
      if (!field.isColumnFull(c)) {
        return c;
      }
    }
    return 0;
  }
}
