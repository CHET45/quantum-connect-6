package ch.qc.tools;

import ch.qc.bot.search.Searcher.Options;

/** Parses harness search-option specs such as "+razor,-leafVcf" (applied to {@link Options#DEFAULT}). */
public final class SearchOptions {
  private SearchOptions() {}

  public static Options parse(String spec) {
    Options o = Options.DEFAULT;
    for (String part : spec.split(",")) {
      part = part.trim();
      if (part.isEmpty()) {
        continue;
      }
      boolean on = !part.startsWith("-");
      String name = part.replaceFirst("^[+-]", "");
      o = switch (name) {
        case "rootVcf" -> new Options(on, o.opponentVcf, o.leafVcf, o.exactLeaves, o.razor);
        case "opponentVcf" -> new Options(o.rootVcf, on, o.leafVcf, o.exactLeaves, o.razor);
        case "leafVcf" -> new Options(o.rootVcf, o.opponentVcf, on, o.exactLeaves, o.razor);
        case "exactLeaves" -> new Options(o.rootVcf, o.opponentVcf, o.leafVcf, on, o.razor);
        case "razor" -> new Options(o.rootVcf, o.opponentVcf, o.leafVcf, o.exactLeaves, on);
        case "noVcf" -> Options.NONE;
        default -> throw new IllegalArgumentException("unknown search option " + name);
      };
    }
    return o;
  }
}
