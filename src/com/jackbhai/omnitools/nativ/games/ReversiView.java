package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.List;

/** Reversi (Othello) vs AI. You are Black and move first. Legal moves
 *  are dotted; a side with no move passes. Most discs at the end wins. */
public final class ReversiView extends BaseGameView {
    private ReversiLogic g = new ReversiLogic();
    private boolean aiThinking;

    public ReversiView(Context c) { super(c); }

    private void afterPlayer() {
        if (g.isOver()) { invalidate(); return; }
        if (!g.legalMoves(ReversiLogic.WHITE).isEmpty()) scheduleAi();
        else if (!g.legalMoves(ReversiLogic.BLACK).isEmpty()) invalidate(); // AI passes
        else invalidate();
    }
    private void scheduleAi() {
        aiThinking = true;
        gameHandler().postDelayed(() -> {
            aiThinking = false;
            int[] mv = g.bestMove(ReversiLogic.WHITE);
            if (mv != null) g.place(mv[0], mv[1], ReversiLogic.WHITE);
            // if player has no move, AI goes again
            if (!g.isOver() && g.legalMoves(ReversiLogic.BLACK).isEmpty()
                    && !g.legalMoves(ReversiLogic.WHITE).isEmpty()) scheduleAi();
            invalidate();
        }, 400);
    }

    @Override protected void update(long dt) {}
    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        boolean over = g.isOver();
        int win = g.winner();
        leftText(cv, "Reversi    You " + g.count(ReversiLogic.BLACK)
                + "  •  AI " + g.count(ReversiLogic.WHITE), pad, dp(34), 15, Glass.TEXT, true);
        leftText(cv, "Tap: [New Game]", pad, dp(58), 12, Glass.NEON_CYAN, false);
        String st = over ? (win == ReversiLogic.BLACK ? "You win" : win == ReversiLogic.WHITE
                ? "AI wins" : "Draw")
                : aiThinking ? "AI is thinking..."
                : g.legalMoves(ReversiLogic.BLACK).isEmpty() ? "No moves — you pass" : "Your move";
        leftText(cv, st, pad, dp(84), 14, Glass.TEXT_DIM, false);
        float board = Math.min(w - pad * 2, getHeight() - dp(120));
        float x0 = (w - board) / 2, y0 = dp(100), cell = board / 8f;
        List<int[]> legal = g.legalMoves(ReversiLogic.BLACK);
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            float l = x0 + c * cell, t = y0 + r * cell;
            glassCell(cv, l + dp(1), t + dp(1), l + cell - dp(1), t + cell - dp(1), dp(5));
            int v = g.get(r, c);
            if (v != ReversiLogic.EMPTY) {
                paint.setColor(v == ReversiLogic.BLACK ? Glass.NEON_CYAN : Glass.NEON_VIOLET);
                cv.drawCircle(l + cell / 2, t + cell / 2, cell * 0.36f, paint);
            } else if (!over && !aiThinking) {
                for (int[] m : legal) if (m[0] == r && m[1] == c) {
                    paint.setColor(Glass.NEON_CYAN);
                    cv.drawCircle(l + cell / 2, t + cell / 2, dp(4), paint);
                }
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float w = getWidth();
        if (e.getY() >= dp(44) && e.getY() < dp(70)) {
            g = new ReversiLogic(); aiThinking = false; invalidate(); return true;
        }
        if (aiThinking || g.isOver()) return true;
        float pad = dp(16);
        float board = Math.min(w - pad * 2, getHeight() - dp(120));
        float x0 = (w - board) / 2, y0 = dp(100), cell = board / 8f;
        int c = (int) ((e.getX() - x0) / cell), r = (int) ((e.getY() - y0) / cell);
        if (r < 0 || r >= 8 || c < 0 || c >= 8) return true;
        if (g.place(r, c, ReversiLogic.BLACK) < 0) return true;
        afterPlayer();
        invalidate();
        return true;
    }
}
