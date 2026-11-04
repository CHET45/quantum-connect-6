package ch.qc.tools;

import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.eval.HandEval;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Diagnostic: how well static evaluations agree with the recorded search values and game outcomes. For a sample of
 * .qcp records it reports, per evaluator, the Spearman rank correlation with the search value and the AUC of
 * predicting a win against a loss. With --val only the validation games of training/qctrain/fit.py are used (the
 * same CRC-32 split), so a learned table is not judged on the positions it was fitted to.
 *
 * <pre>
 * EvalCheck SHARD_DIR [--weights eval.qcw ...] [--count N] [--val] [--rows R --cols C]
 * </pre>
 */
public final class EvalCheck {
  public static void main(String[] args) throws Exception {
    Path dir = Path.of(args[0]);
    List<String> weights = new ArrayList<>();
    int count = 200_000;
    boolean valOnly = false;
    int rows = 8;
    int cols = 12;
    for (int i = 1; i < args.length; i++) {
      switch (args[i]) {
        case "--weights" -> weights.add(args[++i]);
        case "--count" -> count = Integer.parseInt(args[++i]);
        case "--val" -> valOnly = true;
        case "--rows" -> rows = Integer.parseInt(args[++i]);
        case "--cols" -> cols = Integer.parseInt(args[++i]);
        default -> throw new IllegalArgumentException("unknown argument " + args[i]);
      }
    }
    Geometry g = new Geometry(rows, cols);
    int cellBytes = (g.cells + 3) / 4;
    int size = Qcp.recordSize(g.cells);
    List<Evaluator> evals = new ArrayList<>();
    List<String> names = new ArrayList<>();
    evals.add(new HandEval(g));
    names.add("hand");
    for (String w : weights) {
      evals.add(WeightFiles.load(Path.of(w), g));
      names.add(w);
    }
    List<double[]> table = new ArrayList<>(); // per record: value, z, eval_0, eval_1, ...
    try (var files = Files.list(dir)) {
      List<Path> shards = files.filter(p -> p.getFileName().toString().matches("shard-.*[.]qcp")).sorted().toList();
      for (int si = 0; si < shards.size(); si++) {
        Path shard = shards.get(si);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update((dir.getFileName() + "/" + si).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long salt = crc.getValue() & 0xFFFF;
        byte[] all = Files.readAllBytes(shard);
        ByteBuffer bb = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        int n = (all.length - Qcp.HEADER) / size;
        int step = valOnly ? 1 : Math.max(1, n / (count / 17 + 1));
        for (int r = 0; r < n; r += step) {
          int off = Qcp.HEADER + r * size;
          byte[] cells = new byte[g.cells];
          for (int i = 0; i < g.cells; i++) {
            cells[i] = (byte) ((all[off + i / 4] >> (2 * (i % 4))) & 3);
          }
          int o = off + cellBytes;
          int stm = all[o] + 1;
          int cdM = all[o + 1];
          int cdO = all[o + 2];
          int value = bb.getShort(o + 8);
          int proven = all[o + 11];
          int z = all[o + 14];
          if (proven != 0) {
            continue;
          }
          if (valOnly) {
            long key = Short.toUnsignedInt(bb.getShort(o + 22)) + salt * 65536;
            java.util.zip.CRC32 kc = new java.util.zip.CRC32();
            kc.update(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(key).array());
            if (kc.getValue() % 20 != 0) {
              continue;
            }
          }
          Board b = new Board(g);
          b.load(cells);
          double[] row = new double[2 + evals.size()];
          row[0] = value;
          row[1] = z;
          for (int e = 0; e < evals.size(); e++) {
            row[2 + e] = evals.get(e).evaluate(b, stm, cdM, cdO);
          }
          table.add(row);
        }
      }
    }
    int n = table.size();
    double[] v = column(table, 0);
    double[] z = column(table, 1);
    System.out.printf("%d unproven records%n", n);
    System.out.printf(java.util.Locale.ROOT, "  %-40s spearman(value) %.4f  AUC(win vs loss) %.4f%n", "search value",
        1.0, auc(v, z));
    for (int e = 0; e < evals.size(); e++) {
      double[] x = column(table, 2 + e);
      System.out.printf(java.util.Locale.ROOT, "  %-40s spearman(value) %.4f  AUC(win vs loss) %.4f%n", names.get(e),
          spearman(x, v), auc(x, z));
    }
  }

  private static double[] column(List<double[]> rows, int c) {
    double[] x = new double[rows.size()];
    for (int i = 0; i < x.length; i++) {
      x[i] = rows.get(i)[c];
    }
    return x;
  }

  private static double[] ranks(double[] x) {
    Integer[] idx = new Integer[x.length];
    for (int i = 0; i < idx.length; i++) {
      idx[i] = i;
    }
    Arrays.sort(idx, (a, b) -> Double.compare(x[a], x[b]));
    double[] r = new double[x.length];
    for (int i = 0; i < idx.length;) {
      int j = i;
      while (j + 1 < idx.length && x[idx[j + 1]] == x[idx[i]]) {
        j++;
      }
      double avg = (i + j) / 2.0;
      for (int k = i; k <= j; k++) {
        r[idx[k]] = avg;
      }
      i = j + 1;
    }
    return r;
  }

  private static double spearman(double[] a, double[] b) {
    double[] ra = ranks(a);
    double[] rb = ranks(b);
    double ma = Arrays.stream(ra).average().orElse(0);
    double mb = Arrays.stream(rb).average().orElse(0);
    double sab = 0;
    double saa = 0;
    double sbb = 0;
    for (int i = 0; i < ra.length; i++) {
      sab += (ra[i] - ma) * (rb[i] - mb);
      saa += (ra[i] - ma) * (ra[i] - ma);
      sbb += (rb[i] - mb) * (rb[i] - mb);
    }
    return sab / Math.sqrt(saa * sbb);
  }

  /** Probability that a random won position scores higher than a random lost one (ties count half). */
  private static double auc(double[] x, double[] z) {
    double[] r = ranks(x);
    double sumWin = 0;
    long wins = 0;
    long losses = 0;
    List<Double> sub = new ArrayList<>();
    for (int i = 0; i < x.length; i++) {
      if (z[i] != 0) {
        sub.add(x[i]);
      }
    }
    double[] xs = sub.stream().mapToDouble(Double::doubleValue).toArray();
    double[] rs = ranks(xs);
    int k = 0;
    for (int i = 0; i < x.length; i++) {
      if (z[i] == 0) {
        continue;
      }
      if (z[i] > 0) {
        sumWin += rs[k] + 1;
        wins++;
      } else {
        losses++;
      }
      k++;
    }
    return (sumWin - wins * (wins + 1) / 2.0) / ((double) wins * losses);
  }

  private EvalCheck() {}
}
