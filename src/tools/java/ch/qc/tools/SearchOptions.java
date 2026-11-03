package ch.qc.tools;

import ch.qc.bot.search.Searcher.Options;

/**
 * Parses harness search-option specs such as "+razor,-leafVcf" or "ebf=0,wd=1,margin=64" (applied to
 * {@link Options#DEFAULT}; contempt, ebf, wd = blastWinMinDepth, margin = blastMargin, lmr = lmrFrom).
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
      int eq = part.indexOf('=');
      if (eq > 0) {
        int v = Integer.parseInt(part.substring(eq + 1));
        o = switch (part.substring(0, eq)) {
          case "contempt" -> o.withContempt(v);
          case "ebf" -> o.withEbf(v);
          case "wd" -> o.withBlastWinMinDepth(v);
          case "margin" -> o.withBlastMargin(v);
          case "lmr" -> o.withLmrFrom(v);
          default -> throw new IllegalArgumentException("unknown search option " + part);
        };
        continue;
      }
      boolean on = !part.startsWith("-");
      String name = part.replaceFirst("^[+-]", "");
      o = switch (name) {
        case "rootVcf" -> o.withSwitches(on, o.opponentVcf, o.leafVcf, o.exactLeaves, o.razor);
        case "opponentVcf" -> o.withSwitches(o.rootVcf, on, o.leafVcf, o.exactLeaves, o.razor);
        case "leafVcf" -> o.withSwitches(o.rootVcf, o.opponentVcf, on, o.exactLeaves, o.razor);
        case "exactLeaves" -> o.withSwitches(o.rootVcf, o.opponentVcf, o.leafVcf, on, o.razor);
        case "razor" -> o.withSwitches(o.rootVcf, o.opponentVcf, o.leafVcf, o.exactLeaves, on);
        case "noVcf" -> Options.NONE;
        default -> throw new IllegalArgumentException("unknown search option " + name);
      };
    }
    return o;
  }
}
