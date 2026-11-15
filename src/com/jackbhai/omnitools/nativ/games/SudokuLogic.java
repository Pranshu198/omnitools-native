package com.jackbhai.omnitools.nativ.games;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Pure-Java Sudoku: full-grid generator, backtracking solver with
 *  solution counting, and a digger that keeps the puzzle uniquely
 *  solvable. Difficulty = number of givens. */
public final class SudokuLogic {
    public static final int EASY = 40, MEDIUM = 32, HARD = 26;
    private final int[][] solution = new int[9][9];
    private final int[][] puzzle = new int[9][9];
    private final boolean[][] given = new boolean[9][9];

    public SudokuLogic(Random rnd, int givens) {
        generateSolved(rnd);
        for (int r = 0; r < 9; r++) System.arraycopy(solution[r], 0, puzzle[r], 0, 9);
        dig(rnd, givens);
        for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++) given[r][c] = puzzle[r][c] != 0;
    }

    public int solutionAt(int r, int c) { return solution[r][c]; }
    public int puzzleAt(int r, int c) { return puzzle[r][c]; }
    public boolean isGiven(int r, int c) { return given[r][c]; }
    public int givenCount() {
        int n = 0; for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++) if (given[r][c]) n++;
        return n;
    }

    private void generateSolved(Random rnd) {
        fill(solution, 0, rnd);
    }
    private boolean fill(int[][] g, int pos, Random rnd) {
        if (pos == 81) return true;
        int r = pos / 9, c = pos % 9;
        List<Integer> nums = new ArrayList<>();
        for (int n = 1; n <= 9; n++) nums.add(n);
        Collections.shuffle(nums, rnd);
        for (int n : nums) {
            if (ok(g, r, c, n)) { g[r][c] = n; if (fill(g, pos + 1, rnd)) return true; g[r][c] = 0; }
        }
        return false;
    }
    static boolean ok(int[][] g, int r, int c, int n) {
        for (int i = 0; i < 9; i++) {
            if (g[r][i] == n || g[i][c] == n) return false;
            if (g[r - r % 3 + i / 3][c - c % 3 + i % 3] == n) return false;
        }
        return true;
    }
    /** Valid complete grid check (rows/cols/boxes all 1..9). */
    public static boolean isValidSolution(int[][] g) {
        for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++) {
            int v = g[r][c];
            if (v < 1 || v > 9) return false;
            g[r][c] = 0;
            boolean good = ok(g, r, c, v);
            g[r][c] = v;
            if (!good) return false;
        }
        return true;
    }
    /** Count solutions of a puzzle, capped at cap (for uniqueness tests). */
    public static int countSolutions(int[][] p, int cap) {
        int[][] g = new int[9][9];
        for (int r = 0; r < 9; r++) System.arraycopy(p[r], 0, g[r], 0, 9);
        return countRec(g, 0, cap);
    }
    private static int countRec(int[][] g, int pos, int cap) {
        if (cap <= 0) return 0;
        if (pos == 81) return 1;
        int r = pos / 9, c = pos % 9;
        if (g[r][c] != 0) return countRec(g, pos + 1, cap);
        int total = 0;
        for (int n = 1; n <= 9; n++) {
            if (ok(g, r, c, n)) {
                g[r][c] = n;
                total += countRec(g, pos + 1, cap - total);
                g[r][c] = 0;
                if (total >= cap) return total;
            }
        }
        return total;
    }
    /** Solve in place; returns true if a solution exists. */
    public static boolean solve(int[][] p) {
        return solveRec(p, 0);
    }
    private static boolean solveRec(int[][] g, int pos) {
        if (pos == 81) return true;
        int r = pos / 9, c = pos % 9;
        if (g[r][c] != 0) return solveRec(g, pos + 1);
        for (int n = 1; n <= 9; n++) {
            if (ok(g, r, c, n)) { g[r][c] = n; if (solveRec(g, pos + 1)) return true; g[r][c] = 0; }
        }
        return false;
    }
    private void dig(Random rnd, int targetGivens) {
        List<Integer> cells = new ArrayList<>();
        for (int i = 0; i < 81; i++) cells.add(i);
        Collections.shuffle(cells, rnd);
        int remaining = 81;
        for (int pos : cells) {
            if (remaining <= targetGivens) break;
            int r = pos / 9, c = pos % 9;
            int keep = puzzle[r][c];
            puzzle[r][c] = 0;
            if (countSolutions(puzzle, 2) != 1) puzzle[r][c] = keep; // keep uniqueness
            else remaining--;
        }
    }
    /** Check a user board against the stored solution. */
    public boolean isComplete(int[][] user) {
        for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++)
            if (user[r][c] != solution[r][c]) return false;
        return true;
    }
    /** Conflicts in a user board (ignoring correctness vs solution). */
    public boolean hasConflict(int[][] user, int r, int c) {
        int v = user[r][c];
        if (v == 0) return false;
        for (int i = 0; i < 9; i++) {
            if (i != c && user[r][i] == v) return true;
            if (i != r && user[i][c] == v) return true;
            int br = r - r % 3 + i / 3, bc = c - c % 3 + i % 3;
            if ((br != r || bc != c) && user[br][bc] == v) return true;
        }
        return false;
    }
}
