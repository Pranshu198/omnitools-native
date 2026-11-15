package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.Random;

/** Tic-Tac-Toe vs AI. Difficulty: Easy = random moves, Hard = minimax
 *  (never loses). Player side X or O; toggles in the header. */
public final class TttView extends BaseGameView {
    private final char[] b = new char[9];
    private final Random rnd = new Random();
    private char player = TttLogic.X, ai = TttLogic.O;
    private boolean hard = true, aiThinking;

    public TttView(Context c) { super(c); reset(); }

    private void reset() {
        for (int i = 0; i < 9; i++) b[i] = TttLogic.EMPTY;
        aiThinking = false;
        if (ai == TttLogic.X) scheduleAi();
    }
    private void scheduleAi() {
        aiThinking = true;
        gameHandler().postDelayed(() -> {
            aiThinking = false;
            if (TttLogic.isOver(b)) { invalidate(); return; }
            int mv = hard ? TttLogic.bestMove(b, ai, player) : randomMove();
            if (mv >= 0) b[mv] = ai;
            invalidate();
        }, 350);
    }
    private int randomMove() {
        int[] empty = new int[9]; int n = 0;
        for (int i = 0; i < 9; i++) if (b[i] == TttLogic.EMPTY) empty[n++] = i;
        return n == 0 ? -1 : empty[rnd.nextInt(n)];
    }

    @Override protected void update(long dt) {}
    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        leftText(cv, "You: " + player + "    AI: " + (hard ? "Hard" : "Easy"), pad, dp(34), 15, Glass.TEXT, true);
        leftText(cv, "Tap: [Side]  [Level]  [New Game]", pad, dp(58), 12, Glass.NEON_CYAN, false);
        char win = TttLogic.winner(b);
        String status = win == player ? "You win" : win == ai ? "AI wins" : TttLogic.isFull(b) ? "Draw" : aiThinking ? "AI is thinking..." : "Your move";
        leftText(cv, status, pad, dp(84), 14, Glass.TEXT_DIM, false);
        float board = Math.min(w - pad * 2, getHeight() - dp(170));
        float x0 = (w - board) / 2, y0 = dp(100), cell = board / 3f;
        for (int i = 0; i < 9; i++) {
            int r = i / 3, c = i % 3;
            float l = x0 + c * cell + dp(4), t = y0 + r * cell + dp(4);
            float rr = l + cell - dp(8), bb = t + cell - dp(8);
            glassCell(cv, l, t, rr, bb, dp(12));
            if (b[i] != TttLogic.EMPTY)
                centeredText(cv, String.valueOf(b[i]), (l + rr) / 2, (t + bb) / 2, 40,
                        b[i] == player ? Glass.NEON_CYAN : Glass.NEON_VIOLET, true);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float w = getWidth();
        if (e.getY() >= dp(44) && e.getY() < dp(70)) {
            float x = e.getX();
            if (x < w * 0.33f) { // Side toggle
                player = player == TttLogic.X ? TttLogic.O : TttLogic.X;
                ai = player == TttLogic.X ? TttLogic.O : TttLogic.X; reset();
            } else if (x < w * 0.66f) { hard = !hard; }
            else reset();
            invalidate(); return true;
        }
        if (aiThinking || TttLogic.isOver(b)) return true;
        float pad = dp(16);
        float board = Math.min(w - pad * 2, getHeight() - dp(170));
        float x0 = (w - board) / 2, y0 = dp(100), cell = board / 3f;
        int c = (int) ((e.getX() - x0) / cell), r = (int) ((e.getY() - y0) / cell);
        if (r < 0 || r >= 3 || c < 0 || c >= 3) return true;
        int i = r * 3 + c;
        if (b[i] != TttLogic.EMPTY) return true;
        b[i] = player;
        if (!TttLogic.isOver(b)) scheduleAi();
        invalidate();
        return true;
    }
}
