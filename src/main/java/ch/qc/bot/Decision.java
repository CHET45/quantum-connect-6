package ch.qc.bot;

/**
 * Result of accelerateQuark that useSpecialMove may act on (design 2.9). The special flag is only honoured
 * for the same call token and field, and only if the first call finished well inside its budget.
 */
record Decision(int callId, long fingerprint, int column, boolean special, boolean finishedInTime) {}
