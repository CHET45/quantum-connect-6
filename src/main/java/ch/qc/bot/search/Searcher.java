package ch.qc.bot.search;

import ch.qc.bot.Clock;
import ch.qc.bot.eval.Evaluator;
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
 * Uploaded bots may not throw, so an abort sets {@code Ctx.stop} and every caller returns as soon as it sees it;
 * values computed after the stop are never used or stored.
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
  private static final int EBF = 3;
  /** blastWinMinDepth that keeps the winning-blast scan at every node. */
  private static final int ALL_DEPTHS = -1000;

  /** Search outcome; vcfNodes and vcfProofs count the threat-search work of this call. */
  public static final class Result {
    private final int move;
    private final int score;
    private final int depth;
    private final long nodes;
    private final long nanos;
    private final long vcfNodes;
    private final long vcfProofs;
    private final long blastRefutations;

    public Result(int move, int score, int depth, long nodes, long nanos, long vcfNodes, long vcfProofs,
        long blastRefutations) {
      this.move = move;
      this.score = score;
      this.depth = depth;
      this.nodes = nodes;
      this.nanos = nanos;
      this.vcfNodes = vcfNodes;
      this.vcfProofs = vcfProofs;
      this.blastRefutations = blastRefutations;
    }

    public int move() {
      return move;
    }

    public int score() {
      return score;
    }

    public int depth() {
      return depth;
    }

    public long nodes() {
      return nodes;
    }

    public long nanos() {
      return nanos;
    }

    public long vcfNodes() {
      return vcfNodes;
    }

    public long vcfProofs() {
      return vcfProofs;
    }

    public long blastRefutations() {
      return blastRefutations;
    }
  }

  /**
   * Switches of the threat-search integration (design 2.5): own VCF at the root, the opponent's VCF check at the
   * root, VCF at the leaves (only while the defender's special is not ready), exact or tentative leaves [H1],
   * razoring into the VCF [H9]. Leaf VCF is off by default: unrestricted it cost 160 Elo through lost depth.
   * Contempt (1/256 logit) scores a draw as that much worse than equal for the searching side and as that much
   * better for its opponent, so the bot plays on from an unclear position rather than take a draw, but still takes
   * a draw over a position clearly lost. A Searcher with contempt must search for one colour only (its TT keeps
   * draw scores from that colour's view). ebf: no new iteration starts past 1/ebf of the hard stop, 0 uses the whole
   * budget (fixed-node runs keep the node rule). blastWinMinDepth: the winning-blast scan, costly at leaves on large
   * boards, runs only with at least this much depth left. blastMargin (1/256 logit): a non-winning root blast must
   * beat the other moves by this much. lmrFrom: quiet drops from this index in the move order on, with no threat to
   * answer and at least 3 plies left, are searched one ply shallower (two from twice that index with at least 5 plies
   * left) and again in full if they beat alpha; 0 turns this off.
   */
  public static final class Options {
    public static final Options DEFAULT = new Options(true, true, false, true, false, 0, 0, 1, 0, 0);
    public static final Options NONE = new Options(false, false, false, true, false);

    public final boolean rootVcf;
    public final boolean opponentVcf;
    public final boolean leafVcf;
    public final boolean exactLeaves;
    public final boolean razor;
    public final int contempt;
    public final int ebf;
    public final int blastWinMinDepth;
    public final int blastMargin;
    public final int lmrFrom;

    public Options(boolean rootVcf, boolean opponentVcf, boolean leafVcf, boolean exactLeaves, boolean razor) {
      this(rootVcf, opponentVcf, leafVcf, exactLeaves, razor, 0);
    }

    public Options(boolean rootVcf, boolean opponentVcf, boolean leafVcf, boolean exactLeaves, boolean razor,
        int contempt) {
      this(rootVcf, opponentVcf, leafVcf, exactLeaves, razor, contempt, EBF, ALL_DEPTHS, 0, 0);
    }

    private Options(boolean rootVcf, boolean opponentVcf, boolean leafVcf, boolean exactLeaves, boolean razor,
        int contempt, int ebf, int blastWinMinDepth, int blastMargin, int lmrFrom) {
      this.rootVcf = rootVcf;
      this.opponentVcf = opponentVcf;
      this.leafVcf = leafVcf;
      this.exactLeaves = exactLeaves;
      this.razor = razor;
      this.contempt = contempt;
      this.ebf = ebf;
      this.blastWinMinDepth = blastWinMinDepth;
      this.blastMargin = blastMargin;
      this.lmrFrom = lmrFrom;
    }

    public Options withSwitches(boolean rootVcf, boolean opponentVcf, boolean leafVcf, boolean exactLeaves,
        boolean razor) {
      return new Options(rootVcf, opponentVcf, leafVcf, exactLeaves, razor, contempt, ebf, blastWinMinDepth,
          blastMargin, lmrFrom);
    }

    public Options withContempt(int v) {
      return new Options(rootVcf, opponentVcf, leafVcf, exactLeaves, razor, v, ebf, blastWinMinDepth, blastMargin,
          lmrFrom);
    }

    public Options withEbf(int v) {
      return new Options(rootVcf, opponentVcf, leafVcf, exactLeaves, razor, contempt, v, blastWinMinDepth, blastMargin,
          lmrFrom);
    }

    public Options withBlastWinMinDepth(int v) {
      return new Options(rootVcf, opponentVcf, leafVcf, exactLeaves, razor, contempt, ebf, v, blastMargin, lmrFrom);
    }

    public Options withBlastMargin(int v) {
      return new Options(rootVcf, opponentVcf, leafVcf, exactLeaves, razor, contempt, ebf, blastWinMinDepth, v, lmrFrom);
    }

    public Options withLmrFrom(int v) {
      return new Options(rootVcf, opponentVcf, leafVcf, exactLeaves, razor, contempt, ebf, blastWinMinDepth, blastMargin,
          v);
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
  private final Evaluator eval;
  private final TranspositionTable tt;
  private final Vcf.Table vcfTable;
  private final Options options;
  private final boolean leafVcf;
  private final int pollMask;
  private final long zStm;
  private final long[][] zCd = new long[3][COOLDOWN + 1];
  private final long[] zTurn = new long[MAX_PLY + 1];
  private final int[] centreBonus;
  /**
   * gain[colour][code]: what a disc of the colour on an empty cell of this window is worth for the move ordering: the
   * rise of the square of its discs if the opponent has none there, plus the square of the opponent's discs if the
   * colour has none (a window then lost to the opponent). The empty cell's position does not matter.
   */
  private final int[][] gain = new int[3][];
  private volatile int token;
  /**
   * Context of the last finished call, reused by the next one so a call allocates almost nothing (the server
   * limits the heap). A call that is still running (abandoned by the engine) keeps its own.
   */
  private volatile Ctx idle;

  /** Mutable state of one search call. */
  private static final class Ctx {
    final Geometry g;
    int token;
    long deadline;
    long maxNodes;
    int turnBase;
    long nodes;
    /** Set by the first poll past a limit; from then on every search call returns at once. */
    boolean stop;
    final int[][] moves = new int[MAX_PLY][];
    final int[][] order = new int[MAX_PLY][];
    final int[][] killers = new int[MAX_PLY][2];
    final int[][] history = new int[3][];
    final Board[] pool = new Board[MAX_PLY];
    final Vcf vcf;
    final Vcf leafVcf;

    Ctx(Geometry g, Options options, Vcf.Table vcfTable, int token, long deadline, long maxNodes, int turnBase) {
      this.g = g;
      this.token = token;
      this.deadline = deadline;
      this.maxNodes = maxNodes;
      this.turnBase = turnBase;
      vcf = new Vcf(g, vcfTable, true);
      leafVcf = options.exactLeaves ? vcf : new Vcf(g, vcfTable, false);
      history[1] = new int[g.cols];
      history[2] = new int[g.cols];
      for (int[] k : killers) {
        k[0] = k[1] = Tactics.NONE;
      }
    }

    /** Puts a finished context back into the state of a new one. */
    void reset(int token, long deadline, long maxNodes, int turnBase) {
      this.token = token;
      this.deadline = deadline;
      this.maxNodes = maxNodes;
      this.turnBase = turnBase;
      nodes = 0;
      stop = false;
      java.util.Arrays.fill(history[1], 0);
      java.util.Arrays.fill(history[2], 0);
      for (int[] k : killers) {
        k[0] = k[1] = Tactics.NONE;
      }
      vcf.resetStats();
      leafVcf.resetStats();
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

  public Searcher(Geometry g, Evaluator eval, TranspositionTable tt) {
    this(g, eval, tt, Options.DEFAULT);
  }

  public Searcher(Geometry g, Evaluator eval, TranspositionTable tt, Options options) {
    this(g, eval, tt, options, new Vcf.Table(17));
  }

  /** The threat-search table is passed in so a bot can allocate it once, outside any timed call. */
  public Searcher(Geometry g, Evaluator eval, TranspositionTable tt, Options options, Vcf.Table vcfTable) {
    this.g = g;
    this.t = g.tables;
    this.eval = eval;
    this.tt = tt;
    this.options = options;
    this.leafVcf = options.leafVcf && g.cells <= LEAF_VCF_MAX_CELLS;
    // a node costs about the window count (full sums, blast copies), so the polling interval follows it
    this.pollMask = g.windows <= 200 ? 63 : g.windows <= 500 ? 31 : 15;
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
      gain[colour] = new int[t.codes];
      for (int code = 0; code < t.codes; code++) {
        int own = 0;
        int other = 0;
        for (int i = 0, c = code; i < t.k; i++, c /= 3) {
          own += c % 3 == colour ? 1 : 0;
          other += c % 3 == 3 - colour ? 1 : 0;
        }
        gain[colour][code] = (other == 0 ? 2 * own + 1 : 0) + (own == 0 ? other * other : 0);
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
    return run(root, me, cdMe, cdOpp, turnIndex, startNanos, hardNanos, Long.MAX_VALUE);
  }

  /**
   * As {@link #run(Board, int, int, int, int, long, long)} with a node budget as well. With an unreachable time
   * limit the search is reproducible: it stops at the first clock poll after maxNodes and starts no iteration once a
   * third of the budget is used (the harness's fixed-node mode, design 5.2).
   */
  public Result run(Board root, int me, int cdMe, int cdOpp, int turnIndex, long startNanos, long hardNanos,
      long maxNodes) {
    int myToken = ++token;
    tt.newSearch();
    Ctx x = idle;
    idle = null;
    if (x == null) {
      x = new Ctx(g, options, vcfTable, myToken, startNanos + hardNanos, maxNodes, turnIndex);
    } else {
      x.reset(myToken, startNanos + hardNanos, maxNodes, turnIndex);
    }
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
      for (int c = 0; c < g.cols; c++) {
        if (!root.isFull(c)) {
          if (root.blastWins(c)) {
            return result(x, c | Tactics.SPECIAL, MATE - 1, 1, startNanos);
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
          Math.min(hardDeadline, Clock.nanos() + ROOT_VCF_NANOS), ROOT_VCF_NODES);
      if (m != Tactics.NONE) {
        return result(x, m, MATE_BOUND + 1, 0, startNanos);
      }
    }
    if (options.opponentVcf && n > 1) {
      n = dropMovesLosingToVcf(x, root, me, cdMe, cdOpp, turnIndex, moves, scores, n,
          Math.min(hardDeadline, Clock.nanos() + OPP_VCF_NANOS));
    }

    int best = moves[0];
    int bestScore = -INF;
    int completed = 0;
    for (int depth = 1; depth <= MAX_DEPTH; depth++) {
      int iterBest = Tactics.NONE;
      int iterScore = -INF;
      int alpha = -INF;
      for (int i = 0; i < n && !x.stop; i++) {
        int m = moves[i];
        int margin = Tactics.isSpecial(m) ? options.blastMargin : 0;
        int score = rootChild(x, root, me, opp, cdMe, cdOpp, m, depth, alpha + margin, i == 0);
        if (x.stop) {
          break;
        }
        if (Math.abs(score) <= MATE_BOUND) {
          score -= margin;
        }
        scores[i] = score;
        if (score > iterScore) {
          iterScore = score;
          iterBest = m;
        }
        if (score > alpha) {
          alpha = score;
        }
      }
      if (x.stop) {
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
      long elapsed = Clock.nanos() - startNanos;
      if (Math.abs(bestScore) > MATE_BOUND || n == 1 || (options.ebf > 0 && elapsed * options.ebf > hardNanos)
          || x.nodes * EBF > maxNodes) {
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
    Result r = new Result(move, score, depth, x.nodes, Clock.nanos() - startNanos, vn, vp, br);
    idle = x;
    return r;
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
    for (int i = 0; i < kept; i++) {
      moves[i] = m2[i];
      scores[i] = s2[i];
    }
    return kept;
  }

  private int rootChild(Ctx x, Board root, int me, int opp, int cdMe, int cdOpp, int m, int depth, int alpha,
      boolean first) {
    int c = Tactics.column(m);
    if (!Tactics.isSpecial(m)) {
      boolean win = root.make(c, me);
      int s = win ? MATE - 1
          : root.topRowFull() ? draw(0)
          : pvsChild(x, root, opp, cdOpp, Math.min(cdMe + 1, COOLDOWN), depth - 1, alpha, INF, 0, first);
      root.unmake(c);
      return s;
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
    if (x.stop) {
      return 0;
    }
    if (s > alpha && s < beta) {
      s = -search(x, b, stm, cdStm, cdOpp, depth, -beta, -alpha, ply + 1, true);
    }
    return s;
  }

  private int search(Ctx x, Board b, int stm, int cdStm, int cdOpp, int depth, int alpha, int beta, int ply,
      boolean pv) {
    if (x.stop) {
      return 0;
    }
    if ((++x.nodes & pollMask) == 0 && poll(x)) {
      return 0;
    }
    if (x.turnBase + ply >= MAX_TURNS) {
      return draw(ply);
    }
    int opp = 3 - stm;
    int oppThreats = 0;
    int threatCol = -1;
    for (int c = 0; c < g.cols; c++) {
      if (b.columnThreats(c) == 0) {
        continue; // a full column has no threat cell either
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
    if (ready && depth >= options.blastWinMinDepth && blastWins(b)) {
      return MATE - ply - 1;
    }
    if (oppThreats >= 2 && !ready) {
      return -(MATE - ply - 2);
    }
    if (b.topRowFull()) {
      return draw(ply);
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
      return oppThreats >= 2 ? -(MATE - ply - 2) : draw(ply);
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
        int lmr = options.lmrFrom;
        int red = lmr == 0 || i < lmr || depth < 3 || oppThreats > 0 ? 0 : i >= 2 * lmr && depth >= 5 ? 2 : 1;
        if (win) {
          s = MATE - ply - 1;
        } else if (b.topRowFull()) {
          s = draw(ply);
        } else if (red > 0) {
          s = -search(x, b, opp, cdOpp, nextCdOpp, depth - 1 - red, -alpha - 1, -alpha, ply + 1, false);
          if (!x.stop && s > alpha) {
            s = pvsChild(x, b, opp, cdOpp, nextCdOpp, depth - 1, alpha, beta, ply, false);
          }
        } else {
          s = pvsChild(x, b, opp, cdOpp, nextCdOpp, depth - 1, alpha, beta, ply, i == 0);
        }
        b.unmake(c);
      } else {
        Board child = x.pool(ply);
        child.copyFrom(b);
        s = child.blast(c) ? MATE - ply - 1 : pvsChild(x, child, opp, cdOpp, 1, depth - 1, alpha, beta, ply, i == 0);
      }
      if (x.stop) {
        return 0;
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

  /** Draw score for the side to move at a node of this ply (the searching colour moves at even plies). */
  private int draw(int ply) {
    return (ply & 1) == 0 ? -options.contempt : options.contempt;
  }

  /** Short VCF for the side to move at a leaf: a win score if it finds one, else 0. */
  private int leafProof(Ctx x, Board b, int stm, int cdStm, int cdOpp, int ply) {
    int m = x.leafVcf.attack(b, stm, cdStm, cdOpp, LEAF_VCF_MOVES, x.turnBase + ply, x.deadline, LEAF_VCF_NODES);
    if (m == Tactics.NONE) {
      return 0;
    }
    return options.exactLeaves ? MATE - ply - (2 * LEAF_VCF_MOVES + 1) : TENTATIVE_WIN;
  }

  private boolean blastWins(Board b) {
    for (int c = 0; c < g.cols; c++) {
      if (b.blastWins(c)) {
        return true;
      }
    }
    return false;
  }

  /** Checks the limits; returns true (and sets the stop flag) once the search must end. */
  private boolean poll(Ctx x) {
    if (x.nodes > x.maxNodes || Clock.nanos() > x.deadline || token != x.token) {
      x.stop = true;
    }
    return x.stop;
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
    int[] value = gain[stm];
    int[] wins = g.cellWin;
    int i = g.index(c, b.height(c));
    int end = g.cellStart[i + 1];
    int s = 0;
    for (int j = g.cellStart[i]; j < end; j++) {
      s += value[b.windowCode(wins[j])];
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
