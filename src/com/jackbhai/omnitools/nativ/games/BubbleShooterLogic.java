package com.jackbhai.omnitools.nativ.games;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Bubble Shooter: staggered hex grid of coloured bubbles. Shoot the
 *  flying bubble from the bottom; when it sticks next to 3+ of its
 *  colour they pop, and anything left hanging drops too. Score:
 *  10 per popped bubble, 20 per dropped bubble. Lose when any bubble
 *  crosses the deadline near the bottom. */
public final class BubbleShooterLogic {
    public static final int COLS = 10, ROWS = 14, START_ROWS = 6, COLORS = 5;
    private final int[][] grid = new int[ROWS][COLS]; // 0 empty, else colour 1..COLORS
    private final Random rnd;
    public int current, next;
    public int score, shots;

    public BubbleShooterLogic(Random rnd) {
        this.rnd = rnd;
        for (int r = 0; r < START_ROWS; r++)
            for (int c = 0; c < COLS - (r % 2); c++) grid[r][c] = 1 + rnd.nextInt(COLORS);
        current = pickColor(); next = pickColor();
    }
    private int pickColor() {
        // only colours still on the board
        boolean[] present = new boolean[COLORS + 1];
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) if (grid[r][c] > 0) present[grid[r][c]] = true;
        List<Integer> opts = new ArrayList<>();
        for (int i = 1; i <= COLORS; i++) if (present[i]) opts.add(i);
        return opts.isEmpty() ? 1 : opts.get(rnd.nextInt(opts.size()));
    }
    public int at(int r, int c) { return (r < 0 || r >= ROWS || c < 0 || c >= COLS) ? 0 : grid[r][c]; }
    public boolean occupied(int r, int c) { return at(r, c) > 0; }

    /** Neighbours in the staggered grid: even rows are shifted left, so
     *  row parity decides the diagonal offsets. */
    public static int[][] neighbours(int r, int c) {
        if (r % 2 == 0) return new int[][]{{r, c - 1}, {r, c + 1}, {r - 1, c - 1}, {r - 1, c}, {r + 1, c - 1}, {r + 1, c}};
        return new int[][]{{r, c - 1}, {r, c + 1}, {r - 1, c}, {r - 1, c + 1}, {r + 1, c}, {r + 1, c + 1}};
    }
    private boolean inBounds(int r, int c) { return r >= 0 && r < ROWS && c >= 0 && c < COLS - (r % 2); }

    /** Connected same-colour cluster containing (r,c), flood fill. */
    public List<int[]> cluster(int r, int c) {
        List<int[]> out = new ArrayList<>();
        int col = at(r, c);
        if (col == 0) return out;
        boolean[][] seen = new boolean[ROWS][COLS];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{r, c}); seen[r][c] = true;
        while (!q.isEmpty()) {
            int[] cur = q.poll(); out.add(cur);
            for (int[] n : neighbours(cur[0], cur[1]))
                if (inBounds(n[0], n[1]) && !seen[n[0]][n[1]] && grid[n[0]][n[1]] == col) {
                    seen[n[0]][n[1]] = true; q.add(n);
                }
        }
        return out;
    }

    /** Place the current bubble at (r,c); pop clusters of 3+, drop
     *  orphans. @return points gained (0 if the cell is invalid). */
    public int place(int r, int c) {
        if (!inBounds(r, c) || grid[r][c] != 0) return 0;
        grid[r][c] = current;
        shots++;
        int pts = resolve(r, c);
        current = next; next = pickColor();
        return pts;
    }

    private int resolve(int r, int c) {
        int pts = 0;
        List<int[]> cl = cluster(r, c);
        if (cl.size() >= 3) {
            for (int[] p : cl) { grid[p[0]][p[1]] = 0; pts += 10; }
            pts += dropOrphans();
        }
        score += pts;
        return pts;
    }

    /** Remove every bubble no longer connected to the top row.
     *  @return points for dropped bubbles. */
    public int dropOrphans() {
        boolean[][] anchored = new boolean[ROWS][COLS];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int c = 0; c < COLS; c++) if (grid[0][c] > 0) { anchored[0][c] = true; q.add(new int[]{0, c}); }
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            for (int[] n : neighbours(cur[0], cur[1]))
                if (inBounds(n[0], n[1]) && !anchored[n[0]][n[1]] && grid[n[0]][n[1]] > 0) {
                    anchored[n[0]][n[1]] = true; q.add(n);
                }
        }
        int pts = 0;
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++)
            if (grid[r][c] > 0 && !anchored[r][c]) { grid[r][c] = 0; pts += 20; }
        return pts;
    }

    public boolean isWon() {
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) if (grid[r][c] > 0) return false;
        return true;
    }
    /** Deadline is row ROWS-2: a bubble resting there ends the game. */
    public boolean isLost() {
        for (int c = 0; c < COLS; c++) if (grid[ROWS - 2][c] > 0) return true;
        return false;
    }
    public boolean isOver() { return isWon() || isLost(); }
    public int bubbleCount() {
        int n = 0; for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) if (grid[r][c] > 0) n++;
        return n;
    }
    /** Test hook: set a cell directly. */
    public void setCell(int r, int c, int v) { grid[r][c] = v; }
}
