package ch.qc.tools;

import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.eval.GenericTables;
import ch.qc.bot.eval.LearnedEval;
import ch.qc.bot.eval.WeightBlob;
import ch.qc.bot.eval.Weights;
import ch.qc.bot.rules.Geometry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Loads .qcw weight files for the harness (the bot itself only reads the inflated blob compiled into it). */
public final class WeightFiles {
  private WeightFiles() {}

  /** Inflates the zlib stream of a .qcw file; throws IllegalArgumentException if it is truncated or corrupt. */
  public static byte[] inflate(byte[] deflated) {
    Inflater inf = new Inflater();
    try {
      inf.setInput(deflated);
      ByteArrayOutputStream out = new ByteArrayOutputStream(deflated.length * 4);
      byte[] buf = new byte[1 << 16];
      while (!inf.finished()) {
        int n = inf.inflate(buf);
        if (n == 0 && (inf.needsInput() || inf.needsDictionary())) {
          throw new IllegalArgumentException("truncated blob");
        }
        out.write(buf, 0, n);
      }
      return out.toByteArray();
    } catch (DataFormatException e) {
      throw new IllegalArgumentException("corrupt blob", e);
    } finally {
      inf.end();
    }
  }

  /** Parses a .qcw file's bytes; throws IllegalArgumentException on any format problem. */
  public static WeightBlob parse(byte[] deflated) {
    WeightBlob blob = WeightBlob.parse(inflate(deflated));
    if (blob == null) {
      throw new IllegalArgumentException("not a valid QCW1 blob");
    }
    return blob;
  }

  /**
   * A learned evaluation from a .qcw file: a generic blob serves any geometry, a sized one only its own; throws if
   * it does not fit the geometry or its feature spec.
   */
  public static Evaluator load(Path file, Geometry g) throws IOException {
    WeightBlob blob = parse(Files.readAllBytes(file));
    if (blob.generic()) {
      Evaluator e = GenericTables.fromBlob(blob).forGeometry(g);
      if (e == null) {
        throw new IllegalArgumentException(file + " is for windows of " + blob.k + " cells");
      }
      return e;
    }
    if (blob.rows != g.rows || blob.cols != g.cols) {
      throw new IllegalArgumentException(file + " is for " + blob.rows + "x" + blob.cols);
    }
    LearnedEval e = Weights.fromBlob(blob);
    if (e == null) {
      throw new IllegalArgumentException(file + " was trained for another feature spec");
    }
    return e;
  }
}
