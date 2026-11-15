package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.Random;

/** Minesweeper — 9x9 / 10 mines. Tap reveals, long-press flags.
 *  First tap is always safe. Tap the header to restart. */
public final class MinesView extends BaseGameView {
    private MinesLogic game = new MinesLogic(9, 9, 10, new Random());
    private long startMs, frozenMs = -1;
    private float downX, downY; private long downT;

    public MinesView(Context c) { super(c); }

    @Override protected void onGameResume() { startLoop(); }
    @Override protected void update(long dtMs) { /* loop only redraws for the timer */ }

    private String status() {
        if (game.isWon()) return "Cleared in " + elapsed() + "s — tap header to restart";
        if (game.isLost()) return "Mine hit — tap header to restart";
        return "Mines left: " + (game.mineCount - game.flagCount()) + "    Time: " + elapsed() + "s";
    }
    private long elapsed() {
        if (startMs == 0) return 0;
        return ((frozenMs >= 0 ? frozenMs : System.currentTimeMillis()) - startMs) / 1000;
    }
    private static final int[] NUM_COLORS = {0, Glass.NEON_CYAN, Color.rgb(120, 230, 140),
            Color.rgb(255, 120, 120), Glass.NEON_VIOLET, Color.rgb(255, 190, 80),
            Color.rgb(90, 200, 255), Color.rgb(240, 240, 240), Color.rgb(180, 180, 180)};

    @Override protected void drawGame(Canvas cv) {
        if ((game.isWon() || game.isLost()) && frozenMs < 0 && startMs != 0)
            frozenMs = System.currentTimeMillis();
        float w = getWidth(), pad = dp(16);
        leftText(cv, status(), pad, dp(36), 14.5f, Glass.TEXT, true);
        leftText(cv, "Tap: reveal   Long-press: flag   Tap header: new board", pad, dp(60), 11.5f, Glass.TEXT_DIM, false);
        float board = Math.min(w - pad * 2, getHeight() - dp(120));
        float x0 = (w - board) / 2, y0 = dp(76), cell = board / 9f;
        for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++) {
            float l = x0 + c * cell + dp(1.5f), t = y0 + r * cell + dp(1.5f);
            float rr = l + cell - dp(3), bb = t + cell - dp(3);
            if (!game.isRevealed(r, c)) {
                roundRect(cv, l, t, rr, bb, dp(6), Glass.GLASS_FILL_STRONG);
                if (game.isFlagged(r, c)) {
                    paint.setColor(Glass.NEON_VIOLET); paint.setStyle(android.graphics.Paint.Style.FILL);
                    float cx = (l + rr) / 2, cy = (t + bb) / 2;
                    cv.drawCircle(cx, cy, dp(5), paint);
                    centeredText(cv, "F", cx, cy, 11, Glass.BG_DEEP, true);
                }
            } else if (game.isMine(r, c)) {
                roundRect(cv, l, t, rr, bb, dp(6), Color.rgb(150, 40, 50));
                centeredText(cv, "*", (l + rr) / 2, (t + bb) / 2, 20, Glass.TEXT, true);
            } else {
                roundRect(cv, l, t, rr, bb, dp(6), Glass.GLASS_FILL);
                int a = game.adjacent(r, c);
                if (a > 0) centeredText(cv, String.valueOf(a), (l + rr) / 2, (t + bb) / 2, 16, NUM_COLORS[a], true);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float w = getWidth(), pad = dp(16);
        float board = Math.min(w - pad * 2, getHeight() - dp(120));
        float x0 = (w - board) / 2, y0 = dp(76), cell = board / 9f;
        if (e.getAction() == MotionEvent.ACTION_DOWN) { downX = e.getX(); downY = e.getY(); downT = System.currentTimeMillis(); return true; }
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        if (downY < dp(66)) { game = new MinesLogic(9, 9, 10, new Random()); startMs = 0; frozenMs = -1; invalidate(); return true; }
        int c = (int) ((e.getX() - x0) / cell), r = (int) ((e.getY() - y0) / cell);
        if (r < 0 || r >= 9 || c < 0 || c >= 9) return true;
        if (Math.abs(e.getX() - downX) > dp(10) || Math.abs(e.getY() - downY) > dp(10)) return true;
        boolean longPress = System.currentTimeMillis() - downT > 450;
        if (startMs == 0) startMs = System.currentTimeMillis();
        if (longPress) game.toggleFlag(r, c); else game.reveal(r, c);
        if (game.isLost()) game.revealAllMines();
        invalidate();
        return true;
    }
}
