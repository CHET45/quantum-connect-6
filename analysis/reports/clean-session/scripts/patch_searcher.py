"""Scratch copy of Searcher with experiment switches on Options; the project's sources stay untouched."""
import sys

src, dst = sys.argv[1], sys.argv[2]
s = open(src, encoding="utf-8").read()


def sub(old, new):
    global s
    assert s.count(old) == 1, old
    s = s.replace(old, new)


sub("    public final int contempt;\n",
    "    public final int contempt;\n"
    "    public int blastWinMinDepth = -1000;\n"
    "    public int blastGenDepth = 3;\n"
    "    public int blastReduce = 0;\n"
    "    public int ebf = EBF;\n")
sub("elapsed * EBF > hardNanos", "(options.ebf > 0 && elapsed * options.ebf > hardNanos)")
sub("if (ready && blastWins(x, b, ply)) {", "if (ready && depth >= options.blastWinMinDepth && blastWins(x, b, ply)) {")
sub("if (ready && (depth >= 3 || oppThreats > 0)) {", "if (ready && (depth >= options.blastGenDepth || oppThreats > 0)) {")
sub("        s = child.blast(c) ? MATE - ply - 1 : pvsChild(x, child, opp, cdOpp, 1, depth - 1, alpha, beta, ply, i == 0);\n",
    "        if (child.blast(c)) {\n"
    "          s = MATE - ply - 1;\n"
    "        } else if (options.blastReduce > 0 && oppThreats == 0 && i > 0 && depth - 1 > options.blastReduce) {\n"
    "          s = -search(x, child, opp, cdOpp, 1, depth - 1 - options.blastReduce, -alpha - 1, -alpha, ply + 1, false);\n"
    "          if (!x.stop && s > alpha) {\n"
    "            s = pvsChild(x, child, opp, cdOpp, 1, depth - 1, alpha, beta, ply, false);\n"
    "          }\n"
    "        } else {\n"
    "          s = pvsChild(x, child, opp, cdOpp, 1, depth - 1, alpha, beta, ply, i == 0);\n"
    "        }\n")
open(dst, "w", encoding="utf-8", newline="\n").write(s)
print("patched")
