package com.jackbhai.omnitools.nativ.games;

/** Pure-Java Tic-Tac-Toe with a minimax AI that never loses. */
public final class TttLogic {
    public static final char EMPTY = ' ', X = 'X', O = 'O';
    private TttLogic() {}

    public static char winner(char[] b) {
        int[][] lines = {{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
        for (int[] l : lines)
            if (b[l[0]] != EMPTY && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]) return b[l[0]];
        return EMPTY;
    }
    public static boolean isFull(char[] b) { for (char c : b) if (c == EMPTY) return false; return true; }
    public static boolean isOver(char[] b) { return winner(b) != EMPTY || isFull(b); }

    private static int minimax(char[] b, char ai, char human, boolean aiTurn, int depth) {
        char w = winner(b);
        if (w == ai) return 10 - depth;
        if (w == human) return depth - 10;
        if (isFull(b)) return 0;
        int best = aiTurn ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            if (b[i] != EMPTY) continue;
            b[i] = aiTurn ? ai : human;
            int s = minimax(b, ai, human, !aiTurn, depth + 1);
            b[i] = EMPTY;
            best = aiTurn ? Math.max(best, s) : Math.min(best, s);
        }
        return best;
    }

    /** Best move index for ai, or -1 if board is terminal/full. */
    public static int bestMove(char[] b, char ai, char human) {
        if (isOver(b)) return -1;
        int bestIdx = -1, bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < 9; i++) {
            if (b[i] != EMPTY) continue;
            b[i] = ai;
            int s = minimax(b, ai, human, false, 0);
            b[i] = EMPTY;
            if (s > bestScore) { bestScore = s; bestIdx = i; }
        }
        return bestIdx;
    }
}
