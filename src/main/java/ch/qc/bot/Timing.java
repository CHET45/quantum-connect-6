package ch.qc.bot;

/**
 * The bot's time plan: the stop of the first call by own turn (lower while the code is cold), what happens after a
 * sign of trouble, the search in the second call and the warm-up in the constructor. {@link #DEFAULT} is what ships;
 * harness experiments build others from a spec string with {@link #parse}.
 */
public final class Timing {
  private static final long MS = 1_000_000L;

  /** One-line switch: nanoseconds of real search in the constructor, 0 for none (the server may time it). */
  private static final long CONSTRUCTOR_WARMUP_NANOS = 0L;

  public static final Timing DEFAULT = new Timing(new int[] {Integer.MAX_VALUE}, new long[] {80 * MS}, 30 * MS, 0L,
      CONSTRUCTOR_WARMUP_NANOS, 0, 0L, 0L, false, 0L, 0, 0, 0, 0);

  /** stopNanos[i] holds for own turn indices (0-based) below untilTurn[i], the first match wins. */
  final int[] untilTurn;
  final long[] stopNanos;
  /** Stop on the first call of an instance at the latest: everything is cold there. */
  final long firstCallNanos;
  /** Stop of the second-call search, measured from the entry of the second call; 0 for no such search. */
  final long ponderNanos;
  final long warmupNanos;
  /** Own turns the stop stays at cutNanos after a lost turn or an overrun of overrunNanos; 0 turns this off. */
  final int cutTurns;
  final long cutNanos;
  final long overrunNanos;
  /** Play the centre on an empty board without searching. */
  final boolean centreOnEmpty;
  /** First calls aim to end this long after their start, less the stalls seen lately; 0 turns the sensor off. */
  final long stallLimitNanos;
  /** The remembered stall keeps this share, in percent, per own turn, and counts this much, in percent. */
  final int stallDecayPercent;
  final int stallWeightPercent;
  /** On own turns below this the second call runs the warm-up games instead of searching the real position. */
  final int warmTurns;
  /** The second call does not search on own turns below this: the compiler threads get the CPU instead. */
  final int ponderFromTurn;

  Timing(int[] untilTurn, long[] stopNanos, long firstCallNanos, long ponderNanos, long warmupNanos, int cutTurns,
      long cutNanos, long overrunNanos, boolean centreOnEmpty, long stallLimitNanos, int stallDecayPercent,
      int stallWeightPercent, int warmTurns, int ponderFromTurn) {
    this.untilTurn = untilTurn;
    this.stopNanos = stopNanos;
    this.firstCallNanos = firstCallNanos;
    this.ponderNanos = ponderNanos;
    this.warmupNanos = warmupNanos;
    this.cutTurns = cutTurns;
    this.cutNanos = cutNanos;
    this.overrunNanos = overrunNanos;
    this.centreOnEmpty = centreOnEmpty;
    this.stallLimitNanos = stallLimitNanos;
    this.stallDecayPercent = stallDecayPercent;
    this.stallWeightPercent = stallWeightPercent;
    this.warmTurns = warmTurns;
    this.ponderFromTurn = ponderFromTurn;
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
   * Harness only: "s=55@10/65@30/80,f=30,p=85,w=1000,cut=45@5,over=10,c=1,g=96@85@100". s: first-call stop in ms, each
   * until an own turn, the last for all others. f: first call of an instance. p: second-call search. w: warm-up.
   * cut=MS@TURNS: the stop after trouble and for how long. over: the overrun that counts as trouble. c=1: centre on an
   * empty board. g=LIMIT@DECAY@WEIGHT: the stall sensor. wt=N: warm-up games in the second call of own turns below N.
   * pf=N: no second-call search on own turns below N. Anything not named keeps the value of {@link #DEFAULT}.
   */
  public static Timing parse(String spec) {
    Timing d = DEFAULT;
    int[] until = d.untilTurn;
    long[] stop = d.stopNanos;
    long first = d.firstCallNanos;
    long ponder = d.ponderNanos;
    long warm = d.warmupNanos;
    int cutTurns = d.cutTurns;
    long cut = d.cutNanos;
    long over = d.overrunNanos;
    boolean centre = d.centreOnEmpty;
    long stall = d.stallLimitNanos;
    int decay = d.stallDecayPercent;
    int weight = d.stallWeightPercent;
    int warmTurns = d.warmTurns;
    int ponderFrom = d.ponderFromTurn;
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
      } else if (key.equals("f")) {
        first = MS * Long.parseLong(value);
      } else if (key.equals("p")) {
        ponder = MS * Long.parseLong(value);
      } else if (key.equals("w")) {
        warm = MS * Long.parseLong(value);
      } else if (key.equals("cut")) {
        int at = value.indexOf('@');
        cut = MS * Long.parseLong(value.substring(0, at));
        cutTurns = Integer.parseInt(value.substring(at + 1));
      } else if (key.equals("over")) {
        over = MS * Long.parseLong(value);
      } else if (key.equals("c")) {
        centre = value.equals("1");
      } else if (key.equals("pf")) {
        ponderFrom = Integer.parseInt(value);
      } else if (key.equals("wt")) {
        warmTurns = Integer.parseInt(value);
      } else if (key.equals("g")) {
        String[] g = value.split("@");
        stall = MS * Long.parseLong(g[0]);
        decay = Integer.parseInt(g[1]);
        weight = Integer.parseInt(g[2]);
      }
    }
    return new Timing(until, stop, first, ponder, warm, cutTurns, cut, over, centre, stall, decay, weight, warmTurns,
        ponderFrom);
  }
}
