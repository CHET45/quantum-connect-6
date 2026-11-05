package ch.qc.bot;

/**
 * Result of accelerateQuark that useSpecialMove may act on (design 2.9). The special flag is only honoured
 * for the same call token and field, and only if the first call finished well inside its budget.
 */
final class Decision {
  private final int callId;
  private final long fingerprint;
  private final int column;
  private final boolean special;
  private final boolean finishedInTime;

  Decision(int callId, long fingerprint, int column, boolean special, boolean finishedInTime) {
    this.callId = callId;
    this.fingerprint = fingerprint;
    this.column = column;
    this.special = special;
    this.finishedInTime = finishedInTime;
  }

  int callId() {
    return callId;
  }

  long fingerprint() {
    return fingerprint;
  }

  int column() {
    return column;
  }

  boolean special() {
    return special;
  }

  boolean finishedInTime() {
    return finishedInTime;
  }
}
