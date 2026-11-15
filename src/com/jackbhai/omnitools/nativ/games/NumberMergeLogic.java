package com.jackbhai.omnitools.nativ.games;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Number Merge (Threes-style): 4x4 grid of numbered tiles. Slide a row
 *  or column; adjacent equal tiles merge into their sum, and 1+2 merge
 *  into 3 (the Threes rule). 0 and any other mismatched pair block.
 *  A new tile (1, 2 or 3) spawns in the gap the slide opened.
 *  Score = sum of every tile created by merging. Game over when no
 *  slide changes the board. */
public final class NumberMergeLogic {
    public static final int N = 4;
    private final int[][] g = new int[N][N];
    private final Random rnd;
    public int score;

    public NumberMergeLogic(Random rnd) {
        this.rnd = rnd;
        spawn(); spawn(); spawn();
    }

    public int at(int r, int c) { return g[r][c]; }
    /** Test hook. */
    public void set(int r, int c, int v) { g[r][c] = v; }

    /** Can tiles a and b merge? Equal values, or the 1+2 pair. */
    public static boolean canMerge(int a, int b) {
        if (a == 0 || b == 0) return false;
        return a == b || (a + b == 3);
    }
    public static int mergedValue(int a, int b) { return a + b; }

    /** Compress + merge one line toward index 0. Returns points gained.
     *  Each tile merges at most once per move. */
    static int[] slideLine(int[] line, int[] pointsOut) {
        List<Integer> tiles = new ArrayList<>();
        for (int v : line) if (v != 0) tiles.add(v);
        List<Integer> out = new ArrayList<>();
        int pts = 0;
        for (int i = 0; i < tiles.size(); i++) {
            int v = tiles.get(i);
            if (i + 1 < tiles.size() && canMerge(v, tiles.get(i + 1))) {
                int m = mergedValue(v, tiles.get(i + 1));
                out.add(m); pts += m; i++;
            } else out.add(v);
        }
        int[] res = new int[line.length];
        for (int i = 0; i < out.size(); i++) res[i] = out.get(i);
        pointsOut[0] = pts;
        return res;
    }

    /** Slide the whole board. dir: 0 left, 1 right, 2 up, 3 down.
     *  @return true if the board changed (then a tile spawns). */
    public boolean move(int dir) {
        int[][] before = new int[N][N];
        for (int r = 0; r < N; r++) System.arraycopy(g[r], 0, before[r], 0, N);
        int[] pts = new int[1];
        int gained = 0;
        for (int i = 0; i < N; i++) {
            int[] line = new int[N];
            for (int j = 0; j < N; j++) line[j] = cellToward(before, i, j, dir);
            int[] slid = slideLine(line, pts);
            gained += pts[0];
            for (int j = 0; j < N; j++) setToward(i, j, dir, slid[j]);
        }
        boolean changed = false;
        for (int r = 0; r < N && !changed; r++) for (int c = 0; c < N; c++) if (g[r][c] != before[r][c]) changed = true;
        if (!changed) { // restore (slide writes even when unchanged, but values are identical)
            return false;
        }
        score += gained;
        spawn();
        return true;
    }

    private int cellToward(int[][] b, int i, int j, int dir) {
        switch (dir) {
            case 0: return b[i][j];           // left: row i, front = col 0
            case 1: return b[i][N - 1 - j];   // right
            case 2: return b[j][i];           // up: col i, front = row 0
            default: return b[N - 1 - j][i];  // down
        }
    }
    private void setToward(int i, int j, int dir, int v) {
        switch (dir) {
            case 0: g[i][j] = v; break;
            case 1: g[i][N - 1 - j] = v; break;
            case 2: g[j][i] = v; break;
            default: g[N - 1 - j][i] = v; break;
        }
    }

    private void spawn() {
        List<int[]> empty = new ArrayList<>();
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) if (g[r][c] == 0) empty.add(new int[]{r, c});
        if (empty.isEmpty()) return;
        int[] cell = empty.get(rnd.nextInt(empty.size()));
        int roll = rnd.nextInt(12);
        g[cell[0]][cell[1]] = roll < 5 ? 1 : roll < 10 ? 2 : 3;
    }

    public boolean isGameOver() {
        for (int dir = 0; dir < 4; dir++) if (canMove(dir)) return false;
        return true;
    }
    /** Would a slide in dir change anything? (No mutation.) */
    public boolean canMove(int dir) {
        for (int i = 0; i < N; i++) {
            int[] line = new int[N];
            for (int j = 0; j < N; j++) line[j] = cellToward(g, i, j, dir);
            int[] slid = slideLine(line, new int[1]);
            for (int j = 0; j < N; j++) if (slid[j] != line[j]) return true;
        }
        return false;
    }
    public int maxTile() {
        int m = 0; for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) m = Math.max(m, g[r][c]);
        return m;
    }
}
