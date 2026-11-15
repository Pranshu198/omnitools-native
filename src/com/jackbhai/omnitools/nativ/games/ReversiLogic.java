package com.jackbhai.omnitools.nativ.games;

import java.util.ArrayList;
import java.util.List;

/** Pure-Java Reversi (Othello) 8x8 with a greedy positional AI. */
public final class ReversiLogic {
    public static final int N = 8, EMPTY = 0, BLACK = 1, WHITE = 2;
    private static final int[][] DIRS = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
    private static final int[][] WEIGHT = {
        {120,-20, 20,  5,  5, 20,-20,120},
        {-20,-40, -5, -5, -5, -5,-40,-20},
        { 20, -5, 15,  3,  3, 15, -5, 20},
        {  5, -5,  3,  3,  3,  3, -5,  5},
        {  5, -5,  3,  3,  3,  3, -5,  5},
        { 20, -5, 15,  3,  3, 15, -5, 20},
        {-20,-40, -5, -5, -5, -5,-40,-20},
        {120,-20, 20,  5,  5, 20,-20,120}};
    private final int[][] b = new int[N][N];

    public ReversiLogic() {
        b[3][3] = WHITE; b[3][4] = BLACK; b[4][3] = BLACK; b[4][4] = WHITE;
    }
    /** Test constructor. */
    public ReversiLogic(int[][] board) {
        for (int r = 0; r < N; r++) System.arraycopy(board[r], 0, b[r], 0, N);
    }
    public int get(int r, int c) { return b[r][c]; }
    public static int other(int who) { return who == BLACK ? WHITE : BLACK; }

    /** Discs that WOULD flip for who at (r,c); empty list = illegal. */
    public List<int[]> flipsFor(int r, int c, int who) {
        List<int[]> out = new ArrayList<>();
        if (r < 0 || c < 0 || r >= N || c >= N || b[r][c] != EMPTY) return out;
        int opp = other(who);
        for (int[] d : DIRS) {
            List<int[]> run = new ArrayList<>();
            int rr = r + d[0], cc = c + d[1];
            while (rr >= 0 && cc >= 0 && rr < N && cc < N && b[rr][cc] == opp) {
                run.add(new int[]{rr, cc}); rr += d[0]; cc += d[1];
            }
            if (!run.isEmpty() && rr >= 0 && cc >= 0 && rr < N && cc < N && b[rr][cc] == who)
                out.addAll(run);
        }
        return out;
    }
    public boolean isLegal(int r, int c, int who) { return !flipsFor(r, c, who).isEmpty(); }
    public List<int[]> legalMoves(int who) {
        List<int[]> out = new ArrayList<>();
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++)
            if (isLegal(r, c, who)) out.add(new int[]{r, c});
        return out;
    }
    /** Place and flip; returns discs flipped, or -1 if illegal. */
    public int place(int r, int c, int who) {
        List<int[]> flips = flipsFor(r, c, who);
        if (flips.isEmpty()) return -1;
        b[r][c] = who;
        for (int[] f : flips) b[f[0]][f[1]] = who;
        return flips.size();
    }
    public int count(int who) {
        int n = 0;
        for (int[] row : b) for (int v : row) if (v == who) n++;
        return n;
    }
    public boolean isOver() {
        return legalMoves(BLACK).isEmpty() && legalMoves(WHITE).isEmpty();
    }
    public int winner() {
        if (!isOver()) return EMPTY;
        int bl = count(BLACK), wh = count(WHITE);
        return bl == wh ? EMPTY : (bl > wh ? BLACK : WHITE);
    }
    /** Greedy positional AI; returns {r,c} or null if it must pass. */
    public int[] bestMove(int who) {
        List<int[]> legal = legalMoves(who);
        if (legal.isEmpty()) return null;
        int opp = other(who);
        int[] best = null; int bestScore = Integer.MIN_VALUE;
        for (int[] m : legal) {
            ReversiLogic sim = new ReversiLogic(b);
            int flipped = sim.place(m[0], m[1], who);
            int s = WEIGHT[m[0]][m[1]] + flipped * 4;
            // reward denying opponent mobility, punish giving corners away
            s -= sim.legalMoves(opp).size() * 3;
            if (s > bestScore) { bestScore = s; best = m; }
        }
        return best;
    }
}
