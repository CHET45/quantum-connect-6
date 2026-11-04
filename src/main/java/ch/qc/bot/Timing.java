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

  public static final Timing DEFAULT = CONSTRUCTOR_WARMUP_NANOS > 0
      ? new Timing(new int[] {10, Integer.MAX_VALUE}, new long[] {70 * MS, 80 * MS}, 85 * MS,
          CONSTRUCTOR_WARMUP_NANOS, 96 * MS, 85, 100, 10, false, 70 * MS, 75 * MS)
      : new Timing(new int[] {10, 20, 30, 60, Integer.MAX_VALUE}, new long[] {50 * MS, 60 * MS, 70 * MS, 75 * MS, 80 * MS},
          85 * MS, 0L, 96 * MS, 85, 100, 30, false, 70 * MS, 75 * MS);

  /** stopNanos[i] holds for own turn indices (0-based) below untilTurn[i], the first match wins. */
  final int[] untilTurn;
  final long[] stopNanos;
  /** Stop of the second-call search, measured from the entry of the second call; 0 for no such search. */
  final long ponderNanos;
  final long warmupNanos;
  /** First calls aim to end this long after their start, less the stalls seen lately; 0 turns the sensor off. */
  final long stallLimitNanos;
  /** The remembered stall keeps this share, in percent, per own turn, and counts this much, in percent. */
  final int stallDecayPercent;
  final int stallWeightPercent;
  /** The second call does not search on own turns below this: the compiler threads get the CPU instead. */
  final int ponderFromTurn;
  /**
   * Find out from the first turn whether the call limit is above 100 ms, by staying in the second call until the engine
   * gives up. That logs one timeout per game on a server at 100 ms, so it is off: only an opponent whose turns are
   * longer than a limit of 100 ms allows then starts this probe. Below 100 ms the limit is found either way.
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

  Timing(int[] untilTurn, long[] stopNanos, long ponderNanos, long warmupNanos, long stallLimitNanos,
      int stallDecayPercent, int stallWeightPercent, int ponderFromTurn, boolean probeUpward, long blastStopNanos,
      long safeFinishNanos) {
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
   * second-call search on own turns below N. up=1: {@link #probeUpward}. b=STOP@FINISH: blast limits. Anything not named
   * keeps the value of {@link #DEFAULT}.
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
      } else if (key.equals("pf")) {
        ponderFrom = Integer.parseInt(value);
      } else if (key.equals("g")) {
        String[] g = value.split("@");
        stall = MS * Long.parseLong(g[0]);
        decay = Integer.parseInt(g[1]);
        weight = Integer.parseInt(g[2]);
      }
    }
    return new Timing(until, stop, ponder, warm, stall, decay, weight, ponderFrom, up, blastStop, safeFinish);
  }
}
