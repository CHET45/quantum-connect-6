package ch.qc.tools;

import ch.qc.bot.eval.FeatureSpec;
import ch.qc.bot.rules.Geometry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes the FeatureSpec JSON that the training code reads: {@code FeatureSpecDump [rows cols] OUT}. */
public final class FeatureSpecDump {
  public static void main(String[] args) throws Exception {
    int rows = args.length >= 3 ? Integer.parseInt(args[0]) : 8;
    int cols = args.length >= 3 ? Integer.parseInt(args[1]) : 12;
    Path out = Path.of(args[args.length - 1]);
    FeatureSpec spec = new FeatureSpec(new Geometry(rows, cols));
    if (out.getParent() != null) {
      Files.createDirectories(out.getParent());
    }
    Files.writeString(out, spec.toJson(), StandardCharsets.UTF_8);
    System.out.printf("%dx%d: %d windows, %d classes, specHash %s -> %s%n", rows, cols, spec.g.windows, spec.classes,
        Long.toHexString(spec.specHash), out);
  }

  private FeatureSpecDump() {}
}
