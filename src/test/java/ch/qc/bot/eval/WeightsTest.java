package ch.qc.bot.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SplittableRandom;
import java.util.zip.DeflaterOutputStream;
import org.junit.jupiter.api.Test;

/**
 * Weight loading and the Java-Python parity test of design 4.6. The parity part runs when -Pqc.parity=DIR names a
 * model directory with eval.qcw and parity-8x12.qpv written by training/qctrain/parity.py.
 */
class WeightsTest {
  private static final Geometry G = new Geometry(8, 12);

  /** A blob in the export.py format with random weights, for the loader tests. */
  static byte[] blob(FeatureSpec spec, long hash, long seed) throws Exception {
    SplittableRandom rnd = new SplittableRandom(seed);
    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(raw))) {
      out.writeBytes("QCW1");
      for (int x : new int[] {1, spec.g.rows, spec.g.cols, spec.g.k}) {
        out.writeInt(x);
      }
      out.writeLong(hash);
      for (int x : new int[] {256, Features.STAGES, Features.CD_CONTEXTS, spec.classes, spec.codes, Features.T1_SIZE,
          Features.T2_SIZE, Features.T3_SIZE}) {
        out.writeInt(x);
      }
      int n = Features.STAGES * Features.CD_CONTEXTS * spec.windowParams()
          + Features.CD_CONTEXTS * (Features.T1_SIZE + Features.T2_SIZE) + Features.T3_SIZE;
      for (int i = 0; i < n; i++) {
        out.writeShort(rnd.nextInt(-300, 300));
      }
    }
    return raw.toByteArray();
  }

  @Test
  void validBlobLoadsAndEvaluates() throws Exception {
    FeatureSpec spec = new FeatureSpec(G);
    LearnedEval e = new LearnedEval(spec, WeightBlob.parse(blob(spec, spec.specHash, 1)));
    Board b = new Board(G);
    b.make(5, 1);
    b.make(6, 2);
    int v = e.evaluate(b, 1, 1, 1);
    assertTrue(Math.abs(v) <= Evaluator.MAX_EVAL);
    // colours swapped and the other side to move must give the same value
    Board s = new Board(G);
    s.make(5, 2);
    s.make(6, 1);
    assertEquals(v, e.evaluate(s, 2, 1, 1));
  }

  @Test
  void brokenBlobsAreRejected() throws Exception {
    FeatureSpec spec = new FeatureSpec(G);
    byte[] good = blob(spec, spec.specHash, 2);
    assertThrows(IllegalArgumentException.class, () -> WeightBlob.parse(java.util.Arrays.copyOf(good, good.length / 2)));
    byte[] corrupt = good.clone();
    for (int i = 10; i < 200; i++) {
      corrupt[i] ^= 0x5A;
    }
    assertThrows(IllegalArgumentException.class, () -> WeightBlob.parse(corrupt));
    WeightBlob wrongSpec = WeightBlob.parse(blob(spec, spec.specHash + 1, 3));
    assertTrue(!wrongSpec.matches(spec));
    assertThrows(IllegalArgumentException.class, () -> new LearnedEval(spec, wrongSpec));
    assertThrows(IllegalArgumentException.class, () -> WeightBlob.parse(new byte[] {1, 2, 3}));
  }

  @Test
  void javaMatchesPython() throws Exception {
    String dir = System.getProperty("qc.parity");
    assumeTrue(dir != null, "set -Pqc.parity=DIR to run the parity test");
    Path model = Path.of(dir);
    LearnedEval e = Weights.fromFile(model.resolve("eval.qcw"), G);
    ByteBuffer bb = ByteBuffer.wrap(Files.readAllBytes(model.resolve("parity-8x12.qpv")))
        .order(ByteOrder.LITTLE_ENDIAN);
    assertEquals('Q', bb.get());
    bb.position(4);
    int count = bb.getInt();
    int cells = bb.getInt();
    assertEquals(G.cells, cells);
    int[] t = new int[3];
    double maxErr = 0;
    for (int r = 0; r < count; r++) {
      byte[] c = new byte[cells];
      bb.get(c);
      int stm = bb.get();
      int cdM = bb.get();
      int cdO = bb.get();
      int expected = bb.getInt();
      int t1 = Short.toUnsignedInt(bb.getShort());
      int t2 = Short.toUnsignedInt(bb.getShort());
      int t3 = Short.toUnsignedInt(bb.getShort());
      float logit = bb.getFloat();
      Board b = new Board(G);
      b.load(c);
      Features.threatIndices(b, stm, cdM, cdO, t);
      assertEquals(t1, t[0], "t1 of record " + r);
      assertEquals(t2, t[1], "t2 of record " + r);
      assertEquals(t3, t[2], "t3 of record " + r);
      assertEquals(expected, e.rawSum(b, stm, cdM, cdO), "integer sum of record " + r);
      maxErr = Math.max(maxErr, Math.abs(expected / 256.0 - logit));
    }
    System.out.printf(java.util.Locale.ROOT, "parity: %d records equal; max |int/256 - float| %.4f logit%n", count,
        maxErr);
    assertTrue(maxErr <= 70 / 256.0, "quantisation error " + maxErr);
  }
}
