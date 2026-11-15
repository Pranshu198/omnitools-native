package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.Random;

/** Reaction Time — wait for green, tap fast. False starts are caught.
 *  Shows last result, best, and average of attempts this session. */
public final class ReactionView extends BaseGameView {
    private static final int IDLE = 0, WAITING = 1, GREEN = 2, RESULT = 3, FALSE_START = 4;
    private int state = IDLE;
    private long greenAt, totalMs;
    private int attempts, lastMs;
    private final Random rnd = new Random();
    private final Runnable goGreen = () -> {
        if (state != WAITING) return;
        state = GREEN; greenAt = System.currentTimeMillis(); invalidate();
    };

    public ReactionView(Context c) { super(c); }

    @Override protected void update(long dt) {}
    @Override protected void onGameResume() { invalidate(); }
    @Override protected void onDetachedFromWindow() {
        gameHandler().removeCallbacks(goGreen); super.onDetachedFromWindow();
    }

    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), h = getHeight();
        int fill; String big, small;
        switch (state) {
            case WAITING: fill = Color.rgb(150, 45, 55); big = "Wait for green..."; small = "Tap too soon and it counts as a false start"; break;
            case GREEN: fill = Color.rgb(30, 160, 80); big = "Tap"; small = ""; break;
            case RESULT: fill = Glass.GLASS_FILL_STRONG; big = lastMs + " ms"; small = "Tap to go again"; break;
            case FALSE_START: fill = Color.rgb(150, 45, 55); big = "Too soon"; small = "False start — tap to retry"; break;
            default: fill = Glass.GLASS_FILL; big = "Tap to start"; small = "Wait for green, then tap as fast as you can"; break;
        }
        roundRect(cv, dp(16), dp(90), w - dp(16), h - dp(150), dp(20), fill);
        centeredText(cv, big, w / 2, (dp(90) + h - dp(150)) / 2, state == RESULT || state == GREEN ? 38 : 24, Glass.TEXT, true);
        if (!small.isEmpty()) centeredText(cv, small, w / 2, (dp(90) + h - dp(150)) / 2 + dp(44), 13, Glass.TEXT_DIM, false);
        String stats = "Best: " + (best("best_reaction") == 0 ? "-" : best("best_reaction") + " ms")
                + "    Average: " + (attempts == 0 ? "-" : (totalMs / attempts) + " ms")
                + "    Tries: " + attempts;
        centeredText(cv, stats, w / 2, h - dp(96), 14, Glass.TEXT, false);
        centeredText(cv, "Lower is better. Best is saved on this device.", w / 2, h - dp(68), 12, Glass.TEXT_DIM, false);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
        switch (state) {
            case IDLE: case RESULT: case FALSE_START:
                state = WAITING;
                gameHandler().postDelayed(goGreen, 900 + rnd.nextInt(2600));
                break;
            case WAITING:
                gameHandler().removeCallbacks(goGreen);
                state = FALSE_START;
                break;
            case GREEN:
                lastMs = (int) (System.currentTimeMillis() - greenAt);
                attempts++; totalMs += lastMs;
                int b = best("best_reaction");
                if (b == 0 || lastMs < b) saveInt("best_reaction", lastMs);
                state = RESULT;
                break;
        }
        invalidate();
        return true;
    }
}
