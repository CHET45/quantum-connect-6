package ch.cern.quantumconnect.core;

/** Harness access to the package-private engine field, for drivers that call bots through the official timer. */
public final class ToolsFields {
  private ToolsFields() {}

  public static QuantumField create(int rows, int cols, Algorithm first, Algorithm second) {
    return new QuantumField(rows, cols, first, second);
  }

  /** One engine turn for the side to move: a drop or an honoured special, then the turn passes. */
  public static void play(QuantumField field, int col, boolean special) {
    if (special) {
      field.produceSpecialCollision(col);
    } else {
      field.produceCollision(col);
    }
    field.changeCurrentPlayer();
  }

  public static ExperimentStatus status(QuantumField field) {
    return field.getExperimentStatus();
  }
}
