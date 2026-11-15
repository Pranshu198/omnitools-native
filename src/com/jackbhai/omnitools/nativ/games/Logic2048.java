package com.jackbhai.omnitools.nativ.games;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Pure-Java 2048 rules (MIT 2048 by Gabriele Cirulli, re-implemented):
 *  4x4, each tile merges at most once per move, spawn 2 (90%) / 4 (10%)
 *  after each valid move, score = sum of merges, win at 2048. */
public final class Logic2048 {
    public static final int SIZE = 4;
    public static final int LEFT = 0, RIGHT = 1, UP = 2, DOWN = 3;
    private final int[][] b = new int[SIZE][SIZE];
    private final Random rnd;
    private int score;
    private boolean won;

    public Logic2048(Random rnd) { this.rnd = rnd; reset(); }
    public Logic2048() { this(new Random()); }

    public void reset() {
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) b[r][c] = 0;
        score = 0; won = false;
        spawn(); spawn();
    }
    public int get(int r, int c) { return b[r][c]; }
    public void set(int r, int c, int v) { b[r][c] = v; }
    public int score() { return score; }
    public boolean hasWon() { return won; }

    /** Merge one line toward index 0, each tile at most once.
     *  gained[0] accumulates merge score. Returns new line. */
    public static int[] mergeLine(int[] in, int[] gained) {
        int[] vals = new int[in.length];
        int n = 0;
        for (int v : in) if (v != 0) vals[n++] = v;
        int[] out = new int[in.length];
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (i + 1 < n && vals[i] == vals[i + 1]) {
                out[m++] = vals[i] * 2;
                if (gained != null) gained[0] += vals[i] * 2;
                i++;
            } else out[m++] = vals[i];
        }
        return out;
    }

    private int[] line(int dir, int i) {
        int[] l = new int[SIZE];
        for (int k = 0; k < SIZE; k++) {
            if (dir == LEFT) l[k] = b[i][k];
            else if (dir == RIGHT) l[k] = b[i][SIZE - 1 - k];
            else if (dir == UP) l[k] = b[k][i];
            else l[k] = b[SIZE - 1 - k][i];
        }
        return l;
    }
    private void putLine(int dir, int i, int[] l) {
        for (int k = 0; k < SIZE; k++) {
            if (dir == LEFT) b[i][k] = l[k];
            else if (dir == RIGHT) b[i][SIZE - 1 - k] = l[k];
            else if (dir == UP) b[k][i] = l[k];
            else b[SIZE - 1 - k][i] = l[k];
        }
    }

    public boolean move(int dir) {
        boolean changed = false;
        int[] gained = {0};
        for (int i = 0; i < SIZE; i++) {
            int[] before = line(dir, i);
            int[] after = mergeLine(before, gained);
            for (int k = 0; k < SIZE; k++) if (before[k] != after[k]) changed = true;
            putLine(dir, i, after);
        }
        if (!changed) return false;
        score += gained[0];
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++)
            if (b[r][c] >= 2048) won = true;
        spawn();
        return true;
    }

    /** Spawn on a random empty cell: 2 with 90%, 4 with 10%. False if full. */
    public boolean spawn() {
        List<int[]> empty = new ArrayList<>();
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++)
            if (b[r][c] == 0) empty.add(new int[]{r, c});
        if (empty.isEmpty()) return false;
        int[] cell = empty.get(rnd.nextInt(empty.size()));
        b[cell[0]][cell[1]] = rnd.nextFloat() < 0.9f ? 2 : 4;
        return true;
    }

    public boolean canMove() {
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) {
            if (b[r][c] == 0) return true;
            if (c + 1 < SIZE && b[r][c] == b[r][c + 1]) return true;
            if (r + 1 < SIZE && b[r][c] == b[r + 1][c]) return true;
        }
        return false;
    }
    public boolean isGameOver() { return !canMove(); }
}
