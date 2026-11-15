package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.Random;

/** Number Merge: swipe to slide. Equal tiles merge into their sum,
 *  and 1 + 2 merge into 3. Chase the biggest tile you can build. */
public final class NumberMergeView extends BaseGameView {
    private NumberMergeLogic g = new NumberMergeLogic(new Random());
    private final Swipe swipe = new Swipe();

    public NumberMergeView(Context c) { super(c); }

    private static int tileColor(int v) {
        switch (v) {
            case 1: return Color.rgb(120, 200, 255);
            case 2: return Color.rgb(255, 150, 150);
            case 3: return Color.rgb(0, 229, 255);
            case 6: return Color.rgb(92, 220, 140);
            case 12: return Color.rgb(255, 176, 66);
            case 24: return Color.rgb(179, 136, 255);
            case 48: return Color.rgb(255, 92, 92);
            case 96: return Color.rgb(255, 120, 200);
            case 192: return Color.rgb(255, 235, 120);
            default: return v == 0 ? 0 : Color.rgb(240, 240, 240);
        }
    }

    @Override protected void update(long dt) {}
    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        leftText(cv, "Number Merge    Score: " + g.score
                + (best("nummerge_best") > 0 ? "   •   Best: " + best("nummerge_best") : ""), pad, dp(34), 15, Glass.TEXT, true);
        leftText(cv, "Tap: [New Game]", pad, dp(58), 12, Glass.NEON_CYAN, false);
        String st = g.isGameOver() ? "No moves left — biggest tile " + g.maxTile()
                : "Swipe to slide • Equals merge, and 1+2 make 3";
        if (g.isGameOver()) saveBest("nummerge_best", g.score);
        leftText(cv, st, pad, dp(84), 14, Glass.TEXT_DIM, false);
        float board = w - pad * 2, x0 = pad, y0 = dp(104), cell = board / NumberMergeLogic.N;
        glassCell(cv, x0 - dp(4), y0 - dp(4), x0 + board + dp(4), y0 + board + dp(4), dp(14));
        for (int r = 0; r < NumberMergeLogic.N; r++) for (int c = 0; c < NumberMergeLogic.N; c++) {
            float l = x0 + c * cell + dp(3), t = y0 + r * cell + dp(3);
            float rr = l + cell - dp(6), bb = t + cell - dp(6);
            int v = g.at(r, c);
            if (v == 0) { glassCell(cv, l, t, rr, bb, dp(10)); continue; }
            roundRect(cv, l, t, rr, bb, dp(10), tileColor(v));
            centeredText(cv, String.valueOf(v), (l + rr) / 2, (t + bb) / 2, v < 100 ? 20 : 16, Color.rgb(10, 14, 22), true);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                swipe.down(e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_UP:
                int d = swipe.dir(e.getX(), e.getY(), dp(24));
                if (d == -1 && e.getY() >= dp(44) && e.getY() < dp(70)) {
                    g = new NumberMergeLogic(new Random()); invalidate(); return true;
                }
                if (d >= 0 && g.move(d)) invalidate();
                return true;
            default: return true;
        }
    }
}
