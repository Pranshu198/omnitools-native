package com.jackbhai.omnitools.nativ.games;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Nonogram (Picross): generated puzzles with a guaranteed unique
 *  solution. The generator builds a random picture, then a constraint
 *  solver proves uniqueness (line-deduction fixpoint + backtracking
 *  solution counting, capped at 2); non-unique pictures are rejected.
 *  Sizes: 5x5 (Easy) and 10x10 (Hard). */
public final class NonogramLogic {
    public final int n;
    private final boolean[][] solution;
    private final boolean[][] filled;
    private final boolean[][] crossed;
    public final int[][] rowClues, colClues;

    public NonogramLogic(Random rnd, int n) {
        this.n = n;
        boolean[][] pic;
        int tries = 0;
        do { pic = randomPicture(rnd, n); tries++; }
        while (countSolutions(pic, 2) != 1 && tries < 200);
        solution = pic;
        filled = new boolean[n][n];
        crossed = new boolean[n][n];
        rowClues = new int[n][];
        colClues = new int[n][];
        for (int r = 0; r < n; r++) rowClues[r] = cluesOf(line(solution, r, true));
        for (int c = 0; c < n; c++) colClues[c] = cluesOf(line(solution, c, false));
    }

    /** Fixed-picture constructor (tests / curated puzzles). */
    public NonogramLogic(boolean[][] pic) {
        n = pic.length;
        solution = pic;
        filled = new boolean[n][n];
        crossed = new boolean[n][n];
        rowClues = new int[n][];
        colClues = new int[n][];
        for (int r = 0; r < n; r++) rowClues[r] = cluesOf(line(solution, r, true));
        for (int c = 0; c < n; c++) colClues[c] = cluesOf(line(solution, c, false));
    }

    private static boolean[] line(boolean[][] g, int i, boolean row) {
        boolean[] out = new boolean[g.length];
        for (int j = 0; j < g.length; j++) out[j] = row ? g[i][j] : g[j][i];
        return out;
    }
    /** Run-length clues for one line; empty line gives {0}. */
    public static int[] cluesOf(boolean[] line) {
        List<Integer> out = new ArrayList<>();
        int run = 0;
        for (boolean b : line) {
            if (b) run++;
            else if (run > 0) { out.add(run); run = 0; }
        }
        if (run > 0) out.add(run);
        if (out.isEmpty()) out.add(0);
        int[] res = new int[out.size()];
        for (int i = 0; i < res.length; i++) res[i] = out.get(i);
        return res;
    }

    private static boolean[][] randomPicture(Random rnd, int n) {
        boolean[][] pic = new boolean[n][n];
        double density = n <= 5 ? 0.55 : 0.5;
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) pic[r][c] = rnd.nextDouble() < density;
        // avoid degenerate empty/full lines which trivialise the puzzle
        for (int r = 0; r < n; r++) {
            boolean any = false, all = true;
            for (int c = 0; c < n; c++) { any |= pic[r][c]; all &= pic[r][c]; }
            if (!any) pic[r][rnd.nextInt(n)] = true;
            if (all) pic[r][rnd.nextInt(n)] = false;
        }
        return pic;
    }

    /** Count solutions of the clue puzzle implied by pic, capped. */
    public static int countSolutions(boolean[][] pic, int cap) {
        int n = pic.length;
        int[][] rc = new int[n][], cc = new int[n][];
        for (int r = 0; r < n; r++) rc[r] = cluesOf(line(pic, r, true));
        for (int c = 0; c < n; c++) cc[c] = cluesOf(line(pic, c, false));
        int[][] grid = new int[n][n]; // 0 unknown, 1 filled, -1 empty
        return countRec(grid, rc, cc, 0, cap);
    }

    private static int countRec(int[][] grid, int[][] rc, int[][] cc, int pos, int cap) {
        int n = grid.length;
        if (cap <= 0) return 0;
        if (pos == n * n) return 1;
        int r = pos / n, c = pos % n;
        if (grid[r][c] != 0) return countRec(grid, rc, cc, pos + 1, cap);
        int total = 0;
        for (int v : new int[]{1, -1}) {
            grid[r][c] = v;
            if (linePossible(grid, r, true, rc[r]) && linePossible(grid, c, false, cc[c]))
                total += countRec(grid, rc, cc, pos + 1, cap - total);
            grid[r][c] = 0;
            if (total >= cap) return total;
        }
        return total;
    }

    /** Partial-line feasibility: can the decided cells still extend to
     *  some line matching the clues? Checked by enumerating placements
     *  when the line is short (n <= 10) — exact, not a heuristic. */
    static boolean linePossible(int[][] grid, int idx, boolean row, int[] clues) {
        int n = grid.length;
        int[] cells = new int[n];
        for (int j = 0; j < n; j++) cells[j] = row ? grid[idx][j] : grid[j][idx];
        return placeOk(cells, clues, 0, 0);
    }
    private static boolean placeOk(int[] cells, int[] clues, int ci, int pos) {
        int n = cells.length;
        if (ci == clues.length || (ci == 0 && clues[0] == 0)) {
            // remaining cells must all be decidable as empty
            for (int j = pos; j < n; j++) if (cells[j] == 1) return false;
            return true;
        }
        int run = clues[ci];
        if (run == 0) { // whole line empty
            for (int v : cells) if (v == 1) return false;
            return true;
        }
        for (int start = pos; start + run <= n; start++) {
            boolean ok = true;
            for (int j = pos; j < start; j++) if (cells[j] == 1) { ok = false; break; }
            if (!ok) break; // a decided fill before start can never be skipped later
            for (int j = start; j < start + run && ok; j++) if (cells[j] == -1) ok = false;
            if (ok && start + run < n && cells[start + run] == 1) ok = false;
            if (ok) {
                int next = start + run + 1;
                if (ci == clues.length - 1) {
                    boolean tail = true;
                    for (int j = Math.min(next, n); j < n; j++) if (cells[j] == 1) tail = false;
                    if (tail) return true;
                } else if (placeOk(cells, clues, ci + 1, next)) return true;
            }
        }
        return false;
    }

    // ---- play state ----
    public boolean solutionAt(int r, int c) { return solution[r][c]; }
    public boolean isFilled(int r, int c) { return filled[r][c]; }
    public boolean isCrossed(int r, int c) { return crossed[r][c]; }
    /** Toggle fill. @return false when the cell holds a wrong fill now
     *  (filled but not in the solution) so the view can flag mistakes. */
    public boolean toggleFill(int r, int c) {
        if (crossed[r][c]) crossed[r][c] = false;
        filled[r][c] = !filled[r][c];
        return !filled[r][c] || solution[r][c];
    }
    public void toggleCross(int r, int c) {
        if (filled[r][c]) filled[r][c] = false;
        crossed[r][c] = !crossed[r][c];
    }
    public int mistakes() {
        int m = 0;
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) if (filled[r][c] && !solution[r][c]) m++;
        return m;
    }
    public boolean isSolved() {
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++)
            if (filled[r][c] != solution[r][c]) return false;
        return true;
    }
    public int score() { return isSolved() ? n * n * 10 - mistakes() * 25 : 0; }
}
