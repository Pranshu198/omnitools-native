package com.jackbhai.omnitools.nativ.games;

import java.util.Random;

/** Block Fall (falling-block stacker, original code): 10x20 well,
 *  7 tetrominoes defined by rotation states. Lock delay is instant on
 *  hard drop / one gravity tick on soft landing. Scoring: 100/300/500/
 *  800 per 1/2/3/4 lines times level; level rises every 10 lines. */
public final class BlockFallLogic {
    public static final int W = 10, H = 20;
    /** Rotation states per piece, each state = 4 cells "x,y" in a 4x4 box. */
    private static final String[][] SHAPES = {
        {"1,0 1,1 1,2 1,3", "0,1 1,1 2,1 3,1", "1,0 1,1 1,2 1,3", "0,1 1,1 2,1 3,1"}, // I
        {"1,0 1,1 1,2 2,2", "0,1 1,1 2,1 0,2", "0,0 1,0 1,1 1,2", "2,0 0,1 1,1 2,1"}, // L
        {"1,0 1,1 1,2 0,2", "0,0 0,1 1,1 2,1", "2,0 1,0 1,1 1,2", "0,1 1,1 2,1 2,2"}, // J
        {"1,1 2,1 1,2 2,2", "1,1 2,1 1,2 2,2", "1,1 2,1 1,2 2,2", "1,1 2,1 1,2 2,2"}, // O
        {"1,0 2,0 0,1 1,1", "1,0 1,1 2,1 2,2", "1,1 2,1 0,2 1,2", "0,0 0,1 1,1 1,2"}, // S
        {"0,0 1,0 2,0 1,1", "1,0 0,1 1,1 1,2", "1,0 0,1 1,1 2,1", "1,0 1,1 2,1 1,2"}, // T
        {"0,0 1,0 1,1 2,1", "2,0 1,1 2,1 1,2", "0,1 1,1 1,2 2,2", "1,0 0,1 1,1 0,2"}, // Z
    };
    private static final int[][][] CELLS = new int[7][4][4];
    static {
        for (int p = 0; p < 7; p++) for (int s = 0; s < 4; s++) {
            String[] parts = SHAPES[p][s].split(" ");
            for (int i = 0; i < 4; i++) {
                String[] xy = parts[i].split(",");
                CELLS[p][s][i] = Integer.parseInt(xy[0]) * 100 + Integer.parseInt(xy[1]);
            }
        }
    }
    private final int[][] well = new int[H][W]; // 0 empty, else piece+1
    private final Random rnd;
    public int piece, state, px, py, nextPiece;
    public int score, lines, level = 1;
    public boolean over;

    public BlockFallLogic(Random rnd) {
        this.rnd = rnd;
        nextPiece = rnd.nextInt(7);
        spawn();
    }

    public static int[][] cellsOf(int piece, int state) {
        int[][] out = new int[4][2];
        for (int i = 0; i < 4; i++) { out[i][0] = CELLS[piece][state][i] / 100; out[i][1] = CELLS[piece][state][i] % 100; }
        return out;
    }
    public int at(int r, int c) { return well[r][c]; }

    private boolean fits(int p, int s, int x, int y) {
        for (int[] cell : cellsOf(p, s)) {
            int cx = x + cell[0], cy = y + cell[1];
            if (cx < 0 || cx >= W || cy >= H) return false;
            if (cy >= 0 && well[cy][cx] != 0) return false;
        }
        return true;
    }

    public void spawn() {
        piece = nextPiece; nextPiece = rnd.nextInt(7);
        state = 0; px = 3; py = 0;
        if (!fits(piece, state, px, py)) { py = -1; if (!fits(piece, state, px, py)) over = true; }
    }

    public boolean move(int dx) {
        if (over) return false;
        if (fits(piece, state, px + dx, py)) { px += dx; return true; }
        return false;
    }
    public boolean rotate() {
        if (over) return false;
        int ns = (state + 1) % 4;
        for (int kick : new int[]{0, -1, 1, -2, 2})
            if (fits(piece, ns, px + kick, py)) { state = ns; px += kick; return true; }
        return false;
    }
    /** One gravity step. @return false when the piece locked. */
    public boolean stepDown() {
        if (over) return false;
        if (fits(piece, state, px, py + 1)) { py++; return true; }
        lock();
        return false;
    }
    public void hardDrop() {
        if (over) return;
        while (fits(piece, state, px, py + 1)) { py++; score += 2; }
        lock();
    }

    private void lock() {
        for (int[] cell : cellsOf(piece, state)) {
            int cx = px + cell[0], cy = py + cell[1];
            if (cy < 0) { over = true; continue; }
            well[cy][cx] = piece + 1;
        }
        clearLines();
        if (!over) spawn();
    }

    /** Clear full rows. @return number cleared. */
    public int clearLines() {
        int cleared = 0;
        for (int r = H - 1; r >= 0; r--) {
            boolean full = true;
            for (int c = 0; c < W; c++) if (well[r][c] == 0) { full = false; break; }
            if (full) {
                cleared++;
                for (int rr = r; rr > 0; rr--) System.arraycopy(well[rr - 1], 0, well[rr], 0, W);
                for (int c = 0; c < W; c++) well[0][c] = 0;
                r++; // re-check same row index
            }
        }
        if (cleared > 0) {
            score += new int[]{0, 100, 300, 500, 800}[cleared] * level;
            lines += cleared;
            level = 1 + lines / 10;
        }
        return cleared;
    }
    /** Gravity interval in ms for the current level. */
    public int dropMs() { return Math.max(60, 800 - (level - 1) * 70); }
    /** Test hook. */
    public void setCell(int r, int c, int v) { well[r][c] = v; }
}
