package ch.qc.tools;

import ch.qc.bot.search.Searcher.Options;

/**
 * Parses harness search-option specs such as "+razor,-leafVcf" or "contempt=64" (applied to {@link Options#DEFAULT}).
 */
public final class SearchOptions {
  private SearchOptions() {}

  public static Options parse(String spec) {
    Options o = Options.DEFAULT;
    for (String part : spec.split(",")) {
      part = part.trim();
      if (part.isEmpty()) {
        continue;
      }
      if (part.startsWith("contempt=")) {
        int c = Integer.parseInt(part.substring(9));
        o = new Options(o.rootVcf, o.opponentVcf, o.leafVcf, o.exactLeaves, o.razor, c);
        continue;
      }
      boolean on = !part.startsWith("-");
      String name = part.replaceFirst("^[+-]", "");
      int ct = o.contempt;
      o = switch (name) {
        case "rootVcf" -> new Options(on, o.opponentVcf, o.leafVcf, o.exactLeaves, o.razor, ct);
        case "opponentVcf" -> new Options(o.rootVcf, on, o.leafVcf, o.exactLeaves, o.razor, ct);
        case "leafVcf" -> new Options(o.rootVcf, o.opponentVcf, on, o.exactLeaves, o.razor, ct);
        case "exactLeaves" -> new Options(o.rootVcf, o.opponentVcf, o.leafVcf, on, o.razor, ct);
        case "razor" -> new Options(o.rootVcf, o.opponentVcf, o.leafVcf, o.exactLeaves, on, ct);
        case "noVcf" -> Options.NONE;
        default -> throw new IllegalArgumentException("unknown search option " + name);
      };
    }
    return o;
  }
}
