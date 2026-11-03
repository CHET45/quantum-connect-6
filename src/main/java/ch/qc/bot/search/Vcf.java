package ch.qc.bot.search;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;

/**
 * Gravity threat search (milestone M3, design 2.5): victory by continuous forcing drops. The attacker plays only
 * drops after which it has a playable threat cell (the landing cell of a column that completes its six); the
 * defender's replies are enumerated in full: its own immediate win, the block of a single playable threat, and
 * every blast while its special is ready. Every other defender move leaves the threat playable, so a returned win
 * is a proof (exact mode), not a guess. Draws by a full board and by the 1,000-turn cap are respected.
 *
 * <p>Tentative mode (hypothesis H1) skips the defender's blast replies, which makes it much cheaper but turns a
 * "win" into a strong hint; its results use separate table keys so they never pass for proofs.
 *
 * <p>One instance per search call (the board pool is per call); the result table is shared and lockless.
 */
public final class Vcf {
  private static final int COOLDOWN = 10;
  private static final int MAX_TURNS = 1000;
  private static final int MAX_VPLY = 64;
  private static final int WIN = 1;
  private static final int FAIL = 2;

  /** Shared result table of (key ^ data, data); data = result | depth << 2 | (move + 1) << 8. */
  public static final class Table {
    final long[] keys;
    final long[] data;
    final int mask;
    final long[][] zCd = new long[3][COOLDOWN + 1];
    final long[] zSide = new long[3];

    public Table(int log2) {
      keys = new long[1 << log2];
      data = new long[1 << log2];
      mask = (1 << log2) - 1;
      java.util.SplittableRandom rnd = new java.util.SplittableRandom(0x5EEDF00DCAFEL);
      for (int s = 1; s <= 2; s++) {
        zSide[s] = rnd.nextLong();
        for (int c = 0; c <= COOLDOWN; c++) {
          zCd[s][c] = rnd.nextLong();
        }
      }
    }
  }

  private static final long TENTATIVE_KEY = 0x7E47A71BE5EEDL;

  private final Geometry g;
  private final Table table;
  private final boolean exact;
  private final Board[] pool = new Board[MAX_VPLY + 2];
  private int turnBase;
  private long deadline;
  private long nodeLimit;
  private boolean aborted;
  private int winMove;

  /** Statistics over the lifetime of this instance. */
  public long nodes;
  public long proofs;
  public long blastRefutations;

  public Vcf(Geometry g, Table table, boolean exact) {
    this.g = g;
    this.table = table;
    this.exact = exact;
  }

  /**
   * Looks for a forced win of colour a (to move) with at most maxMoves forcing drops before the winning move,
   * deepening from one. Returns the first move of the proof ({@link Tactics} encoding) or {@link Tactics#NONE}.
   *
   * @param turnIndex engine iteration index of a's move, for the 1,000-turn cap
   */
  public int attack(Board b, int a, int cdA, int cdD, int maxMoves, int turnIndex, long deadlineNanos, long nodes) {
    turnBase = turnIndex;
    deadline = deadlineNanos;
    nodeLimit = this.nodes + nodes;
    aborted = false;
    for (int d = 0; d <= maxMoves && !aborted; d++) {
      winMove = Tactics.NONE;
      if (attacker(b, a, Math.min(cdA, COOLDOWN), Math.min(cdD, COOLDOWN), d, 0)) {
        proofs++;
        return winMove;
      }
    }
    return Tactics.NONE;
  }

  public boolean lastAborted() {
    return aborted;
  }

  private boolean attacker(Board b, int a, int cdA, int cdD, int d, int vply) {
    if (aborted || ++nodes > nodeLimit || System.nanoTime() > deadline) {
      aborted = true;
      return false;
    }
    if (turnBase + vply >= MAX_TURNS) {
      return false;
    }
    int def = 3 - a;
    int defThreats = 0;
    int defCol = -1;
    for (int c = 0; c < g.cols; c++) {
      if (b.isFull(c)) {
        continue;
      }
      int cell = g.index(c, b.height(c));
      if (b.threats(a, cell) > 0) {
        if (vply == 0) {
          winMove = c;
        }
        return true;
      }
      if (b.threats(def, cell) > 0) {
        defThreats++;
        defCol = c;
      }
    }
    if (cdA >= COOLDOWN) {
      for (int c = 0; c < g.cols; c++) {
        if (b.blastMayWin(c)) {
          Board p = pool(vply);
          p.copyFrom(b);
          if (p.blast(c)) {
            if (vply == 0) {
              winMove = c | Tactics.SPECIAL;
            }
            return true;
          }
        }
      }
    }
    if (d == 0 || defThreats >= 2 || vply >= MAX_VPLY) {
      return false;
    }
    long key = b.hash() ^ table.zSide[a] ^ table.zCd[a][cdA] ^ table.zCd[def][cdD];
    if (!exact) {
      key ^= TENTATIVE_KEY;
    }
    if (MAX_TURNS - turnBase < 4 * MAX_VPLY) {
      key ^= (long) (MAX_TURNS - turnBase - vply) * 0x9E3779B97F4A7C15L;
    }
    int idx = (int) key & table.mask;
    long e = table.data[idx];
    if (e != 0 && (table.keys[idx] ^ e) == key) {
      int res = (int) (e & 3);
      int depth = (int) (e >>> 2) & 0x3F;
      if (res == WIN && depth <= d) {
        if (vply == 0) {
          winMove = (int) (e >>> 8) - 1;
        }
        if (vply > 0 || winMove != Tactics.NONE) {
          return true;
        }
      } else if (res == FAIL && depth >= d) {
        return false;
      }
    }
    int nextCdA = Math.min(cdA + 1, COOLDOWN);
    boolean defReady = cdD >= COOLDOWN;
    // drops are made and taken back in place; only blasts work on copies
    for (int i = 0; i < g.cols; i++) {
      int c = centre(i);
      if (b.isFull(c) || (defThreats == 1 && c != defCol)) {
        continue;
      }
      b.make(c, a);
      try {
        if (b.topRowFull()) {
          continue;
        }
        int threats = 0;
        int threatCol = -1;
        boolean defWins = false;
        for (int x = 0; x < g.cols; x++) {
          if (b.isFull(x)) {
            continue;
          }
          int cell = g.index(x, b.height(x));
          if (b.threats(a, cell) > 0) {
            threats++;
            threatCol = x;
          }
          if (b.threats(def, cell) > 0) {
            defWins = true;
          }
        }
        if (threats == 0 || defWins || (defReady && blastWins(b, vply + 1))) {
          continue;
        }
        if (defender(b, a, nextCdA, cdD, d - 1, vply + 1, threats, threatCol)) {
          if (vply == 0) {
            winMove = c;
          }
          store(key, idx, WIN, d, c);
          return true;
        }
        if (aborted) {
          return false;
        }
      } finally {
        b.unmake(c);
      }
    }
    if (!aborted) {
      store(key, idx, FAIL, d, Tactics.NONE);
    }
    return false;
  }

  /** Defender to move after a forcing drop: true if every reply still loses to a continued attack. */
  private boolean defender(Board p, int a, int cdA, int cdD, int d, int vply, int threats, int threatCol) {
    int def = 3 - a;
    if (threats == 1) {
      boolean refuted;
      if (p.make(threatCol, def)) {
        refuted = true;
      } else {
        refuted = p.topRowFull() || !attacker(p, a, cdA, Math.min(cdD + 1, COOLDOWN), d, vply + 1);
      }
      p.unmake(threatCol);
      if (refuted) {
        return false;
      }
    }
    Board r = pool(vply);
    if (exact && cdD >= COOLDOWN) {
      for (int c = 0; c < g.cols; c++) {
        if (p.isFull(c)) {
          continue;
        }
        r.copyFrom(p);
        if (r.blast(c) || !attacker(r, a, cdA, 1, d, vply + 1)) {
          if (!aborted) {
            blastRefutations++;
          }
          return false;
        }
      }
    }
    return true;
  }

  private boolean blastWins(Board b, int vply) {
    for (int c = 0; c < g.cols; c++) {
      if (b.blastMayWin(c)) {
        Board p = pool(vply);
        p.copyFrom(b);
        if (p.blast(c)) {
          return true;
        }
      }
    }
    return false;
  }

  private void store(long key, int idx, int result, int d, int move) {
    long data = result | ((long) Math.min(d, 63) << 2) | ((long) (move + 1) << 8);
    table.data[idx] = data;
    table.keys[idx] = key ^ data;
  }

  private Board pool(int vply) {
    Board b = pool[vply];
    if (b == null) {
      b = new Board(g);
      pool[vply] = b;
    }
    return b;
  }

  private int centre(int i) {
    int mid = (g.cols - 1) / 2;
    return i % 2 == 0 ? mid - i / 2 : mid + (i + 1) / 2;
  }
}
