package com.jackbhai.omnitools.nativ.games;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Pure-Java Checkers (English draughts, 8x8): forced captures,
 *  multi-jumps, kings. AI = depth-limited alpha-beta over move list. */
public final class CheckersLogic {
    public static final int EMPTY = 0, RED = 1, RED_KING = 2, BLACK = 3, BLACK_KING = 4;
    private final int[][] b = new int[8][8];

    public CheckersLogic() {
        for (int r = 0; r < 3; r++) for (int c = 0; c < 8; c++) if ((r + c) % 2 == 1) b[r][c] = BLACK;
        for (int r = 5; r < 8; r++) for (int c = 0; c < 8; c++) if ((r + c) % 2 == 1) b[r][c] = RED;
    }
    public CheckersLogic(int[][] board) {
        for (int r = 0; r < 8; r++) System.arraycopy(board[r], 0, b[r], 0, 8);
    }
    public int get(int r, int c) { return b[r][c]; }
    public int[][] boardCopy() {
        int[][] out = new int[8][8];
        for (int r = 0; r < 8; r++) System.arraycopy(b[r], 0, out[r], 0, 8);
        return out;
    }
    public static int sideOf(int piece) {
        return (piece == RED || piece == RED_KING) ? RED : (piece == BLACK || piece == BLACK_KING) ? BLACK : EMPTY;
    }
    public static int other(int side) { return side == RED ? BLACK : RED; }
    static boolean isKing(int p) { return p == RED_KING || p == BLACK_KING; }
    private static int[] dirsFor(int piece) {
        if (isKing(piece)) return new int[]{-1, 1};
        return sideOf(piece) == RED ? new int[]{-1} : new int[]{1}; // RED moves up, BLACK down
    }

    /** A move: full path of squares visited, jumps implied by distance. */
    public static final class Move {
        public final List<int[]> path = new ArrayList<>();
        public boolean capture;
        @Override public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int[] p : path) { if (sb.length() > 0) sb.append(capture ? "x" : "-"); sb.append(p[0]).append(',').append(p[1]); }
            return sb.toString();
        }
    }

    /** All legal moves for side; if any capture exists, only captures. */
    public List<Move> legalMoves(int side) {
        List<Move> captures = new ArrayList<>();
        List<Move> quiet = new ArrayList<>();
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            if (sideOf(b[r][c]) != side) continue;
            List<Move> caps = new ArrayList<>();
            Move start = new Move(); start.capture = true; start.path.add(new int[]{r, c});
            jumpDfs(b, r, c, b[r][c], start, caps);
            captures.addAll(caps);
            if (captures.isEmpty() || true) {
                for (int dr : dirsFor(b[r][c])) for (int dc : new int[]{-1, 1}) {
                    int nr = r + dr, nc = c + dc;
                    if (nr >= 0 && nc >= 0 && nr < 8 && nc < 8 && b[nr][nc] == EMPTY) {
                        Move m = new Move(); m.path.add(new int[]{r, c}); m.path.add(new int[]{nr, nc});
                        quiet.add(m);
                    }
                }
            }
        }
        return captures.isEmpty() ? quiet : captures;
    }

    private static void jumpDfs(int[][] bd, int r, int c, int piece, Move cur, List<Move> out) {
        boolean extended = false;
        for (int dr : dirsFor(piece)) for (int dc : new int[]{-1, 1}) {
            int mr = r + dr, mc = c + dc, lr = r + 2 * dr, lc = c + 2 * dc;
            if (lr < 0 || lc < 0 || lr >= 8 || lc >= 8) continue;
            int mid = bd[mr][mc];
            if (sideOf(mid) == other(sideOf(piece)) && sideOf(mid) != EMPTY && bd[lr][lc] == EMPTY) {
                // apply jump on the scratch board
                bd[r][c] = EMPTY; bd[mr][mc] = EMPTY; bd[lr][lc] = piece;
                cur.path.add(new int[]{lr, lc});
                boolean crowned = !isKing(piece) && (lr == 0 || lr == 7);
                if (crowned) {
                    out.add(copyOf(cur)); // crowning ends the turn
                } else {
                    jumpDfs(bd, lr, lc, piece, cur, out);
                }
                cur.path.remove(cur.path.size() - 1);
                bd[r][c] = piece; bd[mr][mc] = mid; bd[lr][lc] = EMPTY;
                extended = true;
            }
        }
        if (!extended && cur.path.size() > 1) out.add(copyOf(cur));
    }
    private static Move copyOf(Move m) {
        Move n = new Move(); n.capture = m.capture;
        for (int[] p : m.path) n.path.add(new int[]{p[0], p[1]});
        return n;
    }

    /** Apply a legal move to this board. */
    public void apply(Move m) {
        int[] from = m.path.get(0);
        int piece = b[from[0]][from[1]];
        b[from[0]][from[1]] = EMPTY;
        for (int i = 1; i < m.path.size(); i++) {
            int[] prev = m.path.get(i - 1), cur = m.path.get(i);
            if (Math.abs(cur[0] - prev[0]) == 2) b[(cur[0] + prev[0]) / 2][(cur[1] + prev[1]) / 2] = EMPTY;
        }
        int[] end = m.path.get(m.path.size() - 1);
        if (!isKing(piece) && end[0] == 0 && sideOf(piece) == RED) piece = RED_KING;
        if (!isKing(piece) && end[0] == 7 && sideOf(piece) == BLACK) piece = BLACK_KING;
        b[end[0]][end[1]] = piece;
    }

    public int count(int side) {
        int n = 0;
        for (int[] row : b) for (int v : row) if (sideOf(v) == side) n++;
        return n;
    }
    /** Winner side or EMPTY if game continues. Side to move with no moves loses. */
    public int winner(int sideToMove) {
        if (count(RED) == 0) return BLACK;
        if (count(BLACK) == 0) return RED;
        if (legalMoves(sideToMove).isEmpty()) return other(sideToMove);
        return EMPTY;
    }

    private static int evaluate(int[][] bd, int aiSide) {
        int s = 0;
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            int v = bd[r][c]; if (v == EMPTY) continue;
            int val = isKing(v) ? 300 : 100;
            if (!isKing(v)) val += (sideOf(v) == BLACK ? r : 7 - r) * 4; // advancement
            if (c >= 2 && c <= 5) val += 4;
            s += sideOf(v) == aiSide ? val : -val;
        }
        return s;
    }
    private static int search(int[][] bd, int side, int aiSide, int depth, int alpha, int beta) {
        CheckersLogic g = new CheckersLogic(bd);
        List<Move> moves = g.legalMoves(side);
        if (depth == 0 || moves.isEmpty() || g.count(RED) == 0 || g.count(BLACK) == 0) {
            if (moves.isEmpty()) return side == aiSide ? -100000 - depth : 100000 + depth;
            return evaluate(bd, aiSide);
        }
        boolean maxing = side == aiSide;
        int best = maxing ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (Move m : moves) {
            int[][] next = g.boardCopy();
            new CheckersLogic(next).apply(m);
            int v = search(next, other(side), aiSide, depth - 1, alpha, beta);
            if (maxing) { best = Math.max(best, v); alpha = Math.max(alpha, v); }
            else { best = Math.min(best, v); beta = Math.min(beta, v); }
            if (beta <= alpha) break;
        }
        return best;
    }
    /** Alpha-beta AI move; null if no legal move. Depth 4 default. */
    public Move bestMove(int side, Random rnd) { return bestMove(side, 4, rnd); }
    public Move bestMove(int side, int depth, Random rnd) {
        List<Move> moves = legalMoves(side);
        if (moves.isEmpty()) return null;
        Move best = null; int bestVal = Integer.MIN_VALUE;
        for (Move m : moves) {
            int[][] next = boardCopy();
            new CheckersLogic(next).apply(m);
            int v = search(next, other(side), side, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE);
            v += rnd.nextInt(5);
            if (v > bestVal) { bestVal = v; best = m; }
        }
        return best;
    }
}
