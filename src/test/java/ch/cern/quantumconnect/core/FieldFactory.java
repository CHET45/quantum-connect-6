package ch.cern.quantumconnect.core;

/** Test access to the package-private engine field: build positions without running an Experiment. */
public final class FieldFactory {
  private FieldFactory() {}

  public static QuantumField create(int rows, int cols, Algorithm first, Algorithm second) {
    return new QuantumField(rows, cols, first, second);
  }

  /** Plays one engine turn for the side to move: a normal drop or a special, then passes the turn. */
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

  public static boolean firstToMove(QuantumField field, Algorithm first) {
    return field.getCurrentPlayer() == first;
  }
}
