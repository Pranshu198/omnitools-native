package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

/** Breakout — touch-drag paddle, 3 lives, each cleared wall = next level
 *  with a faster ball. Tap to launch / restart. */
public final class BreakoutView extends BaseGameView {
    private static final int ROWS = 5, COLS = 8;
    private final boolean[][] bricks = new boolean[ROWS][COLS];
    private float paddleX = -1, ballX, ballY, vx, vy;
    private int score, lives, level, left;
    private boolean launched, over, cleared;

    public BreakoutView(Context c) { super(c); newGame(); }

    private void newGame() { score = 0; lives = 3; level = 1; buildWall(); resetBall(); }
    private void buildWall() {
        left = ROWS * COLS; cleared = false;
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) bricks[r][c] = true;
    }
    private void resetBall() {
        launched = false; over = false;
        ballX = getWidth() / 2f; ballY = getHeight() * 0.7f;
        float sp = dp(4.2f) + level * dp(0.5f);
        vx = sp * 0.6f; vy = -sp;
    }
    @Override protected void onGameResume() { startLoop(); if (paddleX < 0) { paddleX = getWidth() / 2f; resetBall(); } }

    @Override protected void update(long dt) {
        if (!launched || over || cleared || getWidth() == 0) return;
        float pw = dp(84), ph = dp(12), py = getHeight() - dp(56);
        ballX += vx; ballY += vy;
        if (ballX < dp(8)) { ballX = dp(8); vx = Math.abs(vx); }
        if (ballX > getWidth() - dp(8)) { ballX = getWidth() - dp(8); vx = -Math.abs(vx); }
        if (ballY < dp(70)) { ballY = dp(70); vy = Math.abs(vy); }
        if (vy > 0 && ballY > py - dp(8) && ballY < py + ph && Math.abs(ballX - paddleX) < pw / 2 + dp(6)) {
            vy = -Math.abs(vy);
            vx += (ballX - paddleX) / (pw / 2) * dp(1.6f);
            ballY = py - dp(9);
        }
        float bw = (getWidth() - dp(32)) / COLS, bh = dp(20), top = dp(92);
        int c = (int) ((ballX - dp(16)) / bw), r = (int) ((ballY - top) / bh);
        if (r >= 0 && r < ROWS && c >= 0 && c < COLS && bricks[r][c]) {
            bricks[r][c] = false; left--; score += 10; vy = -vy;
            if (left == 0) { cleared = true; saveBest("best_breakout", score); }
        }
        if (ballY > getHeight() - dp(10)) {
            lives--;
            if (lives <= 0) { over = true; saveBest("best_breakout", score); } else resetBall();
        }
    }

    private static final int[] ROW_COLORS = {Glass.NEON_CYAN, Color.rgb(0, 190, 220),
            Glass.NEON_VIOLET, Color.rgb(150, 110, 240), Color.rgb(255, 170, 90)};

    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), h = getHeight();
        leftText(cv, "Score  " + score + "    Lives  " + lives + "    Level  " + level, dp(16), dp(34), 14.5f, Glass.TEXT, true);
        leftText(cv, "Best  " + Math.max(best("best_breakout"), score), dp(16), dp(56), 12, Glass.TEXT_DIM, false);
        float bw = (w - dp(32)) / COLS, bh = dp(20), top = dp(92);
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            if (!bricks[r][c]) continue;
            roundRect(cv, dp(16) + c * bw + dp(2), top + r * bh + dp(2),
                    dp(16) + (c + 1) * bw - dp(2), top + (r + 1) * bh - dp(2), dp(5), ROW_COLORS[r]);
        }
        if (paddleX < 0) paddleX = w / 2;
        float pw = dp(84), py = h - dp(56);
        if (!launched && !over && !cleared) { ballX = paddleX; ballY = py - dp(12); }
        roundRect(cv, paddleX - pw / 2, py, paddleX + pw / 2, py + dp(12), dp(6), Glass.NEON_CYAN);
        paint.setColor(Glass.TEXT);
        cv.drawCircle(ballX, ballY, dp(7), paint);
        if (over) centeredText(cv, "Game Over — tap to play again", w / 2, h / 2, 17, Glass.NEON_VIOLET, true);
        else if (cleared) centeredText(cv, "Wall cleared — tap for level " + (level + 1), w / 2, h / 2, 16, Glass.NEON_CYAN, true);
        else if (!launched) centeredText(cv, "Drag to move — tap to launch", w / 2, h - dp(110), 13, Glass.TEXT_DIM, false);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        paddleX = Math.max(dp(42), Math.min(getWidth() - dp(42), e.getX()));
        if (e.getAction() == MotionEvent.ACTION_UP) {
            if (over) newGame();
            else if (cleared) { level++; buildWall(); resetBall(); }
            else if (!launched) launched = true;
        }
        invalidate();
        return true;
    }
}
