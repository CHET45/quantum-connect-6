package ch.qc.tools;

import ch.qc.bot.search.Searcher.Options;

/**
 * Parses harness search-option specs such as "+razor,-leafVcf" or "ebf=0,wd=1,margin=64" (applied to
 * {@link Options#DEFAULT}; contempt, ebf, wd = blastWinMinDepth, margin = blastMargin, lmr = lmrFrom; the other
 * names are the tuning fields of {@link Options}).
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
        String name = part.substring(0, eq);
        switch (name) {
          case "contempt" -> o = o.withContempt(v);
          case "ebf" -> o = o.withEbf(v);
          case "wd" -> o = o.withBlastWinMinDepth(v);
          case "margin" -> o = o.withBlastMargin(v);
          case "lmr" -> o = o.withLmrFrom(v);
          default -> {
            o = o.copy();
            setInt(o, name, v);
          }
        }
        continue;
      }
      boolean on = !part.startsWith("-");
      String name = part.replaceFirst("^[+-]", "");
      switch (name) {
        case "rootVcf" -> o = o.withSwitches(on, o.opponentVcf, o.leafVcf, o.exactLeaves, o.razor);
        case "opponentVcf" -> o = o.withSwitches(o.rootVcf, on, o.leafVcf, o.exactLeaves, o.razor);
        case "leafVcf" -> o = o.withSwitches(o.rootVcf, o.opponentVcf, on, o.exactLeaves, o.razor);
        case "exactLeaves" -> o = o.withSwitches(o.rootVcf, o.opponentVcf, o.leafVcf, on, o.razor);
        case "razor" -> o = o.withSwitches(o.rootVcf, o.opponentVcf, o.leafVcf, o.exactLeaves, on);
        case "noVcf" -> o = Options.NONE;
        default -> {
          o = o.copy();
          setBool(o, name, on);
        }
      }
    }
    return o;
  }

  private static void setInt(Options o, String name, int v) {
    switch (name) {
      case "maxDepth" -> o.maxDepth = v;
      case "nodeVcf" -> o.nodeVcf = v;
      case "nodeVcfNodes" -> o.nodeVcfNodes = v;
      case "lmrDepth" -> o.lmrDepth = v;
      case "lmrRoot" -> o.lmrRoot = v;
      case "lmrTact" -> o.lmrTact = v;
      case "lmrBlast" -> o.lmrBlast = v;
      case "lmp" -> o.lmp = v;
      case "nullR" -> o.nullR = v;
      case "blastGenDepth" -> o.blastGenDepth = v;
      case "rootVcfShare" -> o.rootVcfShare = v;
      case "oppVcfShare" -> o.oppVcfShare = v;
      case "vcfBlast" -> o.vcfBlast = v;
      default -> throw new IllegalArgumentException("unknown search option " + name);
    }
  }

  private static void setBool(Options o, String name, boolean on) {
    switch (name) {
      case "nodeRule" -> o.nodeRule = on;
      case "audit" -> o.audit = on;
      case "forcedExt" -> o.forcedExt = on;
      case "blastQs" -> o.blastQs = on;
      case "blastExt" -> o.blastExt = on;
      case "blastDbl" -> o.blastDbl = on;
      case "partial" -> o.partial = on;
      default -> throw new IllegalArgumentException("unknown search option " + name);
    }
  }
}
