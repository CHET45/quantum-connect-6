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
  void heightStageEqualsFillStageOn8x12() {
    Geometry g = new Geometry(8, 12);
    SplittableRandom rnd = new SplittableRandom(11);
    for (int n = 0; n < 200; n++) {
      Board b = randomBoard(g, rnd, rnd.nextInt(g.cells));
      assertEquals(Features.stage(b), Features.heightStage(b));
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

  /**
   * Java-Python parity of generic tables: runs when -Pqc.parity=DIR names a model directory with eval.qcw and
   * parity-ROWSxCOLS.qpv files from training/qctrain/parity.py. Every record is checked twice: on a freshly loaded
   * board (full sum) and on a board where the last disc of a column was just dropped (the kept per-level sums).
   */
  @Test
  void parityWithThePythonModel() throws Exception {
    String dir = System.getProperty("qc.parity");
    org.junit.jupiter.api.Assumptions.assumeTrue(dir != null, "set -Pqc.parity=DIR to run the parity test");
    java.nio.file.Path model = java.nio.file.Path.of(dir);
    GenericTables tables = GenericTables.fromBlob(WeightFiles.parse(java.nio.file.Files.readAllBytes(model.resolve("eval.qcw"))));
    org.junit.jupiter.api.Assumptions.assumeTrue(tables != null, "not a generic model");
    int shift = WeightFiles.parse(java.nio.file.Files.readAllBytes(model.resolve("eval.qcw"))).scale == 1024 ? 2 : 0;
    int files = 0;
    try (java.nio.file.DirectoryStream<java.nio.file.Path> list = java.nio.file.Files.newDirectoryStream(model, "parity-*.qpv")) {
      for (java.nio.file.Path file : list) {
        files++;
        String name = file.getFileName().toString();
        String[] size = name.substring("parity-".length(), name.length() - ".qpv".length()).split("x");
        Geometry g = new Geometry(Integer.parseInt(size[0]), Integer.parseInt(size[1]));
        GenericEval e = tables.forGeometry(g);
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(java.nio.file.Files.readAllBytes(file)).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bb.position(4);
        int count = bb.getInt();
        assertEquals(g.cells, bb.getInt());
        int[] t = new int[3];
        for (int r = 0; r < count; r++) {
          byte[] c = new byte[g.cells];
          bb.get(c);
          int stm = bb.get();
          int cdM = bb.get();
          int cdO = bb.get();
          int expected = bb.getInt();
          int t1 = Short.toUnsignedInt(bb.getShort());
          int t2 = Short.toUnsignedInt(bb.getShort());
          int t3 = Short.toUnsignedInt(bb.getShort());
          bb.getFloat();
          Board b = new Board(g);
          assertTrue(b.load(c));
          Features.threatIndices(b, stm, cdM, cdO, t);
          assertEquals(t1, t[0], "t1 of record " + r);
          assertEquals(t2, t[1], "t2 of record " + r);
          assertEquals(t3, t[2], "t3 of record " + r);
          assertEquals(expected >> shift, e.rawSum(b, stm, cdM, cdO), name + " full sum of record " + r);
          // the same position reached by a drop: evaluate the parent first so the sum runs through the kept levels
          int col = -1;
          for (int x = 0; x < g.cols && col < 0; x++) {
            int h = b.height(x);
            if (h > 0 && c[x * g.rows + h - 1] == 3 - stm) {
              col = x;
            }
          }
          if (col >= 0) {
            int h = b.height(col);
            byte[] parent = c.clone();
            parent[col * g.rows + h - 1] = 0;
            Board p = new Board(g);
            if (p.load(parent)) {
              e.rawSum(p, stm, cdM, cdO);
              if (!p.make(col, 3 - stm)) {
                assertEquals(expected >> shift, e.rawSum(p, stm, cdM, cdO), name + " kept sum of record " + r);
              }
            }
          }
        }
        System.out.printf(java.util.Locale.ROOT, "parity %s: %d records equal%n", name, count);
      }
    }
    assertTrue(files > 0, "no parity files in " + dir);
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
