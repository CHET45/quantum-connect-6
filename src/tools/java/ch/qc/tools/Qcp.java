package ch.qc.tools;

import ch.qc.bot.eval.Features;
import ch.qc.bot.rules.Board;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Training records of design 4.2, little-endian, 48 bytes on 8x12 (cells/4 + 24 bytes in general).
 *
 * <pre>
 * header (32 bytes): "QCP1", u16 rows, u16 cols, u16 K, u16 record size, i64 specHash, i32 generation, i64 reserved
 * record: cells 2 bits each (cell i = col * rows + y; byte i/4, bits 2*(i%4); 0 empty, 1 first, 2 second)
 *   +0 stm (0 first, 1 second)  +1 cdMover  +2 cdOther (clamped to 10)  +3 stage  +4 cdCtx  +5 parity
 *   +6 u16 turn  +8 i16 value (mover's view, 1/256 logit; proven: +-(30000 - plies))  +10 depth
 *   +11 i8 proven (+1 mover wins, -1 loses, 0 none)  +12 u16 plies to the end (0xFFFF if adjudicated)
 *   +14 i8 result z (mover's view)  +15 flags (bit0 after a random move, bit3 adjudicated draw)
 *   +16 u16 t1  +18 u16 t2  +20 u16 t3  +22 u16 game id (low 16 bits)
 * </pre>
 * Offsets after the cells are relative to byte cells/4 (24 on 8x12).
 */
public final class Qcp {
  public static final int HEADER = 32;
  public static final int FLAG_AFTER_RANDOM = 1;
  public static final int FLAG_ADJUDICATED = 8;

  private Qcp() {}

  public static int recordSize(int cells) {
    return (cells + 3) / 4 + 24;
  }

  public static void writeHeader(OutputStream out, int rows, int cols, int k, long specHash, int generation)
      throws IOException {
    ByteBuffer bb = ByteBuffer.allocate(HEADER).order(ByteOrder.LITTLE_ENDIAN);
    bb.put((byte) 'Q').put((byte) 'C').put((byte) 'P').put((byte) '1');
    bb.putShort((short) rows).putShort((short) cols).putShort((short) k).putShort((short) recordSize(rows * cols));
    bb.putLong(specHash).putInt(generation).putLong(0);
    out.write(bb.array());
  }

  /** One position before the mover's move, with everything except the game result. */
  public static final class Rec {
    final byte[] cells;
    final int stm;
    final int cdMover;
    final int cdOther;
    final int stage;
    final int cdCtx;
    final int parity;
    final int turn;
    final int value;
    final int depth;
    final int proven;
    final int flags;
    final int[] t = new int[3];
    int pliesToEnd;
    int z;
    int extraFlags;

    public Rec(Board b, int stm, int cdMover, int cdOther, int turn, int value, int depth, int proven, int flags) {
      this.cells = b.cells();
      this.stm = stm;
      this.cdMover = Math.min(cdMover, 10);
      this.cdOther = Math.min(cdOther, 10);
      this.stage = Features.stage(b);
      this.cdCtx = Features.cdContext(cdMover, cdOther);
      this.parity = b.discs() & 1;
      this.turn = turn;
      this.value = value;
      this.depth = depth;
      this.proven = proven;
      this.flags = flags;
      Features.threatIndices(b, stm, cdMover, cdOther, t);
    }

    public void write(DataOutputStream out, int gameId) throws IOException {
      int cellBytes = (cells.length + 3) / 4;
      ByteBuffer bb = ByteBuffer.allocate(cellBytes + 24).order(ByteOrder.LITTLE_ENDIAN);
      byte[] packed = new byte[cellBytes];
      for (int i = 0; i < cells.length; i++) {
        packed[i / 4] |= (byte) (cells[i] << (2 * (i % 4)));
      }
      bb.put(packed);
      bb.put((byte) (stm - 1)).put((byte) cdMover).put((byte) cdOther).put((byte) stage).put((byte) cdCtx)
          .put((byte) parity);
      bb.putShort((short) turn).putShort((short) value).put((byte) depth).put((byte) proven);
      bb.putShort((short) pliesToEnd).put((byte) z).put((byte) (flags | extraFlags));
      bb.putShort((short) t[0]).putShort((short) t[1]).putShort((short) t[2]).putShort((short) gameId);
      out.write(bb.array());
    }
  }
}
