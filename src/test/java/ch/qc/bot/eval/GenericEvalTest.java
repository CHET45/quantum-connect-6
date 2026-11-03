package ch.qc.bot.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.tools.WeightFiles;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.SplittableRandom;
import java.util.zip.DeflaterOutputStream;
import org.junit.jupiter.api.Test;

/**
 * The size-independent evaluation: its computed indices agree with the featMap the training code uses, it is
 * mirror-symmetric, and a generic blob loads for any board size.
 */
class GenericEvalTest {
  private static final int[][] SIZES = {{8, 12}, {15, 11}, {15, 22}, {6, 7}};

  static byte[] genericBlob(int stages, long seed) throws Exception {
    return genericBlob(stages, seed, 256);
  }

  static byte[] genericBlob(int stages, long seed, int scale) throws Exception {
    SplittableRandom rnd = new SplittableRandom(seed);
    int codes = 729;
    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(raw))) {
      out.writeBytes("QCW1");
      for (int x : new int[] {1, 0, 0, 6}) {
        out.writeInt(x);
      }
      out.writeLong(GenericSpec.hash(6));
      for (int x : new int[] {scale, stages, Features.CD_CONTEXTS, GenericSpec.CLASSES, codes, Features.T1_SIZE,
          Features.T2_SIZE, Features.T3_SIZE}) {
        out.writeInt(x);
      }
      int n = stages * Features.CD_CONTEXTS * GenericSpec.CLASSES * codes
          + Features.CD_CONTEXTS * (Features.T1_SIZE + Features.T2_SIZE) + Features.T3_SIZE;
      for (int i = 0; i < n; i++) {
        out.writeShort(rnd.nextInt(-300, 300));
      }
    }
    return raw.toByteArray();
  }

  private static Board randomBoard(Geometry g, SplittableRandom rnd, int discs) {
    Board b = new Board(g);
    int colour = 1;
    for (int i = 0; i < discs; i++) {
      int c = rnd.nextInt(g.cols);
      if (b.isFull(c)) {
        continue;
      }
      if (b.make(c, colour)) {
        b.unmake(c);
        continue;
      }
      colour = 3 - colour;
    }
    return b;
  }

  @Test
  void computedIndicesMatchTheFeatMap() throws Exception {
    WeightBlob blob = WeightFiles.parse(genericBlob(Features.STAGES, 1));
    GenericTables tables = GenericTables.fromBlob(blob);
    SplittableRandom rnd = new SplittableRandom(2);
    for (int[] size : SIZES) {
      Geometry g = new Geometry(size[0], size[1]);
      GenericEval e = tables.forGeometry(g);
      int[] map = GenericSpec.featMap(g);
      int codes = g.tables.codes;
      int params = GenericSpec.CLASSES * codes;
      for (int n = 0; n < 200; n++) {
        Board b = randomBoard(g, rnd, rnd.nextInt(g.cells));
        int stm = 1 + rnd.nextInt(2);
        int cdStm = rnd.nextInt(11);
        int cdOpp = rnd.nextInt(11);
        int cd = Features.cdContext(cdStm, cdOpp);
        int ctx = Features.stage(b) * Features.CD_CONTEXTS + cd;
        int sum = 0;
        for (int w = 0; w < g.windows; w++) {
          int code = b.windowCode(w);
          int rel = stm == 1 ? code : g.tables.swap12[code];
          sum += blob.w[ctx * params + map[w * codes + rel]];
        }
        int packed = Features.threatIndices(b, stm, cdStm, cdOpp);
        sum += blob.t1[cd * Features.T1_SIZE + Features.t1(packed)];
        sum += blob.t2[cd * Features.T2_SIZE + Features.t2(packed)];
        sum += blob.t3[Features.t3(packed)];
        assertEquals(sum, e.rawSum(b, stm, cdStm, cdOpp), size[0] + "x" + size[1]);
      }
    }
  }

  @Test
  void fineScaleIsReportedInTheUsualUnits() throws Exception {
    Geometry g = new Geometry(15, 11);
    GenericEval coarse = GenericTables.fromBlob(WeightFiles.parse(genericBlob(1, 7, 256))).forGeometry(g);
    GenericEval fine = GenericTables.fromBlob(WeightFiles.parse(genericBlob(1, 7, 1024))).forGeometry(g);
    SplittableRandom rnd = new SplittableRandom(8);
    for (int n = 0; n < 50; n++) {
      Board b = randomBoard(g, rnd, rnd.nextInt(g.cells));
      assertEquals(coarse.rawSum(b, 1, 3, 5) >> 2, fine.rawSum(b, 1, 3, 5));
    }
  }

  @Test
  void mirroredBoardScoresTheSame() throws Exception {
    GenericTables tables = GenericTables.fromBlob(WeightFiles.parse(genericBlob(1, 3)));
    SplittableRandom rnd = new SplittableRandom(4);
    for (int[] size : SIZES) {
      Geometry g = new Geometry(size[0], size[1]);
      GenericEval e = tables.forGeometry(g);
      for (int n = 0; n < 100; n++) {
        Board b = randomBoard(g, rnd, rnd.nextInt(g.cells));
        Board m = new Board(g);
        byte[] cells = b.cells();
        byte[] mc = new byte[cells.length];
        for (int c = 0; c < g.cols; c++) {
          for (int y = 0; y < g.rows; y++) {
            mc[(g.cols - 1 - c) * g.rows + y] = cells[c * g.rows + y];
          }
        }
        assertTrue(m.load(mc));
        assertEquals(e.rawSum(b, 1, 3, 5), e.rawSum(m, 1, 3, 5), size[0] + "x" + size[1]);
      }
    }
  }

  @Test
  void genericBlobServesEveryBoardButNotAsSized() throws Exception {
    WeightBlob blob = WeightFiles.parse(genericBlob(1, 5));
    assertTrue(blob.generic());
    assertEquals(null, Weights.fromBlob(blob));
    GenericTables tables = GenericTables.fromBlob(blob);
    for (int[] size : SIZES) {
      Geometry g = new Geometry(size[0], size[1]);
      assertTrue(tables.forGeometry(g).fits(g));
    }
  }
}
