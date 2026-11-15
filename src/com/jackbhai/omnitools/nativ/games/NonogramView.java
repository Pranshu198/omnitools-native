package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.Random;

/** Nonogram: fill cells so each row and column matches its run-length
 *  clues. Tap a cell to fill it, long-press to mark an X. Puzzles are
 *  generated with a proven unique solution. */
public final class NonogramView extends BaseGameView {
    private final Random rnd = new Random();
    private NonogramLogic g = new NonogramLogic(rnd, 5);
    private int size = 5;
    private String level = "Easy 5x5";
    private long downAt;
    private float downX, downY;

    public NonogramView(Context c) { super(c); }

    private void newGame() { g = new NonogramLogic(rnd, size); }

    private float clueW() { return dp(size == 5 ? 44 : 64); }
    private float gridX0() { return dp(16) + clueW(); }
    private float gridY0() { return dp(104) + clueW(); }
    private float cell() { return (getWidth() - gridX0() - dp(16)) / size; }

    @Override protected void update(long dt) {}
    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        leftText(cv, "Nonogram    " + level
                + (best("nonogram_best") > 0 ? "   •   Best: " + best("nonogram_best") : ""), pad, dp(34), 15, Glass.TEXT, true);
        leftText(cv, "Tap: [Level]  [New Game]", pad, dp(58), 12, Glass.NEON_CYAN, false);
        String st = g.isSolved() ? "Picture complete — score " + g.score()
                : "Tap = fill • Long-press = X • Mistakes: " + g.mistakes();
        if (g.isSolved()) saveBest("nonogram_best", g.score());
        leftText(cv, st, pad, dp(84), 14, Glass.TEXT_DIM, false);

        float x0 = gridX0(), y0 = gridY0(), cell = cell();
        // column clues (stacked bottom-aligned above each column)
        for (int c = 0; c < size; c++) {
            int[] cl = g.colClues[c];
            for (int i = 0; i < cl.length; i++) {
                float baseline = y0 - dp(6) - (cl.length - 1 - i) * dp(15);
                centeredText(cv, String.valueOf(cl[i]), x0 + c * cell + cell / 2, baseline + dp(5), 11, Glass.TEXT_DIM, false);
            }
        }
        // row clues (right-aligned left of each row)
        for (int r = 0; r < size; r++) {
            int[] cl = g.rowClues[r];
            StringBuilder sb = new StringBuilder();
            for (int v : cl) { if (sb.length() > 0) sb.append(' '); sb.append(v); }
            textPaint.setTextSize(dp(11)); textPaint.setColor(Glass.TEXT_DIM);
            textPaint.setTypeface(Glass.font(getContext(), false));
            textPaint.setTextAlign(android.graphics.Paint.Align.RIGHT);
            cv.drawText(sb.toString(), x0 - dp(6), y0 + r * cell + cell / 2 + dp(4), textPaint);
            textPaint.setTextAlign(android.graphics.Paint.Align.LEFT);
        }
        for (int r = 0; r < size; r++) for (int c = 0; c < size; c++) {
            float l = x0 + c * cell + dp(1.5f), t = y0 + r * cell + dp(1.5f);
            float rr = l + cell - dp(3), bb = t + cell - dp(3);
            if (g.isFilled(r, c)) {
                boolean wrong = !g.solutionAt(r, c);
                roundRect(cv, l, t, rr, bb, dp(4), wrong ? Color.rgb(255, 92, 92) : Glass.NEON_CYAN);
            } else {
                glassCell(cv, l, t, rr, bb, dp(4));
                if (g.isCrossed(r, c))
                    centeredText(cv, "X", (l + rr) / 2, (t + bb) / 2, size == 5 ? 16 : 11, Glass.TEXT_DIM, false);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downAt = System.currentTimeMillis(); downX = e.getX(); downY = e.getY();
                return true;
            case MotionEvent.ACTION_UP:
                float w = getWidth();
                if (e.getY() >= dp(44) && e.getY() < dp(70)) {
                    if (e.getX() < w * 0.5f) {
                        if (size == 5) { size = 10; level = "Hard 10x10"; } else { size = 5; level = "Easy 5x5"; }
                    }
                    newGame(); invalidate(); return true;
                }
                if (g.isSolved()) return true;
                float x0 = gridX0(), y0 = gridY0(), cell = cell();
                int c = (int) ((e.getX() - x0) / cell), r = (int) ((e.getY() - y0) / cell);
                if (r < 0 || r >= size || c < 0 || c >= size) return true;
                boolean longPress = System.currentTimeMillis() - downAt > 450
                        && Math.abs(e.getX() - downX) < dp(12) && Math.abs(e.getY() - downY) < dp(12);
                if (longPress) g.toggleCross(r, c); else g.toggleFill(r, c);
                invalidate();
                return true;
            default: return true;
        }
    }
}
