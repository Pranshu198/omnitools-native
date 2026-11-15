package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.Random;

/** Connect Four vs AI. Tap a column to drop; first to four in a row wins. */
public final class Connect4View extends BaseGameView {
    private Connect4Logic g = new Connect4Logic();
    private final Random rnd = new Random();
    private boolean aiThinking;

    public Connect4View(Context c) { super(c); }

    private void scheduleAi() {
        if (g.winner() != Connect4Logic.EMPTY) return;
        aiThinking = true;
        gameHandler().postDelayed(() -> {
            aiThinking = false;
            int col = g.bestMove(Connect4Logic.P2, Connect4Logic.P1, rnd);
            if (col >= 0) g.drop(col, Connect4Logic.P2);
            invalidate();
        }, 400);
    }

    @Override protected void update(long dt) {}
    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        int win = g.winner();
        leftText(cv, "Connect Four", pad, dp(34), 15, Glass.TEXT, true);
        leftText(cv, "Tap: [New Game]", pad, dp(58), 12, Glass.NEON_CYAN, false);
        String st = win == Connect4Logic.P1 ? "You win" : win == Connect4Logic.P2 ? "AI wins"
                : g.isFull() ? "Draw" : aiThinking ? "AI is thinking..." : "Your move — tap a column";
        leftText(cv, st, pad, dp(84), 14, Glass.TEXT_DIM, false);
        float boardW = Math.min(w - pad * 2, getHeight() - dp(150));
        float cell = boardW / Connect4Logic.COLS;
        float x0 = (w - boardW) / 2, y0 = dp(104);
        for (int r = 0; r < Connect4Logic.ROWS; r++) for (int c = 0; c < Connect4Logic.COLS; c++) {
            float l = x0 + c * cell, t = y0 + r * cell;
            glassCell(cv, l + dp(2), t + dp(2), l + cell - dp(2), t + cell - dp(2), dp(10));
            int v = g.get(r, c);
            if (v != Connect4Logic.EMPTY) {
                paint.setColor(v == Connect4Logic.P1 ? Glass.NEON_CYAN : Glass.NEON_VIOLET);
                cv.drawCircle(l + cell / 2, t + cell / 2, cell * 0.34f, paint);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float w = getWidth();
        if (e.getY() >= dp(44) && e.getY() < dp(70)) {
            g = new Connect4Logic(); aiThinking = false; invalidate(); return true;
        }
        if (aiThinking || g.winner() != Connect4Logic.EMPTY) return true;
        float pad = dp(16);
        float boardW = Math.min(w - pad * 2, getHeight() - dp(150));
        float cell = boardW / Connect4Logic.COLS;
        float x0 = (w - boardW) / 2;
        int c = (int) ((e.getX() - x0) / cell);
        if (c < 0 || c >= Connect4Logic.COLS || g.columnFull(c)) return true;
        g.drop(c, Connect4Logic.P1);
        scheduleAi();
        invalidate();
        return true;
    }
}
