package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.Random;

/** 2048 — swipe-only controls (plus tap the header to restart). */
public final class Game2048View extends BaseGameView {
    private final Logic2048 game = new Logic2048(new Random());
    private final Swipe swipe = new Swipe();

    public Game2048View(Context c) { super(c); }

    @Override protected void update(long dt) {}
    @Override protected void onGameResume() { invalidate(); }

    private static int tileColor(int v) {
        switch (v) {
            case 2: return Color.rgb(58, 74, 96); case 4: return Color.rgb(52, 92, 110);
            case 8: return Color.rgb(0, 140, 165); case 16: return Color.rgb(0, 170, 190);
            case 32: return Color.rgb(0, 200, 215); case 64: return Color.rgb(0, 229, 255);
            case 128: return Color.rgb(120, 90, 220); case 256: return Color.rgb(150, 100, 240);
            case 512: return Color.rgb(179, 136, 255); case 1024: return Color.rgb(210, 160, 255);
            case 2048: return Color.rgb(255, 200, 60); default: return Color.rgb(255, 170, 40);
        }
    }

    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        leftText(cv, "Score  " + game.score(), pad, dp(34), 16, Glass.TEXT, true);
        leftText(cv, "Best  " + Math.max(best("best_2048"), game.score()), pad, dp(58), 12.5f, Glass.TEXT_DIM, false);
        leftText(cv, "Tap here for New Game", w - dp(150), dp(34), 12, Glass.NEON_CYAN, false);
        float board = Math.min(w - pad * 2, getHeight() - dp(130));
        float x0 = (w - board) / 2, y0 = dp(76), cell = board / 4f, gap = dp(8);
        glassCell(cv, x0 - gap / 2, y0 - gap / 2, x0 + board + gap / 2, y0 + board + gap / 2, dp(14));
        for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) {
            float l = x0 + c * cell + gap / 2, t = y0 + r * cell + gap / 2;
            float rr = l + cell - gap, bb = t + cell - gap;
            int v = game.get(r, c);
            if (v == 0) { roundRect(cv, l, t, rr, bb, dp(10), Glass.GLASS_FILL); continue; }
            roundRect(cv, l, t, rr, bb, dp(10), tileColor(v));
            centeredText(cv, String.valueOf(v), (l + rr) / 2, (t + bb) / 2,
                    v >= 1000 ? 20 : v >= 100 ? 24 : 28,
                    v >= 2048 ? Color.rgb(30, 25, 5) : Glass.TEXT, true);
        }
        float by = y0 + board + dp(34);
        if (game.isGameOver())
            centeredText(cv, "Game Over — tap header for New Game", w / 2, by, 16, Glass.NEON_VIOLET, true);
        else if (game.hasWon())
            centeredText(cv, "You made 2048 — keep going", w / 2, by, 15, Glass.NEON_CYAN, true);
        else
            centeredText(cv, "Swipe to move tiles", w / 2, by, 13, Glass.TEXT_DIM, false);
        saveBest("best_2048", game.score());
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            swipe.down(e.getX(), e.getY());
            if (e.getY() < dp(66) && e.getX() > getWidth() - dp(170)) { game.reset(); invalidate(); return true; }
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            int d = swipe.dir(e.getX(), e.getY(), dp(24));
            if (d >= 0 && game.move(d)) invalidate();
            return true;
        }
        return true;
    }
}
