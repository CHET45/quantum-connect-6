package ch.qc.bot;

/**
 * The bot's time plan: the stop of the first call by own turn (lower while the code is cold), what happens after a
 * sign of trouble, the search in the second call and the warm-up in the constructor. {@link #DEFAULT} is what ships;
 * harness experiments build others from a spec string with {@link #parse}. All times here are their values at a call
 * limit of 100 ms; {@link CallLimit#scaled} turns them into times at the limit the bot measured.
 */
public final class Timing {
  private static final long MS = 1_000_000L;

  /**
   * One-line switch: real search in the constructor until it has taken this long, 0 for none. Whether the server times
   * the constructor is unknown (about 200 ms is known to work). Without a warm-up the code stays cold for longer, so the
   * plan below has lower stops for longer and a later second-call search.
   */
  private static final long CONSTRUCTOR_WARMUP_NANOS = 1000 * MS;

  /** A board dimension above this is modelled only around the discs ({@link Crop}). */
  private static final int CROP_ABOVE = 40;

  /**
   * One-line switch for a cropped dimension ({@link Crop}): 0 contiguous, 1 leaves out empty stretches between groups of
   * discs, 2 also leaves out far groups too small to make a line soon.
   */
  private static final int CROP_GAPS = 2;

  /**
   * One-line switch: the first-call stop after one and after two own lost turns in the game (0 for no cap). A lost turn on
   * a slow server comes from time the bot cannot see (its thread starts late or is frozen), so the remembered stall fades
   * too fast to keep the next one away.
   */
  private static final long LOST_CAP_ONE = 0L;
  private static final long LOST_CAP_TWO = 0L;

  /**
   * One-line switch: on a board wider than {@link #CROP_ABOVE}, this many quiet own turns early in the game drop a disc
   * far from all others, near an edge. A bot that models one stretch from the leftmost to the rightmost disc then has to
   * search a much wider board; this bot's own gap crop leaves such single discs out. 0 for none.
   */
  private static final int WIDE_TURNS = 0;

  /**
   * One-line switch: a root move after which a lost turn of the opponent hands us the game gets that chance added to its
   * score (Searcher#setPassChance). 0 off, 1 a move that leaves us a playable win, 2 also a move after which one more own
   * move would make an unstoppable double threat. The chance of a lost turn is the opponent's lost turns in this game
   * plus PASS_PRIOR_LOST, over its turns plus PASS_PRIOR_TURNS. A double threat counts as PASS_DOUBLE percent of a win.
   */
  private static final int PASS_MODE = 0;
  private static final int PASS_PRIOR_LOST = 2;
  private static final int PASS_PRIOR_TURNS = 50;
  private static final int PASS_DOUBLE = 85;

  /**
   * One-line switch (organisers allowed it 2026-11-05): how long the second-call search may run, measured from the
   * entry of the second call. The engine only interrupts the second call at the limit, so the thread runs on through the
   * opponent's turn; a value above the limit keeps our real search going on the opponent's time (ordinary pondering) and
   * fills the table deeper for our next turn. The next first call ends it at once (a new search bumps the Searcher token,
   * which every poll of the lingering search sees), so this is only the backstop for when no next call comes, e.g. at
   * the end of a game. 0 keeps the old within-turn budget. Scaled to the measured call limit like the other times here.
   *
   * <p>160 ms, not more: gated 2026-11-05, ps=160 vs rc8 scored 54.7% over 230 pairs (~+33 Elo, about 2 sigma),
   * strongest on 15x100 (57.5% over 120 pairs) and about +10 on 15x30 and 8x12, with no regression or crash. A single
   * 40-pair 15x100 run read +137 but an 80-pair rerun only +13, so the real gain is modest. ps=320 cost -57 Elo on
   * 15x30 (a long search there floods the 2^18 table with the pondered, usually-not-played line).
   */
  private static final long PONDER_SPAN_NANOS = 160 * MS;

  public static final Timing DEFAULT = CONSTRUCTOR_WARMUP_NANOS > 0
      ? new Timing(new int[] {10, Integer.MAX_VALUE}, new long[] {60 * MS, 80 * MS}, 80 * MS,
          CONSTRUCTOR_WARMUP_NANOS, 96 * MS, 85, 100, 10, false, 55 * MS, 60 * MS, CROP_ABOVE)
      : new Timing(new int[] {10, 20, 30, 60, Integer.MAX_VALUE}, new long[] {50 * MS, 60 * MS, 70 * MS, 75 * MS, 80 * MS},
          80 * MS, 0L, 96 * MS, 85, 100, 30, false, 55 * MS, 60 * MS, CROP_ABOVE);

  /** stopNanos[i] holds for own turn indices (0-based) below untilTurn[i], the first match wins. */
  final int[] untilTurn;
  final long[] stopNanos;
  /** Stop of the second-call search, measured from the entry of the second call; 0 for no such search. */
  final long ponderNanos;
  /** See {@link #PONDER_SPAN_NANOS}. Not a constructor parameter: it keeps the default unless {@link #parse} sets it. */
  long ponderSpanNanos = PONDER_SPAN_NANOS;
  final long warmupNanos;
  /** First calls aim to end this long after their start, less the stalls seen lately; 0 turns the sensor off. */
  final long stallLimitNanos;
  /** The remembered stall keeps this share, in percent, per own turn, and counts this much, in percent. */
  final int stallDecayPercent;
  final int stallWeightPercent;
  /** The second call does not search on own turns below this: the compiler threads get the CPU instead. */
  final int ponderFromTurn;
  /**
   * Find out from the second turn on whether the call limit is above 100 ms, by staying in the second call until the
   * engine gives up. That logs one timeout per game on a server at 100 ms, so it is off: only an opponent whose turns are
   * repeatedly and steadily longer than a limit of 100 ms allows then starts this probe. Below 100 ms the limit is found
   * either way.
   */
  final boolean probeUpward;
  /**
   * A special after a timed-out first call crashes the engine (design R10, R15; seen in night matches), and the bot
   * cannot see that timeout, only the time since it was called, which starts late if its thread started late. So a blast
   * is requested only if the first call took at most safeFinishNanos, less the stall remembered; a search with a blast in
   * the lead stops at blastStopNanos, less that stall, to stay inside it.
   */
  final long blastStopNanos;
  final long safeFinishNanos;
  /** See {@link Crop}. */
  final int cropAbove;
  /** See {@link #CROP_GAPS} and {@link Crop#refit}. */
  final int cropGaps;
  /** See {@link #LOST_CAP_ONE}. */
  final long lostCapOneNanos;
  final long lostCapTwoNanos;
  /** See {@link #WIDE_TURNS}. */
  final int wideTurns;
  /** See {@link #PASS_MODE}. */
  final int passMode;
  final int passPriorLost;
  final int passPriorTurns;
  final int passDouble;

  Timing(int[] untilTurn, long[] stopNanos, long ponderNanos, long warmupNanos, long stallLimitNanos,
      int stallDecayPercent, int stallWeightPercent, int ponderFromTurn, boolean probeUpward, long blastStopNanos,
      long safeFinishNanos, int cropAbove) {
    this(untilTurn, stopNanos, ponderNanos, warmupNanos, stallLimitNanos, stallDecayPercent, stallWeightPercent,
        ponderFromTurn, probeUpward, blastStopNanos, safeFinishNanos, cropAbove, CROP_GAPS, LOST_CAP_ONE, LOST_CAP_TWO,
        WIDE_TURNS, PASS_MODE, PASS_PRIOR_LOST, PASS_PRIOR_TURNS, PASS_DOUBLE);
  }

  Timing(int[] untilTurn, long[] stopNanos, long ponderNanos, long warmupNanos, long stallLimitNanos,
      int stallDecayPercent, int stallWeightPercent, int ponderFromTurn, boolean probeUpward, long blastStopNanos,
      long safeFinishNanos, int cropAbove, int cropGaps, long lostCapOneNanos, long lostCapTwoNanos, int wideTurns,
      int passMode, int passPriorLost, int passPriorTurns, int passDouble) {
    this.untilTurn = untilTurn;
    this.stopNanos = stopNanos;
    this.ponderNanos = ponderNanos;
    this.warmupNanos = warmupNanos;
    this.stallLimitNanos = stallLimitNanos;
    this.stallDecayPercent = stallDecayPercent;
    this.stallWeightPercent = stallWeightPercent;
    this.ponderFromTurn = ponderFromTurn;
    this.probeUpward = probeUpward;
    this.blastStopNanos = blastStopNanos;
    this.safeFinishNanos = safeFinishNanos;
    this.cropAbove = cropAbove;
    this.cropGaps = cropGaps;
    this.lostCapOneNanos = lostCapOneNanos;
    this.lostCapTwoNanos = lostCapTwoNanos;
    this.wideTurns = wideTurns;
    this.passMode = passMode;
    this.passPriorLost = passPriorLost;
    this.passPriorTurns = passPriorTurns;
    this.passDouble = passDouble;
  }

  /** Chance that the opponent loses its next turn, from its lost turns so far in this game; 0 when the switch is off. */
  double passChance(int oppLost, int oppTurns) {
    if (passMode <= 0 || oppTurns + passPriorTurns <= 0) {
      return 0;
    }
    return Math.min(0.5, (double) (oppLost + passPriorLost) / (oppTurns + passPriorTurns));
  }

  /** The cap on the first-call stop after this many own lost turns in the game; Long.MAX_VALUE for none. */
  long lostCap(int ownLost) {
    long cap = ownLost >= 2 && lostCapTwoNanos > 0 ? lostCapTwoNanos : ownLost >= 1 ? lostCapOneNanos : 0L;
    return cap > 0 ? cap : Long.MAX_VALUE;
  }

  /** Stop of the first call on this own turn (0-based). */
  long stop(int ownTurn) {
    for (int i = 0; i < untilTurn.length; i++) {
      if (ownTurn < untilTurn[i]) {
        return stopNanos[i];
      }
    }
    return stopNanos[stopNanos.length - 1];
  }

  /**
   * Harness only: "s=55@10/65@30/80,p=85,w=1000,g=96@85@100,up=1,b=70@75". s: first-call stop in ms, each until an own
   * turn, the last for all others. p: second-call search. w: warm-up. g=LIMIT@DECAY@WEIGHT: the stall sensor. pf=N: no
   * second-call search on own turns below N. up=1: {@link #probeUpward}. b=STOP@FINISH: blast limits. crop=N:
   * {@link #cropAbove}. gap=0|1|2: {@link #cropGaps}. lc=ONE@TWO: {@link #LOST_CAP_ONE} in ms. wide=N:
   * {@link #WIDE_TURNS}. pt=MODE@LOST@TURNS@DOUBLE: {@link #PASS_MODE} and its prior. ps=N: {@link #PONDER_SPAN_NANOS}
   * in ms (0 = the old within-turn budget). Anything not named keeps the value of {@link #DEFAULT}.
   */
  public static Timing parse(String spec) {
    Timing d = DEFAULT;
    int[] until = d.untilTurn;
    long[] stop = d.stopNanos;
    long ponder = d.ponderNanos;
    long warm = d.warmupNanos;
    long stall = d.stallLimitNanos;
    int decay = d.stallDecayPercent;
    int weight = d.stallWeightPercent;
    int ponderFrom = d.ponderFromTurn;
    boolean up = d.probeUpward;
    long blastStop = d.blastStopNanos;
    long safeFinish = d.safeFinishNanos;
    int cropAbove = d.cropAbove;
    int cropGaps = d.cropGaps;
    long lostOne = d.lostCapOneNanos;
    long lostTwo = d.lostCapTwoNanos;
    int wide = d.wideTurns;
    int passMode = d.passMode;
    int passLost = d.passPriorLost;
    int passTurns = d.passPriorTurns;
    int passDouble = d.passDouble;
    long span = d.ponderSpanNanos;
    for (String part : spec.split(",")) {
      int eq = part.indexOf('=');
      if (eq < 0) {
        continue;
      }
      String key = part.substring(0, eq);
      String value = part.substring(eq + 1);
      if (key.equals("s")) {
        String[] steps = value.split("/");
        until = new int[steps.length];
        stop = new long[steps.length];
        for (int i = 0; i < steps.length; i++) {
          int at = steps[i].indexOf('@');
          stop[i] = MS * Long.parseLong(at < 0 ? steps[i] : steps[i].substring(0, at));
          until[i] = at < 0 ? Integer.MAX_VALUE : Integer.parseInt(steps[i].substring(at + 1));
        }
      } else if (key.equals("up")) {
        up = value.equals("1");
      } else if (key.equals("p")) {
        ponder = MS * Long.parseLong(value);
      } else if (key.equals("w")) {
        warm = MS * Long.parseLong(value);
      } else if (key.equals("b")) {
        int at = value.indexOf('@');
        blastStop = MS * Long.parseLong(value.substring(0, at));
        safeFinish = MS * Long.parseLong(value.substring(at + 1));
      } else if (key.equals("crop")) {
        cropAbove = Integer.parseInt(value);
      } else if (key.equals("gap")) {
        cropGaps = Integer.parseInt(value);
      } else if (key.equals("wide")) {
        wide = Integer.parseInt(value);
      } else if (key.equals("lc")) {
        int at = value.indexOf('@');
        lostOne = MS * Long.parseLong(at < 0 ? value : value.substring(0, at));
        lostTwo = at < 0 ? lostOne : MS * Long.parseLong(value.substring(at + 1));
      } else if (key.equals("pt")) {
        String[] v = value.split("@");
        passMode = Integer.parseInt(v[0]);
        if (v.length > 2) {
          passLost = Integer.parseInt(v[1]);
          passTurns = Integer.parseInt(v[2]);
        }
        if (v.length > 3) {
          passDouble = Integer.parseInt(v[3]);
        }
      } else if (key.equals("ps")) {
        span = MS * Long.parseLong(value);
      } else if (key.equals("pf")) {
        ponderFrom = Integer.parseInt(value);
      } else if (key.equals("g")) {
        String[] g = value.split("@");
        stall = MS * Long.parseLong(g[0]);
        decay = Integer.parseInt(g[1]);
        weight = Integer.parseInt(g[2]);
      }
    }
    Timing t = new Timing(until, stop, ponder, warm, stall, decay, weight, ponderFrom, up, blastStop, safeFinish,
        cropAbove, cropGaps, lostOne, lostTwo, wide, passMode, passLost, passTurns, passDouble);
    t.ponderSpanNanos = span;
    return t;
  }
}
