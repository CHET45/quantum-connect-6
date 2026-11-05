package ch.qc.tools;

import ch.qc.bot.eval.FeatureSpec;
import ch.qc.bot.eval.GenericSpec;
import ch.qc.bot.rules.Geometry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the FeatureSpec JSON that the training code reads: {@code FeatureSpecDump [--generic] [--k K] [rows cols] OUT};
 * with --generic the size-independent window family of {@link GenericSpec} for that board; --k for a line length
 * other than six.
 */
public final class FeatureSpecDump {
  public static void main(String[] argv) throws Exception {
    int k = ch.qc.bot.rules.Rules.K_DEFAULT;
    java.util.List<String> rest = new java.util.ArrayList<>();
    for (int i = 0; i < argv.length; i++) {
      if (argv[i].equals("--k")) {
        k = Integer.parseInt(argv[++i]);
      } else {
        rest.add(argv[i]);
      }
    }
    String[] args = rest.toArray(new String[0]);
    boolean generic = args.length > 0 && args[0].equals("--generic");
    int a = generic ? 1 : 0;
    int rows = args.length - a >= 3 ? Integer.parseInt(args[a]) : 8;
    int cols = args.length - a >= 3 ? Integer.parseInt(args[a + 1]) : 12;
    Path out = Path.of(args[args.length - 1]);
    Geometry g = new Geometry(rows, cols, k);
    FeatureSpec spec = new FeatureSpec(g);
    if (out.getParent() != null) {
      Files.createDirectories(out.getParent());
    }
    Files.writeString(out, generic ? GenericSpec.toJson(g) : spec.toJson(), StandardCharsets.UTF_8);
    System.out.printf("%dx%d%s: %d windows, %d classes, specHash %s -> %s%n", rows, cols, generic ? " generic" : "",
        g.windows, generic ? GenericSpec.CLASSES : spec.classes, Long.toHexString(spec.specHash), out);
  }

  private FeatureSpecDump() {}
}
