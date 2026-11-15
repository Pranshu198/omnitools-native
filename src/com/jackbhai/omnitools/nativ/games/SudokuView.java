package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.Random;

/** Sudoku: generated puzzle with a unique solution. Givens are locked;
 *  tap a cell, then a number. Conflicts glow red; win when complete. */
public final class SudokuView extends BaseGameView {
    private static final int WARN = Color.rgb(255, 92, 92);
    private final Random rnd = new Random();
    private SudokuLogic g;
    private final int[][] user = new int[9][9];
    private int givens = SudokuLogic.EASY;
    private String level = "Easy";
    private int selR = -1, selC = -1, mistakes;

    public SudokuView(Context c) { super(c); newGame(); }

    private void newGame() {
        g = new SudokuLogic(rnd, givens);
        for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++) user[r][c] = g.puzzleAt(r, c);
        selR = selC = -1; mistakes = 0;
    }
    private boolean done() { return g.isComplete(user); }

    @Override protected void update(long dt) {}
    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        leftText(cv, "Sudoku    " + level + "  •  Mistakes: " + mistakes, pad, dp(34), 15, Glass.TEXT, true);
        leftText(cv, "Tap: [Level]  [New Game]", pad, dp(58), 12, Glass.NEON_CYAN, false);
        leftText(cv, done() ? "Solved — well done" : "Fill every row, column and box with 1–9",
                pad, dp(84), 14, done() ? Glass.NEON_CYAN : Glass.TEXT_DIM, false);
        float board = Math.min(w - pad * 2, getHeight() - dp(300));
        float x0 = (w - board) / 2, y0 = dp(100), cell = board / 9f;
        for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++) {
            float l = x0 + c * cell, t = y0 + r * cell;
            boolean hot = selR >= 0 && (r == selR || c == selC
                    || (r / 3 == selR / 3 && c / 3 == selC / 3));
            roundRect(cv, l + dp(1), t + dp(1), l + cell - dp(1), t + cell - dp(1), dp(3),
                    r == selR && c == selC ? Glass.GLASS_FILL_STRONG
                            : hot ? Glass.GLASS_FILL : Glass.BG_DEEP);
            int v = user[r][c];
            if (v != 0) {
                boolean given = g.isGiven(r, c);
                int col = given ? Glass.TEXT : Glass.NEON_CYAN;
                if (!given && g.hasConflict(user, r, c)) col = WARN;
                centeredText(cv, String.valueOf(v), l + cell / 2, t + cell / 2, 19, col, given);
            }
        }
        paint.setColor(Glass.GLASS_BORDER); paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        for (int i = 0; i <= 3; i++) {
            cv.drawLine(x0 + i * cell * 3, y0, x0 + i * cell * 3, y0 + board, paint);
            cv.drawLine(x0, y0 + i * cell * 3, x0 + board, y0 + i * cell * 3, paint);
        }
        paint.setStyle(android.graphics.Paint.Style.FILL);
        // number pad
        float py = y0 + board + dp(18), pw = (w - pad * 2) / 10f;
        for (int n = 0; n <= 9; n++) {
            float l = pad + n * pw + dp(2), rr = pad + (n + 1) * pw - dp(2);
            glassCell(cv, l, py, rr, py + dp(48), dp(10));
            centeredText(cv, n == 9 ? "X" : String.valueOf(n + 1), (l + rr) / 2, py + dp(24), 17,
                    Glass.NEON_CYAN, true);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float w = getWidth(), pad = dp(16);
        if (e.getY() >= dp(44) && e.getY() < dp(70)) {
            if (e.getX() < w * 0.5f) {
                if (givens == SudokuLogic.EASY) { givens = SudokuLogic.MEDIUM; level = "Medium"; }
                else if (givens == SudokuLogic.MEDIUM) { givens = SudokuLogic.HARD; level = "Hard"; }
                else { givens = SudokuLogic.EASY; level = "Easy"; }
            }
            newGame(); invalidate(); return true;
        }
        float board = Math.min(w - pad * 2, getHeight() - dp(300));
        float x0 = (w - board) / 2, y0 = dp(100), cell = board / 9f;
        float py = y0 + board + dp(18), pw = (w - pad * 2) / 10f;
        if (e.getY() >= py && e.getY() <= py + dp(48) && selR >= 0 && !g.isGiven(selR, selC) && !done()) {
            int n = (int) ((e.getX() - pad) / pw);
            if (n >= 0 && n <= 9) {
                int v = n == 9 ? 0 : n + 1;
                if (v != 0 && v != g.solutionAt(selR, selC) && user[selR][selC] != v) mistakes++;
                user[selR][selC] = v;
            }
            invalidate(); return true;
        }
        int c = (int) ((e.getX() - x0) / cell), r = (int) ((e.getY() - y0) / cell);
        if (r >= 0 && r < 9 && c >= 0 && c < 9) { selR = r; selC = c; }
        invalidate();
        return true;
    }
}
