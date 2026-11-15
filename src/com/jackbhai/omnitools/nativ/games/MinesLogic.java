package com.jackbhai.omnitools.nativ.games;

import java.util.Random;

/** Pure-Java Minesweeper logic. Mines are placed on first reveal so the
 *  first cell and its neighbours are always mine-free. */
public final class MinesLogic {
    public final int rows, cols, mineCount;
    private final boolean[][] mine, revealed, flagged;
    private final int[][] adj;
    private final Random rnd;
    private boolean placed, lost, won;
    private int revealedCount;

    public MinesLogic(int rows, int cols, int mineCount, Random rnd) {
        this.rows = rows; this.cols = cols; this.mineCount = mineCount; this.rnd = rnd;
        mine = new boolean[rows][cols]; revealed = new boolean[rows][cols];
        flagged = new boolean[rows][cols]; adj = new int[rows][cols];
    }
    public MinesLogic() { this(9, 9, 10, new Random()); }

    public boolean isMine(int r, int c) { return mine[r][c]; }
    public boolean isRevealed(int r, int c) { return revealed[r][c]; }
    public boolean isFlagged(int r, int c) { return flagged[r][c]; }
    public int adjacent(int r, int c) { return adj[r][c]; }
    public boolean isLost() { return lost; }
    public boolean isWon() { return won; }
    public boolean minesPlaced() { return placed; }
    public int revealedCount() { return revealedCount; }
    public int flagCount() { int n = 0; for (boolean[] row : flagged) for (boolean f : row) if (f) n++; return n; }

    public void placeMines(int safeR, int safeC) {
        if (placed) return;
        int placedN = 0;
        while (placedN < mineCount) {
            int r = rnd.nextInt(rows), c = rnd.nextInt(cols);
            if (mine[r][c]) continue;
            if (Math.abs(r - safeR) <= 1 && Math.abs(c - safeC) <= 1) continue;
            mine[r][c] = true; placedN++;
        }
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            int n = 0;
            for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) {
                int nr = r + dr, nc = c + dc;
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && mine[nr][nc]) n++;
            }
            adj[r][c] = n;
        }
        placed = true;
    }

    public void toggleFlag(int r, int c) { if (!revealed[r][c] && !lost && !won) flagged[r][c] = !flagged[r][c]; }

    /** Reveal a cell with flood fill on zero-adjacency cells.
     *  @return number of newly revealed cells. */
    public int reveal(int r, int c) {
        if (lost || won || flagged[r][c] || revealed[r][c]) return 0;
        if (!placed) placeMines(r, c);
        if (mine[r][c]) { revealed[r][c] = true; revealedCount++; lost = true; return 1; }
        int before = revealedCount;
        flood(r, c);
        if (revealedCount == rows * cols - mineCount) won = true;
        return revealedCount - before;
    }
    private void flood(int r, int c) {
        if (r < 0 || r >= rows || c < 0 || c >= cols) return;
        if (revealed[r][c] || flagged[r][c] || mine[r][c]) return;
        revealed[r][c] = true; revealedCount++;
        if (adj[r][c] != 0) return;
        for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++)
            if (dr != 0 || dc != 0) flood(r + dr, c + dc);
    }
    public void revealAllMines() {
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++)
            if (mine[r][c]) revealed[r][c] = true;
    }
}
