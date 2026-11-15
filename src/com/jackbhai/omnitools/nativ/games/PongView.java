package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

/** Pong vs AI: drag on your half to move the left paddle. First to 7. */
public final class PongView extends BaseGameView {
    private final PongLogic g = new PongLogic();
    private boolean dragging;

    public PongView(Context c) { super(c); }

    @Override protected void onGameResume() { startLoop(); }

    private float fieldTop() { return dp(104); }
    private float fieldH() { return getHeight() - dp(150); }

    @Override protected void update(long dt) {
        if (!g.isOver()) g.step(Math.min(dt, 50) / 1000f);
    }

    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        leftText(cv, "Pong    You " + g.playerScore + " — " + g.aiScore + " CPU", pad, dp(34), 15, Glass.TEXT, true);
        leftText(cv, "Tap: [New Game]", pad, dp(58), 12, Glass.NEON_CYAN, false);
        String st = g.isOver() ? (g.playerWon() ? "You win — tap New Game" : "CPU wins — tap New Game")
                : "Drag to move your paddle • First to " + PongLogic.WIN_SCORE;
        leftText(cv, st, pad, dp(84), 14, Glass.TEXT_DIM, false);
        float t = fieldTop(), h = fieldH();
        glassCell(cv, pad, t, w - pad, t + h, dp(10));
        float fw = w - pad * 2;
        // centre line
        paint.setColor(Glass.GLASS_BORDER);
        for (float y = t + dp(8); y < t + h - dp(4); y += dp(18)) cv.drawRect(w / 2 - dp(1), y, w / 2 + dp(1), y + dp(9), paint);
        // paddles
        float ph = PongLogic.PADDLE_H * h, pw = Math.max(dp(6), PongLogic.PADDLE_W * fw);
        roundRect(cv, pad + dp(4), t + g.playerY * h - ph / 2, pad + dp(4) + pw, t + g.playerY * h + ph / 2, dp(4), Glass.NEON_CYAN);
        roundRect(cv, w - pad - dp(4) - pw, t + g.aiY * h - ph / 2, w - pad - dp(4), t + g.aiY * h + ph / 2, dp(4), Glass.NEON_VIOLET);
        // ball
        float bx = pad + g.ballX * fw, by = t + g.ballY * h;
        paint.setColor(Glass.TEXT);
        cv.drawCircle(bx, by, dp(6), paint);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float t = fieldTop(), h = fieldH();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (e.getY() >= dp(44) && e.getY() < dp(70)) { g.reset(); return true; }
                dragging = true;
                g.setPlayerY((e.getY() - t) / h);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) g.setPlayerY((e.getY() - t) / h);
                return true;
            case MotionEvent.ACTION_UP:
                dragging = false; return true;
            default: return true;
        }
    }
}
