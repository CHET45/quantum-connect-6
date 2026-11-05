package ch.qc.tools;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Tactics;

/**
 * In-process game loop with the engine's turn semantics (R11-R19, the same model the conformance test checks
 * against the official engine): per-player cooldown counters, honoured specials only at 10 or more, a special on a
 * full column wastes the turn, an invalid column forfeits it, a line after a move wins for the mover, draws by a
 * full top row and at 1,000 turns. No threads and no clocks, so a game is reproducible given its engines.
 */
public final class FastGame {
  /** A player: returns a move in {@link Tactics} encoding for colour me to move on b (b must be left unchanged). */
  public interface Engine {
    int move(Board b, int me, int cdMe, int cdOpp, int turnIndex);
  }

  /** Called before each engine move (position, side to move, counters, turn) and with the move played. */
  public interface Observer {
    void turn(Board b, int stm, int cdStm, int cdOpp, int turnIndex, int move);
  }

  /** winner 0 for a draw; reason six, blast, full or cap; plies played including the opening. */
  public record Outcome(int winner, String reason, int plies) {}

  private FastGame() {}

  public static Outcome play(Geometry g, int[] opening, Engine first, Engine second, Observer observer) {
    Board b = new Board(g);
    int[] counter = new int[3];
    int toMove = 1;
    int iter = 0;
    for (int c : opening) {
      if (b.isFull(c) || b.make(c, toMove)) {
        throw new IllegalArgumentException("opening plays into a full column or makes a line");
      }
      counter[toMove]++;
      toMove = 3 - toMove;
      iter++;
    }
    Board view = new Board(g);
    while (true) {
      if (b.topRowFull()) {
        return new Outcome(0, "full", iter);
      }
      if (iter >= 1000) {
        return new Outcome(0, "cap", iter);
      }
      view.copyFrom(b);
      Engine e = toMove == 1 ? first : second;
      int move = e.move(view, toMove, counter[toMove], counter[3 - toMove], iter);
      if (observer != null) {
        observer.turn(b, toMove, counter[toMove], counter[3 - toMove], iter, move);
      }
      int col = move == Tactics.NONE ? -1 : Tactics.column(move);
      boolean special = move != Tactics.NONE && Tactics.isSpecial(move);
      boolean win = false;
      boolean blast = false;
      if (special && counter[toMove] >= 10 && col >= 0 && col < g.cols) {
        if (!b.isFull(col)) {
          win = b.blast(col);
          blast = true;
        }
        counter[toMove] = 0;
      } else if (col >= 0 && col < g.cols && !b.isFull(col)) {
        win = b.make(col, toMove);
      }
      counter[toMove]++;
      iter++;
      if (win) {
        return new Outcome(toMove, blast ? "blast" : "six", iter);
      }
      toMove = 3 - toMove;
    }
  }
}
