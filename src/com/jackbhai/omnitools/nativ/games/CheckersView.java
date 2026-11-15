package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.List;
import java.util.Random;

/** Checkers vs AI. You are Red (bottom). Captures are forced; a full
 *  jump chain is one move. Difficulty sets AI search depth. */
public final class CheckersView extends BaseGameView {
    private CheckersLogic g = new CheckersLogic();
    private final Random rnd = new Random();
    private int selR = -1, selC = -1;
    private int depth = 4;
    private String level = "Hard";
    private boolean aiThinking;

    public CheckersView(Context c) { super(c); }

    private List<CheckersLogic.Move> myMoves() { return g.legalMoves(CheckersLogic.RED); }
    private CheckersLogic.Move moveFor(int fr, int fc, int tr, int tc) {
        for (CheckersLogic.Move m : myMoves()) {
            int[] f = m.path.get(0), e = m.path.get(m.path.size() - 1);
            if (f[0] == fr && f[1] == fc && e[0] == tr && e[1] == tc) return m;
        }
        return null;
    }
    private boolean canLand(int r, int c) {
        return selR >= 0 && moveFor(selR, selC, r, c) != null;
    }
    private void scheduleAi() {
        if (g.winner(CheckersLogic.BLACK) != CheckersLogic.EMPTY) return;
        aiThinking = true;
        gameHandler().postDelayed(() -> {
            aiThinking = false;
            CheckersLogic.Move m = g.bestMove(CheckersLogic.BLACK, depth, rnd);
            if (m != null) g.apply(m);
            invalidate();
        }, 400);
    }

    @Override protected void update(long dt) {}
    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        int win = g.winner(CheckersLogic.RED);
        leftText(cv, "Checkers    AI: " + level, pad, dp(34), 15, Glass.TEXT, true);
        leftText(cv, "Tap: [Level]  [New Game]", pad, dp(58), 12, Glass.NEON_CYAN, false);
        String st = win == CheckersLogic.RED ? "You win" : win == CheckersLogic.BLACK ? "AI wins"
                : aiThinking ? "AI is thinking..." : "Your move — red " + g.count(CheckersLogic.RED)
                + " vs " + g.count(CheckersLogic.BLACK);
        leftText(cv, st, pad, dp(84), 14, Glass.TEXT_DIM, false);
        float board = Math.min(w - pad * 2, getHeight() - dp(120));
        float x0 = (w - board) / 2, y0 = dp(100), cell = board / 8f;
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            float l = x0 + c * cell, t = y0 + r * cell;
            boolean dark = (r + c) % 2 == 1;
            roundRect(cv, l + dp(1), t + dp(1), l + cell - dp(1), t + cell - dp(1), dp(4),
                    dark ? Glass.GLASS_FILL_STRONG : Glass.GLASS_FILL);
            if (r == selR && c == selC) {
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setColor(Glass.NEON_CYAN); paint.setStrokeWidth(dp(2));
                rect.set(l + dp(2), t + dp(2), l + cell - dp(2), t + cell - dp(2));
                cv.drawRoundRect(rect, dp(4), dp(4), paint);
                paint.setStyle(android.graphics.Paint.Style.FILL);
            }
            if (canLand(r, c)) {
                paint.setColor(Glass.NEON_CYAN);
                cv.drawCircle(l + cell / 2, t + cell / 2, dp(5), paint);
            }
            int p = g.get(r, c);
            if (p != CheckersLogic.EMPTY) {
                int side = CheckersLogic.sideOf(p);
                paint.setColor(side == CheckersLogic.RED ? Glass.NEON_CYAN : Glass.NEON_VIOLET);
                cv.drawCircle(l + cell / 2, t + cell / 2, cell * 0.36f, paint);
                if (p == CheckersLogic.RED_KING || p == CheckersLogic.BLACK_KING) {
                    paint.setColor(Glass.BG_DEEP);
                    cv.drawCircle(l + cell / 2, t + cell / 2, cell * 0.16f, paint);
                }
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float w = getWidth();
        if (e.getY() >= dp(44) && e.getY() < dp(70)) {
            if (e.getX() < w * 0.5f) {
                if (depth == 4) { depth = 2; level = "Medium"; }
                else if (depth == 2) { depth = 1; level = "Easy"; }
                else { depth = 4; level = "Hard"; }
            } else {
                g = new CheckersLogic();
                selR = selC = -1; aiThinking = false;
            }
            invalidate(); return true;
        }
        if (aiThinking || g.winner(CheckersLogic.RED) != CheckersLogic.EMPTY) return true;
        float pad = dp(16);
        float board = Math.min(w - pad * 2, getHeight() - dp(120));
        float x0 = (w - board) / 2, y0 = dp(100), cell = board / 8f;
        int c = (int) ((e.getX() - x0) / cell), r = (int) ((e.getY() - y0) / cell);
        if (r < 0 || r >= 8 || c < 0 || c >= 8) return true;
        if (selR >= 0 && canLand(r, c)) {
            g.apply(moveFor(selR, selC, r, c));
            selR = selC = -1;
            scheduleAi();
        } else if (CheckersLogic.sideOf(g.get(r, c)) == CheckersLogic.RED) {
            selR = r; selC = c;
        } else selR = selC = -1;
        invalidate();
        return true;
    }

}
