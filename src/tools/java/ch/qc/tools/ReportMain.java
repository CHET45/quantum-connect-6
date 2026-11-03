package ch.qc.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pair statistics and the SPRT verdict of stored match logs (the JSONL lines of {@link MatchMain}).
 *
 * <pre>
 * ReportMain [--sprt elo0,elo1] FILE...
 * </pre>
 *
 * Also reads logs written before the locale fix, whose scores have a decimal comma ("scoreA":0,5).
 */
public final class ReportMain {
  private static final Pattern PAIR = Pattern.compile("\"pair\":(\\d+)");
  private static final Pattern SCORE = Pattern.compile("\"scoreA\":(\\d)[.,](\\d)");
  private static final Pattern REASON = Pattern.compile("\"reason\":\"(\\w+)\"");
  private static final Pattern A_FIRST = Pattern.compile("\"aFirst\":(true|false)");

  public static void main(String[] args) throws Exception {
    double elo0 = 0;
    double elo1 = 10;
    int i = 0;
    if (args.length > 1 && args[0].equals("--sprt")) {
      String[] b = args[1].split(",");
      elo0 = Double.parseDouble(b[0]);
      elo1 = Double.parseDouble(b[1]);
      i = 2;
    }
    for (; i < args.length; i++) {
      System.out.println(Path.of(args[i]).getFileName() + ": " + report(Path.of(args[i]), elo0, elo1));
    }
  }

  public static String report(Path file, double elo0, double elo1) throws Exception {
    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    Map<Integer, int[]> pairs = new TreeMap<>(); // pair -> {half points, games}
    int games = 0;
    int draws = 0;
    int blasts = 0;
    double firstScore = 0;
    for (String line : lines) {
      if (line.isBlank()) {
        continue;
      }
      int pair = Integer.parseInt(find(PAIR, line));
      Matcher sm = SCORE.matcher(line);
      if (!sm.find()) {
        throw new IllegalArgumentException("no score in " + line);
      }
      int half = 2 * Integer.parseInt(sm.group(1)) + (sm.group(2).equals("5") ? 1 : 0);
      String reason = find(REASON, line);
      boolean aFirst = find(A_FIRST, line).equals("true");
      int[] p = pairs.computeIfAbsent(pair, k -> new int[2]);
      p[0] += half;
      p[1]++;
      games++;
      if (reason.equals("full") || reason.equals("cap")) {
        draws++;
      }
      if (reason.equals("blast")) {
        blasts++;
      }
      firstScore += aFirst ? half / 2.0 : 1 - half / 2.0;
    }
    Sprt sprt = new Sprt(elo0, elo1);
    int incomplete = 0;
    for (int[] p : pairs.values()) {
      if (p[1] == 2) {
        sprt.add(p[0]);
      } else {
        incomplete++;
      }
    }
    return String.format(java.util.Locale.ROOT,
        "%d games, draws %.1f%%, decided by a blast %.1f%%, first player scores %.1f%%%s%n  %s",
        games, 100.0 * draws / games, 100.0 * blasts / games, 100 * firstScore / games,
        incomplete > 0 ? ", " + incomplete + " unpaired games ignored" : "", sprt.summary());
  }

  private static String find(Pattern p, String line) {
    Matcher m = p.matcher(line);
    if (!m.find()) {
      throw new IllegalArgumentException("missing " + p + " in " + line);
    }
    return m.group(1);
  }

  private ReportMain() {}
}
