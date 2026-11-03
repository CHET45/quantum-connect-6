package ch.qc.tools;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Replays tournament-server games (matches.jsonl exports) on {@link Board} with the engine's turn rules and
 * checks each one against the recorded winner. Notation of "moves": a concatenation of tokens, side letter F or
 * S, one hex column digit, and a trailing * for a blast; a turn lost to a timeout or a full column leaves no
 * token (it shows as a break in the F/S alternation); "win_at" is the 0-based index of the winning token.
 * Several exports are merged by game id. Draws are not stored by the server, so every game here is decided.
 *
 * <pre>
 * [--rows 8 --cols 12 --team "No beaches?" --report FILE --openings FILE --plies 8 --skips-free] a.jsonl b.jsonl
 * </pre>
 */
public final class ServerGames {
  private static final int COOLDOWN = 10;
  private static final String[] SIDE = {"", "F", "S"};

  /** One server game; side 1 is the first player F, side 2 the second player S. */
  record Game(String id, String[] name, String[] team, String moves, int winner, int winAt, String timeouts,
      String full, int[] oom, int warn, int warnMax, List<String> errors) {
    String key() {
      return name[0] + "|" + name[1] + "|" + moves;
    }
  }

  /** A turn that left no token: side, 0-based turn index counting skipped turns, kind T (timeout) or C (full). */
  record Skip(int side, int turn, char kind) {}

  /** A timestamped server log event. */
  record Event(char kind, int side, long ms) {}

  /** Result of replaying one game. */
  static final class Replay {
    String problem;
    int plies;
    int turns;
    String reason = "none";
    int winner;
    Board board;
    final List<Skip> skips = new ArrayList<>();
    final int[] blasts = new int[3];
    /** Per blast: side, own turn index (skipped turns included) and the cooldown counter when it was played. */
    final List<int[]> blastLog = new ArrayList<>();
    String lastToken = "";
  }

  /** A replayed game with its derived log facts. */
  static final class Item {
    Game g;
    Replay r;
    List<Event> events = new ArrayList<>();
    long oomMs = -1;
    int oomMb = -1;
    boolean matched;
  }

  /** Per-bot totals. */
  static final class Bot {
    String name;
    int games;
    int wins;
    int firstGames;
    int firstWins;
    int secondGames;
    int secondWins;
    int six;
    int blastGames;
    int sixWins;
    int blastWins;
    int blastsUsed;
    int timeouts;
    int timeoutGames;
    int fulls;
    int oomGames;
    final List<Integer> plies = new ArrayList<>();
    final List<Integer> turns = new ArrayList<>();
    final List<Integer> mb = new ArrayList<>();
    final List<Integer> warnMax = new ArrayList<>();
    int warnGames;
    /** OOM games by which side timed out: own side only, opponent only, both, none. */
    final int[] oomTimeouts = new int[4];
    final int[] plainTimeouts = new int[4];
    /** Next timeout or full-column event after the OOM line: own, opponent, none. */
    final int[] nextAfterOom = new int[3];
  }

  public static void main(String[] args) throws Exception {
    int rows = 8;
    int cols = 12;
    int plies = 8;
    String team = "No beaches?";
    String report = null;
    String openings = null;
    boolean skipsFree = false;
    List<String> files = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--rows" -> rows = Integer.parseInt(args[++i]);
        case "--cols" -> cols = Integer.parseInt(args[++i]);
        case "--plies" -> plies = Integer.parseInt(args[++i]);
        case "--team" -> team = args[++i];
        case "--report" -> report = args[++i];
        case "--openings" -> openings = args[++i];
        case "--skips-free" -> skipsFree = true;
        default -> files.add(args[i]);
      }
    }
    if (files.isEmpty()) {
      throw new IllegalArgumentException("no matches.jsonl given");
    }
    Map<String, Game> byId = new LinkedHashMap<>();
    int lines = 0;
    for (String f : files) {
      for (String line : Files.readAllLines(Path.of(f), StandardCharsets.UTF_8)) {
        if (!line.isBlank()) {
          lines++;
          Game g = game(line);
          byId.putIfAbsent(g.id, g);
        }
      }
    }
    Map<String, Game> byKey = new LinkedHashMap<>();
    int sameKey = 0;
    for (Game g : byId.values()) {
      if (byKey.putIfAbsent(g.key(), g) != null) {
        sameKey++;
      }
    }
    Geometry geo = new Geometry(rows, cols);
    List<Item> items = new ArrayList<>();
    int bad = 0;
    int altOk = 0;
    for (Game g : byKey.values()) {
      Item it = new Item();
      it.g = g;
      it.r = replay(geo, g, skipsFree);
      altOk += replay(geo, g, !skipsFree).problem == null ? 1 : 0;
      events(it);
      items.add(it);
      if (it.r.problem != null) {
        bad++;
        System.out.printf(Locale.ROOT, "INCONSISTENT %s %s vs %s: %s%n", g.id, g.name[0], g.name[1], it.r.problem);
      }
    }
    System.out.printf(Locale.ROOT, "%d lines in %d files, %d unique ids, %d dropped as same players and moves, "
        + "%d games replayed, %d consistent, %d inconsistent%n", lines, files.size(), byId.size(), sameKey,
        items.size(), items.size() - bad, bad);
    System.out.printf(Locale.ROOT, "with skipped turns %s the cooldown: %d consistent%n",
        skipsFree ? "counting toward" : "not counting toward", altOk);
    if (report != null) {
      Path p = Path.of(report);
      if (p.getParent() != null) {
        Files.createDirectories(p.getParent());
      }
      Files.writeString(p, new Report(items, team, files, lines, byId.size(), sameKey, skipsFree, altOk).build(),
          StandardCharsets.UTF_8);
      System.out.println("report -> " + report);
    }
    if (openings != null) {
      writeOpenings(Path.of(openings), items, plies, rows, cols);
    }
  }

  // ---------------------------------------------------------------- parsing and replay

  @SuppressWarnings("unchecked")
  static Game game(String line) {
    Map<String, Object> m = (Map<String, Object>) new Json(line).value();
    List<Object> pl = (List<Object>) m.get("players");
    String[] name = new String[2];
    String[] team = new String[2];
    for (int i = 0; i < 2; i++) {
      Map<String, Object> p = (Map<String, Object>) pl.get(i);
      name[i] = (String) p.get("name");
      team[i] = (String) p.get("team");
    }
    Map<String, Object> log = (Map<String, Object>) m.get("log");
    List<Object> oomList = (List<Object>) log.get("oom");
    int[] oom = new int[oomList.size()];
    for (int i = 0; i < oom.length; i++) {
      oom[i] = ((Number) oomList.get(i)).intValue();
    }
    List<String> errors = new ArrayList<>();
    for (Object o : (List<Object>) m.get("errors")) {
      errors.add((String) o);
    }
    Number wm = (Number) log.get("warn_max");
    return new Game((String) m.get("id"), name, team, (String) m.get("moves"),
        "F".equals(m.get("winner")) ? 1 : 2, ((Number) m.get("win_at")).intValue(), (String) log.get("timeouts"),
        (String) log.get("full"), oom, ((Number) log.get("warn")).intValue(), wm == null ? -1 : wm.intValue(), errors);
  }

  /**
   * Replays the moves with per-side cooldown counters as in {@link FastGame}: every own turn, skipped ones too,
   * counts, a blast needs a counter of at least 10 and resets it. Stops at the first inconsistency.
   */
  static Replay replay(Geometry g, Game game, boolean skipsFree) {
    Replay r = new Replay();
    Board b = new Board(g);
    r.board = b;
    int[] counter = new int[3];
    int[] own = new int[3];
    String s = game.moves;
    int expected = 1;
    int turn = 0;
    int n = 0;
    int i = 0;
    while (i < s.length()) {
      if (i + 1 >= s.length() || (s.charAt(i) != 'F' && s.charAt(i) != 'S')
          || Character.digit(s.charAt(i + 1), 16) < 0) {
        r.problem = "bad token at char " + i;
        return r;
      }
      int side = s.charAt(i) == 'F' ? 1 : 2;
      int col = Character.digit(s.charAt(i + 1), 16);
      boolean star = i + 2 < s.length() && s.charAt(i + 2) == '*';
      i += star ? 3 : 2;
      r.lastToken = SIDE[side] + Integer.toHexString(col) + (star ? "*" : "");
      if (side != expected) {
        r.skips.add(new Skip(expected, turn, '?'));
        if (!skipsFree) {
          counter[expected]++;
        }
        own[expected]++;
        turn++;
        expected = side;
      }
      if (col >= g.cols) {
        r.problem = "column " + col + " outside the board at ply " + (n + 1);
        return r;
      }
      boolean win;
      if (star) {
        if (counter[side] < COOLDOWN) {
          r.problem = "blast at ply " + (n + 1) + " with cooldown counter " + counter[side];
          return r;
        }
        if (b.isFull(col)) {
          r.problem = "blast on a full column at ply " + (n + 1);
          return r;
        }
        win = b.blast(col);
        r.blastLog.add(new int[] {side, own[side], counter[side]});
        r.blasts[side]++;
        counter[side] = 0;
      } else {
        if (b.isFull(col)) {
          r.problem = "disc into a full column at ply " + (n + 1);
          return r;
        }
        win = b.make(col, side);
      }
      counter[side]++;
      own[side]++;
      turn++;
      n++;
      expected = 3 - side;
      if (win) {
        r.reason = star ? "blast" : "six";
        r.winner = side;
        if (i < s.length()) {
          r.problem = "line at ply " + n + " but more moves follow";
          return r;
        }
      }
    }
    r.plies = n;
    r.turns = turn;
    if (r.winner == 0) {
      r.problem = "no line after the last move (" + n + " plies), recorded winner " + SIDE[game.winner];
    } else if (r.winner != game.winner) {
      r.problem = "replay winner " + SIDE[r.winner] + " but recorded " + SIDE[game.winner];
    } else if (game.winAt != n - 1) {
      r.problem = "win at ply index " + (n - 1) + " but recorded win_at " + game.winAt;
    }
    return r;
  }

  /** Reads the timestamped timeout, full-column and OOM lines and pairs them with the replay's skipped turns. */
  static void events(Item it) {
    for (String e : it.g.errors) {
      long ms = e.length() > 24 && Character.isDigit(e.charAt(0)) ? Instant.parse(e.substring(0, e.indexOf(' ')))
          .toEpochMilli() : -1;
      int side = e.endsWith("FIRST_PLAYER") ? 1 : e.endsWith("SECOND_PLAYER") ? 2 : 0;
      if (e.contains("Operation timed out")) {
        it.events.add(new Event('T', side, ms));
      } else if (e.contains("Column is full")) {
        it.events.add(new Event('C', side, ms));
      } else if (e.contains("Memory limit exceeded:")) {
        it.oomMs = ms;
        it.oomMb = Integer.parseInt(e.replaceAll(".*exceeded: (\\d+)MB.*", "$1"));
      }
    }
    List<Skip> sk = it.r.skips;
    if (sk.size() == it.events.size()) {
      boolean same = true;
      for (int i = 0; i < sk.size(); i++) {
        same &= sk.get(i).side == it.events.get(i).side;
      }
      if (same) {
        it.matched = true;
        for (int i = 0; i < sk.size(); i++) {
          sk.set(i, new Skip(sk.get(i).side, sk.get(i).turn, it.events.get(i).kind));
        }
      }
    }
  }

  // ---------------------------------------------------------------- openings

  /** First plies of each unique game, cut at the first skipped turn or blast, which the file format cannot hold. */
  static void writeOpenings(Path out, List<Item> items, int plies, int rows, int cols) throws IOException {
    Set<String> lines = new LinkedHashSet<>();
    int cut = 0;
    for (Item it : items) {
      if (it.r.problem != null) {
        continue;
      }
      String s = it.g.moves;
      StringBuilder sb = new StringBuilder();
      int expected = 1;
      int n = 0;
      for (int i = 0; i < s.length() && n < plies; ) {
        int side = s.charAt(i) == 'F' ? 1 : 2;
        boolean star = i + 2 < s.length() && s.charAt(i + 2) == '*';
        if (side != expected || star) {
          cut++;
          break;
        }
        sb.append(n == 0 ? "" : ",").append(Character.digit(s.charAt(i + 1), 16));
        i += star ? 3 : 2;
        expected = 3 - side;
        n++;
      }
      if (n >= 2) {
        lines.add(sb.toString());
      }
    }
    Files.createDirectories(out.toAbsolutePath().getParent());
    List<String> all = new ArrayList<>();
    all.add(String.format(Locale.ROOT, "# %dx%d server-game openings: first %d plies of %d games, %d cut at a skipped "
        + "turn or blast", rows, cols, plies, items.size(), cut));
    all.addAll(lines);
    Files.write(out, all, StandardCharsets.UTF_8);
    System.out.printf(Locale.ROOT, "%d distinct openings (%d lines cut short) -> %s%n", lines.size(), cut, out);
  }

  // ---------------------------------------------------------------- report

  /** Builds the markdown report. */
  static final class Report {
    final List<Item> items;
    final String team;
    final List<String> files;
    final int lines;
    final int ids;
    final int sameKey;
    final boolean skipsFree;
    final int altOk;
    final Map<String, Bot> bots = new TreeMap<>();
    final StringBuilder sb = new StringBuilder();

    Report(List<Item> items, String team, List<String> files, int lines, int ids, int sameKey, boolean skipsFree,
        int altOk) {
      this.items = items;
      this.team = team;
      this.files = files;
      this.lines = lines;
      this.ids = ids;
      this.sameKey = sameKey;
      this.skipsFree = skipsFree;
      this.altOk = altOk;
    }

    Bot bot(String name) {
      return bots.computeIfAbsent(name, k -> {
        Bot b = new Bot();
        b.name = k;
        return b;
      });
    }

    boolean ours(String name, Game g) {
      return team.equals(g.team[g.name[0].equals(name) ? 0 : 1]);
    }

    String build() {
      tally();
      sb.append("# Server games report\n\n");
      sb.append(String.format(Locale.ROOT, "%d unique games (from %d lines in %d export file(s), %d unique ids, %d "
          + "dropped as same players and moves). Draws are not stored by the server, so every game here is decided "
          + "and the W/L tables contain no draws. Plies are recorded moves (blasts included, skipped turns not); "
          + "turns also count skipped turns.%n%n", items.size(), lines, files.size(), ids, sameKey));
      replaySection();
      botSection();
      lengthSection();
      advantageSection();
      matrixSection();
      oursSection();
      faultSection();
      clockSection();
      return sb.toString();
    }

    void tally() {
      for (Item it : items) {
        Game g = it.g;
        Replay r = it.r;
        int[] to = new int[3];
        for (char c : g.timeouts.toCharArray()) {
          to[c == 'F' ? 1 : 2]++;
        }
        int[] fu = new int[3];
        for (char c : g.full.toCharArray()) {
          fu[c == 'F' ? 1 : 2]++;
        }
        boolean oom = g.oom.length > 0;
        for (int side = 1; side <= 2; side++) {
          Bot b = bot(g.name[side - 1]);
          b.games++;
          if (side == 1) {
            b.firstGames++;
          } else {
            b.secondGames++;
          }
          boolean won = g.winner == side;
          if (won) {
            b.wins++;
            if (side == 1) {
              b.firstWins++;
            } else {
              b.secondWins++;
            }
            if (r.reason.equals("blast")) {
              b.blastWins++;
            } else {
              b.sixWins++;
            }
          }
          if (r.reason.equals("blast")) {
            b.blastGames++;
          } else {
            b.six++;
          }
          b.blastsUsed += r.blasts[side];
          b.timeouts += to[side];
          b.fulls += fu[side];
          if (to[side] > 0) {
            b.timeoutGames++;
          }
          b.plies.add(r.plies);
          b.turns.add(r.turns);
          if (g.warn > 0) {
            b.warnGames++;
            b.warnMax.add(g.warnMax);
          }
          int cat = to[side] > 0 && to[3 - side] > 0 ? 2 : to[side] > 0 ? 0 : to[3 - side] > 0 ? 1 : 3;
          if (oom) {
            b.oomGames++;
            b.mb.add(g.oom[0]);
            b.oomTimeouts[cat]++;
            int next = 2;
            for (Event e : it.events) {
              if (it.oomMs >= 0 && e.ms >= it.oomMs && e.side != 0) {
                next = e.side == side ? 0 : 1;
                break;
              }
            }
            b.nextAfterOom[next]++;
          } else {
            b.plainTimeouts[cat]++;
          }
        }
      }
    }

    List<Bot> sorted() {
      List<Bot> l = new ArrayList<>(bots.values());
      l.sort((a, b) -> b.games != a.games ? b.games - a.games : a.name.compareTo(b.name));
      return l;
    }

    void replaySection() {
      int ok = 0;
      for (Item it : items) {
        ok += it.r.problem == null ? 1 : 0;
      }
      sb.append("## Notation and replay check\n\n");
      sb.append(String.format(Locale.ROOT, "Replay with the engine rules (gravity, colour-blind six, 3x3 blast, a "
          + "blast needs a cooldown counter of at least 10, every own turn%s counts toward it): **%d of %d games "
          + "consistent** (recorded winner is the replay winner, the line appears exactly at win_at, no earlier "
          + "line, no disc into a full column, no blast before the cooldown).%n%n",
          skipsFree ? ", skipped ones NOT," : ", skipped ones included,", ok, items.size()));
      for (Item it : items) {
        if (it.r.problem != null) {
          sb.append(String.format(Locale.ROOT, "- `%s` %s vs %s: %s%n", it.g.id, it.g.name[0], it.g.name[1],
              it.r.problem));
        }
      }
      int blasts = 0;
      int minCounter = Integer.MAX_VALUE;
      int minGap = Integer.MAX_VALUE;
      int firstMin = Integer.MAX_VALUE;
      int matched = 0;
      int skipTotal = 0;
      int starGames = 0;
      for (Item it : items) {
        blasts += it.r.blastLog.size();
        starGames += it.r.blastLog.isEmpty() ? 0 : 1;
        int[] last = new int[3];
        boolean[] seen = new boolean[3];
        for (int[] b : it.r.blastLog) {
          minCounter = Math.min(minCounter, b[2]);
          if (seen[b[0]]) {
            minGap = Math.min(minGap, b[1] - last[b[0]]);
          } else {
            firstMin = Math.min(firstMin, b[1]);
          }
          seen[b[0]] = true;
          last[b[0]] = b[1];
        }
        matched += it.matched ? 1 : 0;
        skipTotal += it.r.skips.size();
      }
      sb.append(String.format(Locale.ROOT, "- `*` after a token marks a blast in that column: %d blasts in %d games; "
          + "smallest cooldown counter at a blast %s; earliest blast is the own turn #%s (0-based, own turns "
          + "counted from 0); smallest gap between two blasts of one side %s own turns. Replaying with skipped "
          + "turns %s counting toward the cooldown gives %d of %d consistent, so the data cannot tell the two "
          + "apart.%n", blasts, starGames,
          blasts == 0 ? "-" : minCounter, blasts == 0 ? "-" : firstMin, minGap == Integer.MAX_VALUE ? "-" : minGap,
          skipsFree ? "" : "NOT", altOk, items.size()));
      sb.append(String.format(Locale.ROOT, "- A skipped turn (timeout or full column) leaves no token; replay finds "
          + "%d of them as breaks in the F/S alternation. In %d of %d games the breaks equal the logged timeout / "
          + "column-full lines in count and sides (a break is invisible when both sides skip in a row).%n%n",
          skipTotal, matched, items.size()));
    }

    void botSection() {
      sb.append("## Per bot\n\n");
      sb.append("Decided by: games of this bot ending in a normal six / in a blast (either side winning). Blasts "
          + "used counts every `*` token by this bot. Length is plies per game.\n\n");
      List<String[]> rows = new ArrayList<>();
      for (Bot b : sorted()) {
        rows.add(new String[] {name(b.name), "" + b.games, b.wins + "/" + (b.games - b.wins),
            b.firstWins + "/" + (b.firstGames - b.firstWins), b.secondWins + "/" + (b.secondGames - b.secondWins),
            b.six + " / " + b.blastGames, b.sixWins + " / " + b.blastWins, "" + b.blastsUsed,
            f1(mean(b.plies)), f1(median(b.plies))});
      }
      table(new String[] {"Bot", "Games", "W/L", "As F W/L", "As S W/L", "Six / blast games", "Its wins six / blast",
          "Blasts used", "Mean plies", "Median plies"}, rows);
    }

    void lengthSection() {
      sb.append("## Game length\n\n");
      List<Integer> all = new ArrayList<>();
      List<Integer> allTurns = new ArrayList<>();
      int[] over = new int[3];
      int[] overTurns = new int[3];
      int blast = 0;
      for (Item it : items) {
        all.add(it.r.plies);
        allTurns.add(it.r.turns);
        blast += it.r.reason.equals("blast") ? 1 : 0;
        int[] lim = {200, 300, 400};
        for (int i = 0; i < 3; i++) {
          over[i] += it.r.plies > lim[i] ? 1 : 0;
          overTurns[i] += it.r.turns > lim[i] ? 1 : 0;
        }
      }
      sb.append(String.format(Locale.ROOT, "All games: plies min/median/p90/max = %s, mean %s; turns (skips included) "
          + "%s. Games ending by a blast: %d of %d (%s). Games over 200/300/400 plies: %d/%d/%d (over 200/300/400 "
          + "turns: %d/%d/%d).%n%n", spread(all), f1(mean(all)), spread(allTurns), blast, items.size(),
          pct(blast, items.size()), over[0], over[1], over[2], overTurns[0], overTurns[1], overTurns[2]));
      sb.append("Plies in 20-ply buckets (all games):\n\n");
      int max = Collections.max(all);
      List<String[]> rows = new ArrayList<>();
      for (int lo = 0; lo <= max; lo += 20) {
        int c = 0;
        for (int p : all) {
          c += p > lo && p <= lo + 20 || (lo == 0 && p == 0) ? 1 : 0;
        }
        rows.add(new String[] {(lo + 1) + "-" + (lo + 20), "" + c, "#".repeat((int) Math.ceil(c * 40.0 / items.size()
            * 2))});
      }
      table(new String[] {"Plies", "Games", ""}, rows);
      sb.append("Per bot (plies; the over-N columns count games with more than N plies):\n\n");
      rows = new ArrayList<>();
      for (Bot b : sorted()) {
        int[] o = new int[3];
        for (int p : b.plies) {
          o[0] += p > 200 ? 1 : 0;
          o[1] += p > 300 ? 1 : 0;
          o[2] += p > 400 ? 1 : 0;
        }
        rows.add(new String[] {name(b.name), "" + b.games, spread(b.plies), "" + o[0], "" + o[1], "" + o[2]});
      }
      table(new String[] {"Bot", "Games", "min/median/p90/max", "> 200", "> 300", "> 400"}, rows);
      List<Integer> ours = new ArrayList<>();
      int[] o = new int[3];
      for (Item it : items) {
        for (int s = 0; s < 2; s++) {
          if (team.equals(it.g.team[s])) {
            ours.add(it.r.plies);
            o[0] += it.r.plies > 200 ? 1 : 0;
            o[1] += it.r.plies > 300 ? 1 : 0;
            o[2] += it.r.plies > 400 ? 1 : 0;
          }
        }
      }
      if (!ours.isEmpty()) {
        sb.append(String.format(Locale.ROOT, "Our team (%s): %d games, plies min/median/p90/max = %s, over "
            + "200/300/400 plies: %d/%d/%d.%n%n", team, ours.size(), spread(ours), o[0], o[1], o[2]));
      }
    }

    void advantageSection() {
      int f = 0;
      int fSix = 0;
      int sSix = 0;
      int fBlast = 0;
      int sBlast = 0;
      int clean = 0;
      int cleanF = 0;
      for (Item it : items) {
        boolean first = it.g.winner == 1;
        f += first ? 1 : 0;
        if (it.r.reason.equals("blast")) {
          fBlast += first ? 1 : 0;
          sBlast += first ? 0 : 1;
        } else {
          fSix += first ? 1 : 0;
          sSix += first ? 0 : 1;
        }
        if (it.g.timeouts.isEmpty() && it.g.full.isEmpty()) {
          clean++;
          cleanF += first ? 1 : 0;
        }
      }
      int n = items.size();
      double p = (double) f / n;
      double se = Math.sqrt(p * (1 - p) / n);
      sb.append("## First-player advantage\n\n");
      sb.append(String.format(Locale.ROOT, "First player (F) won %d of %d (%s, normal-approximation 95%% interval "
          + "%.1f-%.1f%%). By a six: F %d, S %d; by a blast: F %d, S %d. In games without any timeout or "
          + "full-column event: F %d of %d (%s). Bots differ in strength, so this mixes colour with pairing; the "
          + "pairings are colour-balanced only if each ordered pair is played equally often.%n%n", f, n, pct(f, n),
          100 * (p - 1.96 * se), 100 * (p + 1.96 * se), fSix, sSix, fBlast, sBlast, cleanF, clean, pct(cleanF, clean)));
    }

    void matrixSection() {
      List<Bot> order = sorted();
      sb.append("## Head to head\n\nCell = wins of the row bot over the column bot, as wins-losses (all colours).\n\n");
      String[] head = new String[order.size() + 2];
      head[0] = "#";
      head[1] = "Bot";
      for (int i = 0; i < order.size(); i++) {
        head[i + 2] = "" + (i + 1);
      }
      List<String[]> rows = new ArrayList<>();
      for (int i = 0; i < order.size(); i++) {
        String[] row = new String[order.size() + 2];
        row[0] = "" + (i + 1);
        row[1] = name(order.get(i).name);
        for (int j = 0; j < order.size(); j++) {
          int w = 0;
          int l = 0;
          for (Item it : items) {
            String a = it.g.name[0];
            String b = it.g.name[1];
            String ni = order.get(i).name;
            String nj = order.get(j).name;
            if (a.equals(ni) && b.equals(nj)) {
              w += it.g.winner == 1 ? 1 : 0;
              l += it.g.winner == 2 ? 1 : 0;
            } else if (b.equals(ni) && a.equals(nj)) {
              w += it.g.winner == 2 ? 1 : 0;
              l += it.g.winner == 1 ? 1 : 0;
            }
          }
          row[j + 2] = i == j ? "." : w + l == 0 ? "" : w + "-" + l;
        }
        rows.add(row);
      }
      table(head, rows);
    }

    void oursSection() {
      sb.append("## Our team: ").append(team).append("\n\n");
      List<String[]> rows = new ArrayList<>();
      List<Item> losses = new ArrayList<>();
      List<String> faults = new ArrayList<>();
      int w = 0;
      int l = 0;
      int n = 0;
      for (Item it : items) {
        Game g = it.g;
        int me = team.equals(g.team[0]) ? 1 : team.equals(g.team[1]) ? 2 : 0;
        if (me == 0) {
          continue;
        }
        n++;
        boolean won = g.winner == me;
        w += won ? 1 : 0;
        l += won ? 0 : 1;
        if (!won) {
          losses.add(it);
        }
        for (int k = 0; k < it.events.size(); k++) {
          Event e = it.events.get(k);
          if (e.side == me) {
            faults.add(String.format(Locale.ROOT, "- Game #%d vs %s: our %s line%s; OOM line %s.", n, g.name[2 - me],
                e.kind == 'T' ? "timeout" : "full-column", it.matched ? " at turn " + (it.r.skips.get(k).turn + 1) : "",
                it.oomMs < 0 ? "absent" : it.oomMb + " MB, " + Math.abs(e.ms - it.oomMs) + " ms "
                + (e.ms >= it.oomMs ? "before" : "after") + " the timeout"));
          }
        }
        StringBuilder mine = new StringBuilder();
        int theirs = 0;
        for (Skip s : it.r.skips) {
          if (s.side == me) {
            mine.append(mine.length() == 0 ? "" : ", ").append("turn ").append(s.turn + 1)
                .append(s.kind == '?' ? "" : " (" + (s.kind == 'T' ? "timeout" : "full col") + ")");
          } else {
            theirs++;
          }
        }
        rows.add(new String[] {"" + n, name(g.name[2 - me]) + " (" + g.name[me - 1] + ")", SIDE[me],
            won ? "W" : "L", "" + it.r.plies, "" + it.r.turns, it.r.reason, "" + it.r.blasts[me] + "/"
            + it.r.blasts[3 - me], mine.length() == 0 ? "-" : mine.toString(), "" + theirs, g.oom.length > 0
            ? g.oom[0] + " MB" : "-"});
      }
      sb.append(String.format(Locale.ROOT, "%d games, %d wins, %d losses. Opponent column shows our bot's version in "
          + "brackets; blasts are ours/theirs; our skipped turns are numbered by turn (skipped turns included, "
          + "1-based) and, when the log pairs up, their cause; the last column is the OOM line of the game, if any "
          + "(not tagged with a bot).%n%n", n, w, l));
      table(new String[] {"#", "Opponent (our version)", "Side", "Res", "Plies", "Turns", "Decided by", "Blasts",
          "Our skipped turns", "Opp skips", "OOM line"}, rows);
      if (!faults.isEmpty()) {
        sb.append("Our own timeout lines and the OOM line of the same game:\n\n");
        faults.forEach(f -> sb.append(f).append('\n'));
        sb.append('\n');
      }
      for (Item it : losses) {
        Game g = it.g;
        int me = team.equals(g.team[0]) ? 1 : 2;
        sb.append(String.format(Locale.ROOT, "### Loss: %s (%s) vs %s (%s)%n%n", g.name[me - 1], SIDE[me],
            g.name[2 - me], SIDE[3 - me]));
        sb.append(String.format(Locale.ROOT, "Match id `%s`. Decided at ply %d (turn %d, last token `%s`, by a %s, "
            + "win_at %d). Skipped turns: %s.%n%n", g.id, it.r.plies, it.r.turns, it.r.lastToken, it.r.reason,
            g.winAt, it.r.skips.isEmpty() ? "none" : it.r.skips.toString()));
        sb.append("Moves:\n\n```\n").append(g.moves).append("\n```\n\nPosition after the deciding move (top row "
            + "first, F = X, S = O):\n\n```\n").append(ascii(it.r.board)).append("```\n\n");
      }
    }

    void faultSection() {
      sb.append("## Timeouts and out-of-memory\n\n");
      sb.append("Timeouts are the per-side letters of the log (an own timeout also gives an 'Invalid column "
          + "selected: -1' line). OOM lines (`Memory limit exceeded: N MB used`, then an uncaught "
          + "SecurityException) are not tagged with a side or bot, at most one per game. Warn = games with "
          + "memory-warning lines in the log (both bots).\n\n");
      List<String[]> rows = new ArrayList<>();
      int games = items.size();
      int oomGames = 0;
      for (Item it : items) {
        oomGames += it.g.oom.length > 0 ? 1 : 0;
      }
      for (Bot b : sorted()) {
        rows.add(new String[] {name(b.name), "" + b.games, "" + b.timeouts, "" + b.timeoutGames, "" + b.fulls,
            b.oomGames + " (" + pct(b.oomGames, b.games) + ")", b.mb.isEmpty() ? "-" : spread(b.mb),
            b.warnGames + (b.warnMax.isEmpty() ? "" : " (max " + Collections.max(b.warnMax) + " MB)")});
      }
      table(new String[] {"Bot", "Games", "Own timeouts", "Games with own timeout", "Own full-column turns",
          "Games with an OOM line", "OOM MB min/median/p90/max", "Games with warn lines"}, rows);
      sb.append(String.format(Locale.ROOT, "%d of %d games (%s) contain an OOM line.%n%n", oomGames, games,
          pct(oomGames, games)));
      sb.append("Which side timed out in games with / without an OOM line, seen from each bot "
          + "(own = only this bot's side, opp = only its opponent's, both, none):\n\n");
      rows = new ArrayList<>();
      for (Bot b : sorted()) {
        rows.add(new String[] {name(b.name), cat(b.oomTimeouts), cat(b.plainTimeouts)});
      }
      table(new String[] {"Bot", "OOM games: own / opp / both / none", "Other games: own / opp / both / none"}, rows);
      sb.append("First timeout or full-column line at or after the OOM line, by whose side it hit "
          + "(own / opp / no later event):\n\n");
      rows = new ArrayList<>();
      List<Integer> gaps = new ArrayList<>();
      for (Item it : items) {
        if (it.oomMs >= 0) {
          for (Event e : it.events) {
            if (e.ms >= it.oomMs && e.side != 0) {
              gaps.add((int) (e.ms - it.oomMs));
              break;
            }
          }
        }
      }
      for (Bot b : sorted()) {
        rows.add(new String[] {name(b.name), b.nextAfterOom[0] + " / " + b.nextAfterOom[1] + " / "
            + b.nextAfterOom[2]});
      }
      table(new String[] {"Bot", "own / opp / none"}, rows);
      sb.append(String.format(Locale.ROOT, "Games with an OOM line followed by a later timeout line: %d of %d; delay "
          + "from the OOM line to it, ms min/median/p90/max = %s.%n%n", gaps.size(), oomGames, spread(gaps)));
      int before = 0;
      for (Item it : items) {
        for (Event e : it.events) {
          if (it.oomMs >= 0 && e.ms < it.oomMs) {
            before++;
            break;
          }
        }
      }
      sb.append(String.format(Locale.ROOT, "Games where a timeout line precedes the OOM line: %d.%n%n", before));
      sb.append("OOM games / games for every pair of bots (both colours); a bot that exhausts memory shows up as a "
          + "column and row of high shares:\n\n");
      List<Bot> order = sorted();
      String[] head = new String[order.size() + 2];
      head[0] = "#";
      head[1] = "Bot";
      for (int i = 0; i < order.size(); i++) {
        head[i + 2] = "" + (i + 1);
      }
      rows = new ArrayList<>();
      for (int i = 0; i < order.size(); i++) {
        String[] row = new String[order.size() + 2];
        row[0] = "" + (i + 1);
        row[1] = name(order.get(i).name);
        for (int j = 0; j < order.size(); j++) {
          int o = 0;
          int c = 0;
          for (Item it : items) {
            String a = it.g.name[0];
            String b = it.g.name[1];
            if (a.equals(order.get(i).name) && b.equals(order.get(j).name)
                || b.equals(order.get(i).name) && a.equals(order.get(j).name)) {
              c++;
              o += it.g.oom.length > 0 ? 1 : 0;
            }
          }
          row[j + 2] = i == j ? "." : c == 0 ? "" : o + "/" + c;
        }
        rows.add(row);
      }
      table(head, rows);
      sb.append("Median OOM size in MB for every pair of bots (the figure is the memory in use when the limit "
          + "tripped; abrupt jumps give large values, slow growth gives values just above 200):\n\n");
      rows = new ArrayList<>();
      for (int i = 0; i < order.size(); i++) {
        String[] row = new String[order.size() + 2];
        row[0] = "" + (i + 1);
        row[1] = name(order.get(i).name);
        for (int j = 0; j < order.size(); j++) {
          List<Integer> v = new ArrayList<>();
          for (Item it : items) {
            String a = it.g.name[0];
            String b = it.g.name[1];
            if (it.g.oom.length > 0 && (a.equals(order.get(i).name) && b.equals(order.get(j).name)
                || b.equals(order.get(i).name) && a.equals(order.get(j).name))) {
              v.add(it.g.oom[0]);
            }
          }
          row[j + 2] = i == j || v.isEmpty() ? "" : f1(median(v)).replace(".0", "");
        }
        rows.add(row);
      }
      table(head, rows);
    }

    void clockSection() {
      sb.append("## Wall-clock evidence\n\n");
      int stamped = 0;
      for (Item it : items) {
        for (String e : it.g.errors) {
          stamped += Character.isDigit(e.isEmpty() ? ' ' : e.charAt(0)) ? 1 : 0;
        }
      }
      sb.append(String.format(Locale.ROOT, "The exported log lines are only ERROR lines (OOM, timeout, invalid column, "
          + "full column); they carry millisecond timestamps (%d lines) but there is no per-move or start/end line, "
          + "so a game's duration can only be bracketed from its first to its last timestamped event.%n%n", stamped));
      List<Integer> spans = new ArrayList<>();
      List<Integer> spanPlies = new ArrayList<>();
      List<Integer> perTurn = new ArrayList<>();
      long dt = 0;
      long dTurns = 0;
      int intervals = 0;
      int gamesWithIntervals = 0;
      for (Item it : items) {
        if (!it.matched || it.events.size() < 2) {
          continue;
        }
        gamesWithIntervals++;
        Event first = it.events.get(0);
        Event last = it.events.get(it.events.size() - 1);
        List<Skip> sk = it.r.skips;
        spans.add((int) (last.ms - first.ms));
        spanPlies.add(sk.get(sk.size() - 1).turn - sk.get(0).turn);
        for (int i = 1; i < it.events.size(); i++) {
          long d = it.events.get(i).ms - it.events.get(i - 1).ms;
          int turns = sk.get(i).turn - sk.get(i - 1).turn;
          if (turns > 0) {
            intervals++;
            dt += d;
            dTurns += turns;
            perTurn.add((int) (d / turns));
          }
        }
      }
      if (intervals == 0) {
        sb.append("No game has two paired timeout lines, so no time per move can be estimated.\n\n");
        return;
      }
      sb.append(String.format(Locale.ROOT, "%d games have two or more paired timeout lines (log events match the "
          + "skipped turns found by the replay). Between consecutive such events (%d intervals, %d turns): time per "
          + "turn in ms, min/median/p90/max = %s, pooled mean %.0f ms per turn (%.1f s over %d turns). "
          + "First-to-last event span per game, ms min/median/p90/max = %s, covering turns %s. Consecutive "
          + "timeouts can be logged only a few ms apart although each is reported as exceeding 100 ms, so the log "
          + "timestamps are not a reliable clock for single moves; they give an order of magnitude only (games "
          + "of this many turns are logged within about a second, far below 30 s).%n%n", gamesWithIntervals,
          intervals, dTurns, spread(perTurn), (double) dt / dTurns, dt / 1000.0, dTurns, spread(spans),
          spread(spanPlies)));
    }

    String cat(int[] c) {
      return c[0] + " / " + c[1] + " / " + c[2] + " / " + c[3];
    }

    String name(String n) {
      return n.replace("|", "/");
    }

    void table(String[] head, List<String[]> rows) {
      sb.append("| ").append(String.join(" | ", head)).append(" |\n|");
      for (int i = 0; i < head.length; i++) {
        sb.append("---|");
      }
      sb.append('\n');
      for (String[] r : rows) {
        sb.append("| ").append(String.join(" | ", r)).append(" |\n");
      }
      sb.append('\n');
    }
  }

  static String ascii(Board b) {
    StringBuilder sb = new StringBuilder();
    for (int y = b.g.rows - 1; y >= 0; y--) {
      for (int c = 0; c < b.g.cols; c++) {
        int v = b.cell(c, y);
        sb.append(v == 0 ? '.' : v == 1 ? 'X' : 'O');
      }
      sb.append('\n');
    }
    for (int c = 0; c < b.g.cols; c++) {
      sb.append(Integer.toHexString(c));
    }
    return sb.append('\n').toString();
  }

  static double mean(List<Integer> v) {
    return v.stream().mapToInt(Integer::intValue).average().orElse(0);
  }

  static double median(List<Integer> v) {
    List<Integer> s = new ArrayList<>(v);
    Collections.sort(s);
    int n = s.size();
    return n == 0 ? 0 : n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0;
  }

  /** "min/median/p90/max" with p90 by nearest rank. */
  static String spread(List<Integer> v) {
    if (v.isEmpty()) {
      return "-";
    }
    List<Integer> s = new ArrayList<>(v);
    Collections.sort(s);
    int p90 = s.get((int) Math.ceil(0.9 * s.size()) - 1);
    return s.get(0) + "/" + f1(median(v)).replace(".0", "") + "/" + p90 + "/" + s.get(s.size() - 1);
  }

  static String f1(double d) {
    return String.format(Locale.ROOT, "%.1f", d);
  }

  static String pct(int a, int b) {
    return b == 0 ? "-" : String.format(Locale.ROOT, "%.1f%%", 100.0 * a / b);
  }

  /** Minimal JSON reader for the export lines: objects, arrays, strings with escapes, numbers, literals. */
  static final class Json {
    private final String s;
    private int p;

    Json(String s) {
      this.s = s;
    }

    Object value() {
      ws();
      char c = s.charAt(p);
      if (c == '{') {
        Map<String, Object> m = new LinkedHashMap<>();
        p++;
        ws();
        if (s.charAt(p) == '}') {
          p++;
          return m;
        }
        while (true) {
          ws();
          String k = string();
          ws();
          expect(':');
          m.put(k, value());
          ws();
          if (s.charAt(p++) == '}') {
            return m;
          }
        }
      }
      if (c == '[') {
        List<Object> l = new ArrayList<>();
        p++;
        ws();
        if (s.charAt(p) == ']') {
          p++;
          return l;
        }
        while (true) {
          l.add(value());
          ws();
          if (s.charAt(p++) == ']') {
            return l;
          }
        }
      }
      if (c == '"') {
        return string();
      }
      if (s.startsWith("null", p)) {
        p += 4;
        return null;
      }
      if (s.startsWith("true", p)) {
        p += 4;
        return Boolean.TRUE;
      }
      if (s.startsWith("false", p)) {
        p += 5;
        return Boolean.FALSE;
      }
      int q = p;
      while (p < s.length() && "+-0123456789.eE".indexOf(s.charAt(p)) >= 0) {
        p++;
      }
      String num = s.substring(q, p);
      return num.contains(".") || num.contains("e") || num.contains("E") ? (Number) Double.parseDouble(num)
          : (Number) Long.parseLong(num);
    }

    private String string() {
      expect('"');
      StringBuilder sb = new StringBuilder();
      while (s.charAt(p) != '"') {
        char c = s.charAt(p++);
        if (c != '\\') {
          sb.append(c);
          continue;
        }
        char e = s.charAt(p++);
        switch (e) {
          case 'n' -> sb.append('\n');
          case 't' -> sb.append('\t');
          case 'r' -> sb.append('\r');
          case 'b' -> sb.append('\b');
          case 'f' -> sb.append('\f');
          case 'u' -> {
            sb.append((char) Integer.parseInt(s.substring(p, p + 4), 16));
            p += 4;
          }
          default -> sb.append(e);
        }
      }
      p++;
      return sb.toString();
    }

    private void ws() {
      while (p < s.length() && Character.isWhitespace(s.charAt(p))) {
        p++;
      }
    }

    private void expect(char c) {
      if (s.charAt(p++) != c) {
        throw new IllegalArgumentException("expected " + c + " at " + (p - 1));
      }
    }
  }

  private ServerGames() {}
}
