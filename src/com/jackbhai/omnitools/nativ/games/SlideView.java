package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.Random;

/** Sliding Puzzle — 4x4 default, 3x3 toggle. Shuffles are random legal
 *  moves from solved, so always solvable (parity-checked logic). */
public final class SlideView extends BaseGameView {
    private SlideLogic game = new SlideLogic(4);
    private final Random rnd = new Random();

    public SlideView(Context c) { super(c); game.shuffle(rnd, 120); }

    @Override protected void update(long dt) {}
    private void newGame(int n) { game = new SlideLogic(n); game.shuffle(rnd, n == 3 ? 60 : 120); }

    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        leftText(cv, game.isSolved() ? "Solved in " + game.moves() + " moves" : "Moves: " + game.moves(), pad, dp(36), 15.5f, Glass.TEXT, true);
        leftText(cv, "Tap: [Shuffle]  [3 x 3]  [4 x 4]", pad, dp(60), 12.5f, Glass.NEON_CYAN, false);
        float board = Math.min(w - pad * 2, getHeight() - dp(120));
        float x0 = (w - board) / 2, y0 = dp(80), cell = board / game.n;
        for (int i = 0; i < game.n * game.n; i++) {
            int v = game.get(i);
            if (v == 0) continue;
            int r = i / game.n, c = i % game.n;
            float l = x0 + c * cell + dp(3), t = y0 + r * cell + dp(3);
            float rr = l + cell - dp(6), bb = t + cell - dp(6);
            boolean home = v == i + 1;
            roundRect(cv, l, t, rr, bb, dp(10), home ? Glass.GLASS_FILL_STRONG : Glass.GLASS_FILL);
            centeredText(cv, String.valueOf(v), (l + rr) / 2, (t + bb) / 2, 22,
                    home ? Glass.NEON_CYAN : Glass.TEXT, true);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float w = getWidth();
        if (e.getY() >= dp(46) && e.getY() < dp(72)) {
            float x = e.getX();
            if (x < w * 0.36f) game.shuffle(rnd, game.n == 3 ? 60 : 120);
            else if (x < w * 0.68f) newGame(3);
            else newGame(4);
            invalidate(); return true;
        }
        float pad = dp(16);
        float board = Math.min(w - pad * 2, getHeight() - dp(120));
        float x0 = (w - board) / 2, y0 = dp(80), cell = board / game.n;
        int c = (int) ((e.getX() - x0) / cell), r = (int) ((e.getY() - y0) / cell);
        if (r < 0 || r >= game.n || c < 0 || c >= game.n) return true;
        game.slide(r * game.n + c);
        invalidate();
        return true;
    }
}
