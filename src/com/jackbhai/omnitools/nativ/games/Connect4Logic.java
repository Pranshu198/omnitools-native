package com.jackbhai.omnitools.nativ.games;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Pure-Java Connect Four (7x6) with a heuristic AI: win if possible,
 *  block if needed, prefer centre, one-ply threat scoring. */
public final class Connect4Logic {
    public static final int COLS = 7, ROWS = 6, EMPTY = 0, P1 = 1, P2 = 2;
    private final int[][] b = new int[ROWS][COLS]; // row 0 = top
    private int moves;

    public int get(int r, int c) { return b[r][c]; }
    public int moves() { return moves; }
    public boolean columnFull(int c) { return b[0][c] != EMPTY; }
    public boolean isFull() { return moves >= ROWS * COLS; }

    /** Drop a piece; returns landing row or -1 if column full. */
    public int drop(int c, int who) {
        if (c < 0 || c >= COLS) return -1;
        for (int r = ROWS - 1; r >= 0; r--) {
            if (b[r][c] == EMPTY) { b[r][c] = who; moves++; return r; }
        }
        return -1;
    }
    private void undrop(int c) {
        for (int r = 0; r < ROWS; r++) if (b[r][c] != EMPTY) { b[r][c] = EMPTY; moves--; return; }
    }

    public boolean hasWon(int who) {
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            if (b[r][c] != who) continue;
            for (int[] d : dirs) {
                int r3 = r + 3 * d[0], c3 = c + 3 * d[1];
                if (r3 < 0 || r3 >= ROWS || c3 < 0 || c3 >= COLS) continue;
                if (b[r + d[0]][c + d[1]] == who && b[r + 2 * d[0]][c + 2 * d[1]] == who && b[r3][c3] == who)
                    return true;
            }
        }
        return false;
    }
    public int winner() {
        if (hasWon(P1)) return P1;
        if (hasWon(P2)) return P2;
        return EMPTY;
    }
    public List<Integer> legalMoves() {
        List<Integer> out = new ArrayList<>();
        for (int c = 0; c < COLS; c++) if (!columnFull(c)) out.add(c);
        return out;
    }

    /** Count of windows of 4 containing n of `who` and rest empty. */
    private int windowScore(int who) {
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        int score = 0;
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            for (int[] d : dirs) {
                int r3 = r + 3 * d[0], c3 = c + 3 * d[1];
                if (r3 < 0 || r3 >= ROWS || c3 < 0 || c3 >= COLS) continue;
                int mine = 0, opp = 0;
                for (int k = 0; k < 4; k++) {
                    int v = b[r + k * d[0]][c + k * d[1]];
                    if (v == who) mine++; else if (v != EMPTY) opp++;
                }
                if (mine > 0 && opp == 0) score += mine * mine * mine;
                if (opp > 0 && mine == 0) score -= opp * opp * opp;
            }
        }
        return score;
    }

    /** AI move for `ai` against `human`; -1 if no legal move. */
    public int bestMove(int ai, int human, Random rnd) {
        List<Integer> legal = legalMoves();
        if (legal.isEmpty()) return -1;
        // win now
        for (int c : legal) { drop(c, ai); boolean w = hasWon(ai); undrop(c); if (w) return c; }
        // block now
        for (int c : legal) { drop(c, human); boolean w = hasWon(human); undrop(c); if (w) return c; }
        // one-ply heuristic with centre preference; avoid handing the human a win above
        int bestC = legal.get(0), bestS = Integer.MIN_VALUE;
        for (int c : legal) {
            drop(c, ai);
            int s = windowScore(ai) - windowScore(human);
            s += (3 - Math.abs(3 - c)) * 2;
            // if human can win next turn by playing on top, penalise
            boolean danger = false;
            for (int c2 : legalMoves()) { drop(c2, human); if (hasWon(human)) danger = true; undrop(c2); if (danger) break; }
            if (danger) s -= 500;
            undrop(c);
            s += rnd.nextInt(3);
            if (s > bestS) { bestS = s; bestC = c; }
        }
        return bestC;
    }
}
