package com.jackbhai.omnitools.nativ.games;

import java.util.Random;

/** Pure-Java sliding puzzle: tiles 1..n*n-1 plus 0 (blank). Shuffles are
 *  made by random legal moves from the solved state, so every shuffled
 *  board is solvable by construction; isSolvable() parity-checks any board. */
public final class SlideLogic {
    public final int n;
    private final int[] t;
    private int moves;

    public SlideLogic(int n) {
        this.n = n; t = new int[n * n];
        for (int i = 0; i < t.length - 1; i++) t[i] = i + 1;
        t[t.length - 1] = 0;
    }
    public int get(int i) { return t[i]; }
    public int[] tiles() { return t.clone(); }
    public int moves() { return moves; }
    public int blank() { for (int i = 0; i < t.length; i++) if (t[i] == 0) return i; return -1; }

    public boolean isSolved() {
        for (int i = 0; i < t.length - 1; i++) if (t[i] != i + 1) return false;
        return t[t.length - 1] == 0;
    }

    /** Slide tile at index i into the blank if adjacent. */
    public boolean slide(int i) {
        int b = blank();
        int br = b / n, bc = b % n, r = i / n, c = i % n;
        if (Math.abs(br - r) + Math.abs(bc - c) != 1) return false;
        t[b] = t[i]; t[i] = 0; moves++; return true;
    }

    public void shuffle(Random rnd, int steps) {
        int prev = -1;
        for (int s = 0; s < steps; s++) {
            int b = blank(), br = b / n, bc = b % n;
            int[] opts = new int[4]; int m = 0;
            if (br > 0 && b - n != prev) opts[m++] = b - n;
            if (br < n - 1 && b + n != prev) opts[m++] = b + n;
            if (bc > 0 && b - 1 != prev) opts[m++] = b - 1;
            if (bc < n - 1 && b + 1 != prev) opts[m++] = b + 1;
            int pick = opts[rnd.nextInt(m)];
            prev = b;
            t[b] = t[pick]; t[pick] = 0;
        }
        moves = 0;
        if (isSolved()) shuffle(rnd, steps);
    }

    /** Inversion-parity solvability for any arrangement. */
    public static boolean isSolvable(int[] tiles, int n) {
        int inv = 0;
        for (int i = 0; i < tiles.length; i++)
            for (int j = i + 1; j < tiles.length; j++)
                if (tiles[i] != 0 && tiles[j] != 0 && tiles[i] > tiles[j]) inv++;
        if (n % 2 == 1) return inv % 2 == 0;
        int blankRowFromBottom = 0;
        for (int i = 0; i < tiles.length; i++) if (tiles[i] == 0) blankRowFromBottom = n - (i / n);
        return (blankRowFromBottom % 2 == 0) == (inv % 2 == 1);
    }
}
