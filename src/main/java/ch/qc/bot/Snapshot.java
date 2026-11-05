package ch.qc.bot;

import ch.cern.quantumconnect.core.Move;
import ch.cern.quantumconnect.core.QuantumField;
import ch.cern.quantumconnect.core.SpaceOwnership;
import ch.qc.bot.rules.Board;
import java.util.List;

/** One read of the live engine field at the start of a call: cells, own colour, move history. */
public final class Snapshot {
  public final int rows;
  public final int cols;
  /** Cells in {@link ch.qc.bot.rules.Geometry} index order (col * rows + y, y = 0 bottom). */
  public final byte[] cells;
  public final int discs;
  /** Board.FIRST or Board.SECOND, or 0 when the board is empty and the colour cannot be read. */
  public final int myColour;
  public final List<Move> history;
  public final long fingerprint;

  private Snapshot(int rows, int cols, byte[] cells, int discs, int myColour, List<Move> history, long fingerprint) {
    this.rows = rows;
    this.cols = cols;
    this.cells = cells;
    this.discs = discs;
    this.myColour = myColour;
    this.history = history;
    this.fingerprint = fingerprint;
  }

  public static Snapshot read(QuantumField field) {
    SpaceOwnership[][] grid = field.getField();
    int rows = grid.length;
    int cols = rows == 0 ? 0 : grid[0].length;
    byte[] cells = new byte[rows * cols];
    int discs = 0;
    int myColour = 0;
    long fp = 1469598103934665603L;
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        int v = encode(grid[r][c]);
        cells[c * rows + (rows - 1 - r)] = (byte) v;
        fp = (fp ^ v) * 1099511628211L;
        if (v != Board.EMPTY) {
          discs++;
          if (myColour == 0) {
            myColour = field.belongsToMe(r, c) ? v : 3 - v;
          }
        }
      }
    }
    return new Snapshot(rows, cols, cells, discs, myColour, field.getMoveHistory(), fp);
  }

  /** Same fingerprint as {@link #read} computes, without the history copy. */
  public static long fingerprint(QuantumField field) {
    SpaceOwnership[][] grid = field.getField();
    long fp = 1469598103934665603L;
    for (SpaceOwnership[] row : grid) {
      for (SpaceOwnership v : row) {
        fp = (fp ^ encode(v)) * 1099511628211L;
      }
    }
    return fp;
  }

  public static int encode(SpaceOwnership v) {
    return v == SpaceOwnership.FIRST_PLAYER ? Board.FIRST : v == SpaceOwnership.SECOND_PLAYER ? Board.SECOND : Board.EMPTY;
  }
}
