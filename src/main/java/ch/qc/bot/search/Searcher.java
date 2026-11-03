package ch.qc.bot.search;

import ch.qc.bot.eval.HandEval;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.rules.Tables;
import java.util.SplittableRandom;

/**
 * Alpha-beta search of milestone M2 (design 2.6, 2.8): fail-soft negamax PVS with iterative deepening, a shared
 * transposition table keyed by cells, side to move and both cooldown counters, TT-move / killer / history
 * ordering with blasts last, mate-distance scores, and a zero-cost forced reply to a single playable threat.
 * Moves are encoded as in {@link Tactics}. Each {@link #run} gets its own context and a token, so a search that
 * outlived its call (the engine only interrupts it) aborts at its next clock poll instead of disturbing the next.
 */
public final class Searcher {
  public static final int MATE = 30_000;
  /** Scores beyond this magnitude are proven wins or losses. */
  public static final int MATE_BOUND = MATE - 1_000;
  private static final int INF = 32_000;
  private static final int MAX_PLY = 96;
  private static final int MAX_DEPTH = 64;
  private static final int COOLDOWN = 10;
  private static final int MAX_TURNS = 1000;
  /** Clock polling interval in nodes; nodes on boards above 400 cells cost much more (blast copies, eval). */
  private static final int POLL_MASK = 255;
  private static final int POLL_MASK_LARGE = 15;
  private static final int EBF = 3;

  /** Search outcome; vcfNodes and vcfProofs count the threat-search work of this call. */
  public record Result(int move, int score, int depth, long nodes, long nanos, long vcfNodes, long vcfProofs,
      long blastRefutations) {}

  /**
   * Switches of the threat-search integration (design 2.5): own VCF at the root, the opponent's VCF check at the
   * root, VCF at the leaves (only while the defender's special is not ready), exact or tentative leaves [H1],
   * razoring into the VCF [H9]. Leaf VCF is off by default: unrestricted it cost 160 Elo through lost depth.
   */
  public record Options(boolean rootVcf, boolean opponentVcf, boolean leafVcf, boolean exactLeaves, boolean razor) {
    public static final Options DEFAULT = new Options(true, true, false, true, false);
    public static final Options NONE = new Options(false, false, false, true, false);

    /** Applies a spec such as "+razor,-leafVcf" to the defaults. */
    public static Options parse(String spec) {
      Options o = DEFAULT;
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
          case "noVcf" -> NONE;
          default -> throw new IllegalArgumentException("unknown search option " + name);
        };
      }
      return o;
    }
  }

  private static final int ROOT_VCF_MOVES = 12;
  private static final long ROOT_VCF_NODES = 20_000;
  private static final long ROOT_VCF_NANOS = 10_000_000L;
  private static final int OPP_VCF_MOVES = 8;
  private static final long OPP_VCF_NODES = 8_000;
  private static final long OPP_VCF_NANOS = 5_000_000L;
  private static final long OPP_VCF_REPLY_NODES = 1_500;
  private static final int LEAF_VCF_MOVES = 3;
  private static final long LEAF_VCF_NODES = 100;
  private static final int TENTATIVE_WIN = 5_000;
  /** Leaf and razor VCF only on boards up to this many cells; above it a leaf proof costs too much time. */
  private static final int LEAF_VCF_MAX_CELLS = 400;
  private static final int[] RAZOR_MARGIN = {0, 300, 600};

  private final Geometry g;
  private final Tables t;
  private final HandEval eval;
  private final TranspositionTable tt;
  private final Vcf.Table vcfTable;
  private final Options options;
  private final boolean leafVcf;
  private final int pollMask;
  private final long zStm;
  private final long[][] zCd = new long[3][COOLDOWN + 1];
  private final long[] zTurn = new long[MAX_PLY + 1];
  private final int[] centreBonus;
  /** live[colour][code]: square of the colour's discs in a window without opponent discs, else 0. */
  private final int[][] live = new int[3][];
  private volatile int token;

  private static final class Abort extends RuntimeException {
    Abort() {
      super(null, null, false, false);
    }
  }

  private static final Abort ABORT = new Abort();

  /** Mutable state of one search call. */
  private final class Ctx {
    final int token;
    final long deadline;
    final int turnBase;
    long nodes;
    final int[][] moves = new int[MAX_PLY][];
    final int[][] order = new int[MAX_PLY][];
    final int[][] killers = new int[MAX_PLY][2];
    final int[][] history = new int[3][];
    final Board[] pool = new Board[MAX_PLY];
    final Vcf vcf;
    final Vcf leafVcf;

    Ctx(int token, long deadline, int turnBase) {
      this.token = token;
      this.deadline = deadline;
      this.turnBase = turnBase;
      vcf = new Vcf(g, vcfTable, true);
      leafVcf = options.exactLeaves ? vcf : new Vcf(g, vcfTable, false);
      history[1] = new int[g.cols];
      history[2] = new int[g.cols];
      for (int[] k : killers) {
        k[0] = k[1] = Tactics.NONE;
      }
    }

    int[] moves(int ply) {
      if (moves[ply] == null) {
        moves[ply] = new int[2 * g.cols];
        order[ply] = new int[2 * g.cols];
      }
      return moves[ply];
    }

    Board pool(int ply) {
      Board b = pool[ply];
      if (b == null) {
        b = new Board(g);
        pool[ply] = b;
      }
      return b;
    }
  }

  public Searcher(Geometry g, HandEval eval, TranspositionTable tt) {
    this(g, eval, tt, Options.DEFAULT);
  }

  public Searcher(Geometry g, HandEval eval, TranspositionTable tt, Options options) {
    this(g, eval, tt, options, new Vcf.Table(17));
  }

  /** The threat-search table is passed in so a bot can allocate it once, outside any timed call. */
  public Searcher(Geometry g, HandEval eval, TranspositionTable tt, Options options, Vcf.Table vcfTable) {
    this.g = g;
    this.t = g.tables;
    this.eval = eval;
    this.tt = tt;
    this.options = options;
    this.leafVcf = options.leafVcf && g.cells <= LEAF_VCF_MAX_CELLS;
    this.pollMask = g.cells > LEAF_VCF_MAX_CELLS ? POLL_MASK_LARGE : POLL_MASK;
    this.vcfTable = vcfTable;
    SplittableRandom rnd = new SplittableRandom(0x7C0FFEE5EA4C4L);
    zStm = rnd.nextLong();
    for (int colour = 1; colour <= 2; colour++) {
      for (int c = 0; c <= COOLDOWN; c++) {
        zCd[colour][c] = rnd.nextLong();
      }
    }
    for (int i = 0; i <= MAX_PLY; i++) {
      zTurn[i] = rnd.nextLong();
    }
    for (int colour = 1; colour <= 2; colour++) {
      live[colour] = new int[t.codes];
      for (int code = 0; code < t.codes; code++) {
        int own = 0;
        boolean dead = false;
        for (int i = 0, c = code; i < t.k; i++, c /= 3) {
          dead |= c % 3 == 3 - colour;
          own += c % 3 == colour ? 1 : 0;
        }
        live[colour][code] = dead ? 0 : own * own;
      }
    }
    centreBonus = new int[g.cols];
    for (int c = 0; c < g.cols; c++) {
      centreBonus[c] = g.cols - Math.abs(2 * c - (g.cols - 1));
    }
  }

  /**
   * Searches the position for colour me and returns the best move found before the hard deadline.
   *
   * @param cdMe engine cooldown counter of me before this turn
   * @param cdOpp the opponent's counter before its next turn
   * @param turnIndex engine iteration index of this turn (both players, from 0), for the 1,000-turn cap
   */
  public Result run(Board root, int me, int cdMe, int cdOpp, int turnIndex, long startNanos, long hardNanos) {
    int myToken = ++token;
    tt.newSearch();
    Ctx x = new Ctx(myToken, startNanos + hardNanos, turnIndex);
    int opp = 3 - me;
    cdMe = Math.min(cdMe, COOLDOWN);
    cdOpp = Math.min(cdOpp, COOLDOWN);

    int[] moves = new int[2 * g.cols];
    int[] scores = new int[2 * g.cols];
    int n = 0;
    for (int c = 0; c < g.cols; c++) {
      if (!root.isFull(c)) {
        if (root.threats(me, g.index(c, root.height(c))) > 0) {
          return result(x, c, MATE - 1, 1, startNanos);
        }
        moves[n++] = c;
      }
    }
    if (n == 0) {
      return result(x, Tactics.NONE, 0, 0, startNanos);
    }
    if (cdMe >= COOLDOWN) {
      Board probe = x.pool(0);
      for (int c = 0; c < g.cols; c++) {
        if (!root.isFull(c)) {
          if (root.blastMayWin(c)) {
            probe.copyFrom(root);
            if (probe.blast(c)) {
              return result(x, c | Tactics.SPECIAL, MATE - 1, 1, startNanos);
            }
          }
          moves[n++] = c | Tactics.SPECIAL;
        }
      }
    }
    for (int i = 0; i < n; i++) {
      scores[i] = Tactics.isSpecial(moves[i]) ? -1_000_000 : potential(root, Tactics.column(moves[i]), me)
          + centreBonus[Tactics.column(moves[i])];
    }
    sortByScore(moves, scores, n);

    long hardDeadline = startNanos + hardNanos;
    if (options.rootVcf) {
      int m = x.vcf.attack(root, me, cdMe, cdOpp, ROOT_VCF_MOVES, turnIndex,
          Math.min(hardDeadline, System.nanoTime() + ROOT_VCF_NANOS), ROOT_VCF_NODES);
      if (m != Tactics.NONE) {
        return result(x, m, MATE_BOUND + 1, 0, startNanos);
      }
    }
    if (options.opponentVcf && n > 1) {
      n = dropMovesLosingToVcf(x, root, me, cdMe, cdOpp, turnIndex, moves, scores, n,
          Math.min(hardDeadline, System.nanoTime() + OPP_VCF_NANOS));
    }

    int best = moves[0];
    int bestScore = -INF;
    int completed = 0;
    for (int depth = 1; depth <= MAX_DEPTH; depth++) {
      int iterBest = Tactics.NONE;
      int iterScore = -INF;
      int alpha = -INF;
      try {
        for (int i = 0; i < n; i++) {
          int m = moves[i];
          int score = rootChild(x, root, me, opp, cdMe, cdOpp, m, depth, alpha, i == 0);
          scores[i] = score;
          if (score > iterScore) {
            iterScore = score;
            iterBest = m;
          }
          if (score > alpha) {
            alpha = score;
          }
        }
      } catch (Abort a) {
        // a move that beat the previous best with a completed score is still trustworthy
        if (iterBest != Tactics.NONE && iterBest != best && iterScore > bestScore && completed > 0) {
          best = iterBest;
          bestScore = iterScore;
        }
        break;
      }
      best = iterBest;
      bestScore = iterScore;
      completed = depth;
      sortByScore(moves, scores, n);
      long elapsed = System.nanoTime() - startNanos;
      if (Math.abs(bestScore) > MATE_BOUND || n == 1 || elapsed * EBF > hardNanos) {
        break;
      }
    }
    return result(x, best, bestScore, completed, startNanos);
  }

  private Result result(Ctx x, int move, int score, int depth, long startNanos) {
    boolean two = x.leafVcf != x.vcf;
    long vn = x.vcf.nodes + (two ? x.leafVcf.nodes : 0);
    long vp = x.vcf.proofs + (two ? x.leafVcf.proofs : 0);
    long br = x.vcf.blastRefutations + (two ? x.leafVcf.blastRefutations : 0);
    return new Result(move, score, depth, x.nodes, System.nanoTime() - startNanos, vn, vp, br);
  }

  /**
   * If the opponent would win by VCF after a pass, keeps only the root moves after which its VCF is no longer
   * found (moves stay in their order). Returns the new move count; all moves are kept if none stops it.
   */
  private int dropMovesLosingToVcf(Ctx x, Board root, int me, int cdMe, int cdOpp, int turnIndex, int[] moves,
      int[] scores, int n, long deadline) {
    int opp = 3 - me;
    if (x.vcf.attack(root, opp, cdOpp, cdMe, OPP_VCF_MOVES, turnIndex + 1, deadline, OPP_VCF_NODES) == Tactics.NONE) {
      return n;
    }
    int kept = 0;
    int[] keep = new int[n];
    Board child = new Board(g);
    for (int i = 0; i < n; i++) {
      int m = moves[i];
      int c = Tactics.column(m);
      child.copyFrom(root);
      int cdNext;
      if (Tactics.isSpecial(m)) {
        child.blast(c);
        cdNext = 1;
      } else {
        child.make(c, me);
        cdNext = Math.min(cdMe + 1, COOLDOWN);
      }
      if (x.vcf.attack(child, opp, cdOpp, cdNext, OPP_VCF_MOVES, turnIndex + 1, deadline, OPP_VCF_REPLY_NODES)
          == Tactics.NONE) {
        keep[kept++] = i;
      }
    }
    if (kept == 0 || kept == n) {
      return n;
    }
    int[] m2 = new int[kept];
    int[] s2 = new int[kept];
    for (int i = 0; i < kept; i++) {
      m2[i] = moves[keep[i]];
      s2[i] = scores[keep[i]];
    }
    System.arraycopy(m2, 0, moves, 0, kept);
    System.arraycopy(s2, 0, scores, 0, kept);
    return kept;
  }

  private int rootChild(Ctx x, Board root, int me, int opp, int cdMe, int cdOpp, int m, int depth, int alpha,
      boolean first) {
    int c = Tactics.column(m);
    if (!Tactics.isSpecial(m)) {
      boolean win = root.make(c, me);
      try {
        if (win) {
          return MATE - 1;
        }
        if (root.topRowFull()) {
          return 0;
        }
        return pvsChild(x, root, opp, cdOpp, Math.min(cdMe + 1, COOLDOWN), depth - 1, alpha, INF, 0, first);
      } finally {
        root.unmake(c);
      }
    }
    Board child = x.pool(0);
    child.copyFrom(root);
    if (child.blast(c)) {
      return MATE - 1;
    }
    return pvsChild(x, child, opp, cdOpp, 1, depth - 1, alpha, INF, 0, first);
  }

  /** Searches a child at ply + 1 with a null window first unless it is the first move. Returns our score. */
  private int pvsChild(Ctx x, Board b, int stm, int cdStm, int cdOpp, int depth, int alpha, int beta, int ply,
      boolean first) {
    if (first) {
      return -search(x, b, stm, cdStm, cdOpp, depth, -beta, -alpha, ply + 1, true);
    }
    int s = -search(x, b, stm, cdStm, cdOpp, depth, -alpha - 1, -alpha, ply + 1, false);
    if (s > alpha && s < beta) {
      s = -search(x, b, stm, cdStm, cdOpp, depth, -beta, -alpha, ply + 1, true);
    }
    return s;
  }

  private int search(Ctx x, Board b, int stm, int cdStm, int cdOpp, int depth, int alpha, int beta, int ply,
      boolean pv) {
    if ((++x.nodes & pollMask) == 0) {
      poll(x);
    }
    if (x.turnBase + ply >= MAX_TURNS) {
      return 0;
    }
    int opp = 3 - stm;
    int oppThreats = 0;
    int threatCol = -1;
    for (int c = 0; c < g.cols; c++) {
      if (b.isFull(c)) {
        continue;
      }
      int cell = g.index(c, b.height(c));
      if (b.threats(stm, cell) > 0) {
        return MATE - ply - 1;
      }
      if (b.threats(opp, cell) > 0) {
        oppThreats++;
        threatCol = c;
      }
    }
    boolean ready = cdStm >= COOLDOWN;
    if (ready && blastWins(x, b, ply)) {
      return MATE - ply - 1;
    }
    if (oppThreats >= 2 && !ready) {
      return -(MATE - ply - 2);
    }
    if (b.topRowFull()) {
      return 0;
    }
    alpha = Math.max(alpha, -(MATE - ply - 2));
    beta = Math.min(beta, MATE - ply - 1);
    if (alpha >= beta) {
      return alpha;
    }
    boolean forced = oppThreats == 1;
    if (depth <= 0 || ply >= MAX_PLY - 2) {
      if (!forced || ply >= MAX_PLY - 2) {
        if (leafVcf && cdOpp < COOLDOWN && b.live3(stm) > 0) {
          int v = leafProof(x, b, stm, cdStm, cdOpp, ply);
          if (v != 0) {
            return v;
          }
        }
        return eval.evaluate(b, stm, cdStm, cdOpp);
      }
      depth = 1;
    }
    if (options.razor && !pv && !forced && depth <= 2) {
      int ev = eval.evaluate(b, stm, cdStm, cdOpp);
      if (ev + RAZOR_MARGIN[depth] < alpha) {
        int v = leafVcf && cdOpp < COOLDOWN && b.live3(stm) > 0 ? leafProof(x, b, stm, cdStm, cdOpp, ply) : 0;
        return v != 0 ? v : ev;
      }
    }

    long key = key(b, stm, cdStm, cdOpp, x.turnBase + ply);
    long e = tt.probe(key);
    int ttMove = Tactics.NONE;
    if (e != 0) {
      ttMove = TranspositionTable.move(e);
      if (!pv && TranspositionTable.depth(e) >= depth) {
        int s = fromTT(TranspositionTable.score(e), ply);
        int bound = TranspositionTable.bound(e);
        if (bound == TranspositionTable.EXACT || (bound == TranspositionTable.LOWER && s >= beta)
            || (bound == TranspositionTable.UPPER && s <= alpha)) {
          return s;
        }
      }
    }

    int[] list = x.moves(ply);
    int[] score = x.order[ply];
    int n = 0;
    if (forced) {
      list[n++] = threatCol;
    } else if (oppThreats == 0) {
      for (int c = 0; c < g.cols; c++) {
        if (!b.isFull(c)) {
          list[n++] = c;
        }
      }
    }
    if (ready && (depth >= 3 || oppThreats > 0)) {
      for (int c = 0; c < g.cols; c++) {
        if (!b.isFull(c)) {
          list[n++] = c | Tactics.SPECIAL;
        }
      }
    }
    if (n == 0) {
      return oppThreats >= 2 ? -(MATE - ply - 2) : 0;
    }
    int[] kill = x.killers[ply];
    int[] hist = x.history[stm];
    for (int i = 0; i < n; i++) {
      int m = list[i];
      int c = Tactics.column(m);
      if (m == ttMove) {
        score[i] = 1 << 30;
      } else if (Tactics.isSpecial(m)) {
        score[i] = -(1 << 28) + centreBonus[c];
      } else if (m == kill[0]) {
        score[i] = 1 << 27;
      } else if (m == kill[1]) {
        score[i] = 1 << 26;
      } else {
        int s = hist[c] + 4 * potential(b, c, stm) + centreBonus[c];
        int above = g.index(c, b.height(c)) + 1;
        if (b.height(c) + 1 < g.rows && b.threats(opp, above) > 0) {
          s -= 1 << 24; // the reply above would complete the opponent's line
        }
        score[i] = s;
      }
    }

    int best = -INF;
    int bestMove = Tactics.NONE;
    int origAlpha = alpha;
    int nextCdOpp = Math.min(cdStm + 1, COOLDOWN);
    for (int i = 0; i < n; i++) {
      int bi = i;
      for (int j = i + 1; j < n; j++) {
        if (score[j] > score[bi]) {
          bi = j;
        }
      }
      int m = list[bi];
      list[bi] = list[i];
      list[i] = m;
      int tmp = score[bi];
      score[bi] = score[i];
      score[i] = tmp;

      int c = Tactics.column(m);
      int s;
      if (!Tactics.isSpecial(m)) {
        boolean win = b.make(c, stm);
        try {
          s = win ? MATE - ply - 1
              : b.topRowFull() ? 0
              : pvsChild(x, b, opp, cdOpp, nextCdOpp, depth - 1, alpha, beta, ply, i == 0);
        } finally {
          b.unmake(c);
        }
      } else {
        Board child = x.pool(ply);
        child.copyFrom(b);
        s = child.blast(c) ? MATE - ply - 1 : pvsChild(x, child, opp, cdOpp, 1, depth - 1, alpha, beta, ply, i == 0);
      }
      if (s > best) {
        best = s;
        bestMove = m;
        if (s > alpha) {
          alpha = s;
          if (alpha >= beta) {
            if (!Tactics.isSpecial(m)) {
              if (kill[0] != m) {
                kill[1] = kill[0];
                kill[0] = m;
              }
              hist[c] += depth * depth;
            }
            break;
          }
        }
      }
    }
    int bound = best <= origAlpha ? TranspositionTable.UPPER : best >= beta ? TranspositionTable.LOWER
        : TranspositionTable.EXACT;
    tt.store(key, depth, toTT(best, ply), bound, bestMove);
    return best;
  }

  /** Short VCF for the side to move at a leaf: a win score if it finds one, else 0. */
  private int leafProof(Ctx x, Board b, int stm, int cdStm, int cdOpp, int ply) {
    int m = x.leafVcf.attack(b, stm, cdStm, cdOpp, LEAF_VCF_MOVES, x.turnBase + ply, x.deadline, LEAF_VCF_NODES);
    if (m == Tactics.NONE) {
      return 0;
    }
    return options.exactLeaves ? MATE - ply - (2 * LEAF_VCF_MOVES + 1) : TENTATIVE_WIN;
  }

  private boolean blastWins(Ctx x, Board b, int ply) {
    for (int c = 0; c < g.cols; c++) {
      if (b.blastMayWin(c)) {
        Board p = x.pool(ply);
        p.copyFrom(b);
        if (p.blast(c)) {
          return true;
        }
      }
    }
    return false;
  }

  private void poll(Ctx x) {
    if (System.nanoTime() > x.deadline || token != x.token || Thread.currentThread().isInterrupted()) {
      throw ABORT;
    }
  }

  private long key(Board b, int stm, int cdStm, int cdOpp, int turn) {
    int cdFirst = stm == 1 ? cdStm : cdOpp;
    int cdSecond = stm == 1 ? cdOpp : cdStm;
    long k = b.hash() ^ zCd[1][cdFirst] ^ zCd[2][cdSecond];
    if (stm == 2) {
      k ^= zStm;
    }
    int left = MAX_TURNS - turn;
    if (left <= MAX_PLY) {
      k ^= zTurn[left];
    }
    return k;
  }

  /** Window potential gained and denied by dropping into column c (move ordering only). */
  private int potential(Board b, int c, int stm) {
    int[] mine = live[stm];
    int[] theirs = live[3 - stm];
    int i = g.index(c, b.height(c));
    int s = 0;
    for (int j = g.cellStart[i]; j < g.cellStart[i + 1]; j++) {
      int old = b.windowCode(g.cellWin[j]);
      int nw = old + stm * t.pow3[g.cellPos[j]];
      s += mine[nw] - mine[old] + theirs[old] - theirs[nw];
    }
    return s;
  }

  private static int toTT(int s, int ply) {
    return s > MATE_BOUND ? s + ply : s < -MATE_BOUND ? s - ply : s;
  }

  private static int fromTT(int s, int ply) {
    return s > MATE_BOUND ? s - ply : s < -MATE_BOUND ? s + ply : s;
  }

  private static void sortByScore(int[] moves, int[] scores, int n) {
    for (int i = 1; i < n; i++) {
      int m = moves[i];
      int s = scores[i];
      int j = i - 1;
      while (j >= 0 && scores[j] < s) {
        moves[j + 1] = moves[j];
        scores[j + 1] = scores[j];
        j--;
      }
      moves[j + 1] = m;
      scores[j + 1] = s;
    }
  }
}
