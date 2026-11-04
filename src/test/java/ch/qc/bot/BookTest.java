package ch.qc.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class BookTest {
  private static byte[] blob(Geometry g, int maxDiscs, Board position, int column) throws IOException {
    long a = Book.hash(position, false);
    long m = Book.hash(position, true);
    boolean flip = Long.compareUnsigned(m, a) < 0;
    long key = (flip ? m : a) >>> 24;
    int stored = flip ? g.cols - 1 - column : column;
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream d = new DataOutputStream(bytes);
    d.writeBytes("QCB1");
    d.writeByte(1);
    d.writeByte(g.rows);
    d.writeByte(g.cols);
    d.writeByte(maxDiscs);
    d.writeInt(1);
    for (int b = 4; b >= 0; b--) {
      d.writeByte((int) (key >>> (8 * b)));
    }
    d.writeByte(stored);
    return bytes.toByteArray();
  }

  private static Board board(Geometry g, int... columns) {
    Board b = new Board(g);
    int stm = Board.FIRST;
    for (int c : columns) {
      b.make(c, stm);
      stm = 3 - stm;
    }
    return b;
  }

  @Test
  void findsAPositionAndItsMirror() throws IOException {
    Geometry g = new Geometry(15, 11);
    Book book = Book.fromBytes(blob(g, 20, board(g, 3, 7), 2));
    assertEquals(1, book.size(15, 11));
    assertEquals(0, book.size(8, 12));
    assertEquals(2, book.column(board(g, 3, 7), 2, Board.FIRST, 1));
    // the mirror: columns 3 and 7 trade places, colours kept, the answer mirrors too
    assertEquals(8, book.column(board(g, 7, 3), 2, Board.FIRST, 1));
    assertEquals(-1, book.column(board(g, 3, 6), 2, Board.FIRST, 1));
  }

  @Test
  void anotherBoardSizeNeverGetsAColumn() throws IOException {
    Geometry g = new Geometry(15, 11);
    Book book = Book.fromBytes(blob(g, 20, new Board(g), 5));
    assertEquals(5, book.column(new Board(g), 0, Board.FIRST, 0));
    for (int[] size : new int[][] {{6, 7}, {8, 12}, {11, 15}, {15, 22}, {25, 25}}) {
      assertEquals(-1, book.column(new Board(new Geometry(size[0], size[1])), 0, Board.FIRST, 0));
    }
  }

  @Test
  void refusesWhenTheTurnsDoNotAddUp() throws IOException {
    Geometry g = new Geometry(15, 11);
    Board position = board(g, 3, 7);
    Book book = Book.fromBytes(blob(g, 20, position, 2));
    assertEquals(-1, book.column(position, 3, Board.FIRST, 1), "a skipped turn");
    assertEquals(-1, book.column(position, 2, Board.SECOND, 1), "wrong colour for the disc count");
    Book shallow = Book.fromBytes(blob(g, 1, position, 2));
    assertEquals(-1, shallow.column(position, 2, Board.FIRST, 1), "past the deepest entry");
  }

  @Test
  void refusesAMoveThatDoesNotBlock() throws IOException {
    Geometry g = new Geometry(8, 12);
    // F has five in column 4 and S to move must block there
    Board position = board(g, 4, 0, 4, 0, 4, 1, 4, 1, 4);
    Book elsewhere = Book.fromBytes(blob(g, 20, position, 7));
    assertEquals(-1, elsewhere.column(position, 9, Board.SECOND, 5));
    Book blocking = Book.fromBytes(blob(g, 20, position, 4));
    assertEquals(4, blocking.column(position, 9, Board.SECOND, 5));
  }

  @Test
  void refusesADropUnderAWinningCell() {
    Geometry g = new Geometry(8, 12);
    byte[] cells = new byte[g.cells];
    for (int c = 1; c <= 5; c++) {
      cells[g.index(c, 0)] = (byte) (c % 2 == 0 ? Board.FIRST : Board.SECOND);
      cells[g.index(c, 1)] = Board.FIRST;
    }
    Board b = new Board(g);
    assertTrue(b.load(cells));
    assertFalse(Book.safe(b, 6, Board.SECOND, 3), "F wins on top of it");
    assertTrue(Book.safe(b, 9, Board.SECOND, 3));
  }

  @Test
  void refusesAFullColumn() throws IOException {
    Geometry g = new Geometry(6, 12);
    Board position = board(g, 5, 5, 5, 5, 5, 5);
    assertTrue(position.isFull(5));
    Book book = Book.fromBytes(blob(g, 20, position, 5));
    assertEquals(-1, book.column(position, 6, Board.FIRST, 3));
  }

  @Test
  void refusesAMoveThatAllowsAWinningBlast() {
    Geometry g = new Geometry(8, 12);
    byte[] cells = new byte[g.cells];
    for (int c = 0; c <= 4; c++) {
      cells[g.index(c, 0)] = Board.FIRST;
    }
    cells[g.index(5, 0)] = Board.SECOND;
    cells[g.index(5, 1)] = Board.SECOND;
    cells[g.index(5, 2)] = Board.FIRST;
    Board b = new Board(g);
    assertTrue(b.load(cells));
    // a blast in empty column 6 lets F's disc in column 5 fall into the floor row beside its five
    assertFalse(Book.safe(b, 10, Board.SECOND, 10), "F's blast is ready");
    assertTrue(Book.safe(b, 10, Board.SECOND, 9), "F's blast is not ready");
    assertTrue(Book.safe(b, 6, Board.SECOND, 10), "a disc in column 6 moves the band up");
  }

  @Test
  void keysAreStable() {
    Geometry g = new Geometry(15, 11);
    Board b = board(g, 5, 5, 5, 4);
    // a changed hash silently empties every book, so it is pinned to the value of the first build
    long first = Book.hash(b, false);
    assertEquals(first, Book.hash(board(new Geometry(15, 11), 5, 5, 5, 4), false));
    assertEquals(first, Book.hash(board(g, 5, 5, 5, 4), false));
    assertNotEquals(first, Book.hash(b, true));
    assertEquals(PINNED_KEY, first);
    // Geometry's Zobrist keys come from SplittableRandom with a fixed seed: equal between constructions
    assertEquals(g.zobrist[1][17], new Geometry(15, 11).zobrist[1][17]);
    assertEquals(PINNED_ZOBRIST, g.zobrist[1][17]);
  }

  private static final long PINNED_KEY = 115213255417102408L;
  private static final long PINNED_ZOBRIST = -167800177346053779L;

  @Test
  void brokenBlobsGiveAnEmptyBook() {
    assertEquals(0, Book.fromBytes(new byte[] {1, 2, 3}).size(8, 12));
    assertEquals(0, Book.fromBytes(null).size(8, 12));
    assertEquals(0, Book.fromBytes(new byte[] {'Q', 'C', 'B', '1', 1, 8, 12, 20, 0, 0, 0, 9}).size(8, 12));
    assertEquals(0, Book.fromBase64(new String[] {"!!!!"}).size(8, 12));
    assertEquals(0, Book.fromBase64(new String[0]).size(8, 12));
  }

  @Test
  void base64RoundTripSplitIntoChunks() throws IOException {
    Geometry g = new Geometry(15, 11);
    byte[] raw = blob(g, 20, board(g, 3, 7), 2);
    for (int extra = 0; extra < 3; extra++) {
      byte[] padded = java.util.Arrays.copyOf(raw, raw.length + extra);
      String b64 = Base64.getEncoder().encodeToString(padded);
      String[] chunks = {b64.substring(0, 8), b64.substring(8)};
      Book book = Book.fromBase64(chunks);
      assertEquals(1, book.size(15, 11));
      assertEquals(2, book.column(board(g, 3, 7), 2, Board.FIRST, 1));
    }
    assertFalse(Book.embedded() == null);
  }
}
