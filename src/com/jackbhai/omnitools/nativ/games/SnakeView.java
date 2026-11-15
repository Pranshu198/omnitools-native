package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.ArrayList;
import java.util.Random;

/** Snake — swipe to steer, speed rises as it grows, best score saved. */
public final class SnakeView extends BaseGameView {
    private static final int N = 20;
    private final ArrayList<int[]> snake = new ArrayList<>();
    private final Random rnd = new Random();
    private final Swipe swipe = new Swipe();
    private int dir = 1, nextDir = 1, foodX, foodY, score, acc;
    private boolean dead, started;

    public SnakeView(Context c) { super(c); reset(); }

    private void reset() {
        snake.clear(); snake.add(new int[]{10, 10}); snake.add(new int[]{9, 10}); snake.add(new int[]{8, 10});
        dir = nextDir = 1; score = 0; acc = 0; dead = false; placeFood();
    }
    private void placeFood() {
        while (true) {
            foodX = rnd.nextInt(N); foodY = rnd.nextInt(N);
            boolean hit = false;
            for (int[] s : snake) if (s[0] == foodX && s[1] == foodY) hit = true;
            if (!hit) return;
        }
    }
    private int interval() { return Math.max(65, 150 - snake.size() * 3); }

    @Override protected void onGameResume() { startLoop(); }
    @Override protected void update(long dt) {
        if (!started || dead) return;
        acc += dt;
        if (acc < interval()) return;
        acc = 0; dir = nextDir;
        int[] h = snake.get(0);
        int nx = h[0] + (dir == 1 ? 1 : dir == 0 ? -1 : 0);
        int ny = h[1] + (dir == 3 ? 1 : dir == 2 ? -1 : 0);
        if (nx < 0 || ny < 0 || nx >= N || ny >= N) { dead = true; saveBest("best_snake", score); return; }
        for (int[] s : snake) if (s[0] == nx && s[1] == ny) { dead = true; saveBest("best_snake", score); return; }
        snake.add(0, new int[]{nx, ny});
        if (nx == foodX && ny == foodY) { score += 10; placeFood(); } else snake.remove(snake.size() - 1);
    }

    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        leftText(cv, "Score  " + score + "    Best  " + Math.max(best("best_snake"), score), pad, dp(34), 15, Glass.TEXT, true);
        float board = Math.min(w - pad * 2, getHeight() - dp(110));
        float x0 = (w - board) / 2, y0 = dp(52), cell = board / N;
        glassCell(cv, x0, y0, x0 + board, y0 + board, dp(12));
        roundRect(cv, x0 + foodX * cell + dp(2), y0 + foodY * cell + dp(2),
                x0 + (foodX + 1) * cell - dp(2), y0 + (foodY + 1) * cell - dp(2), dp(5), Glass.NEON_VIOLET);
        for (int i = snake.size() - 1; i >= 0; i--) {
            int[] s = snake.get(i);
            int col = i == 0 ? Glass.NEON_CYAN : Color.rgb(0, 170, 195);
            roundRect(cv, x0 + s[0] * cell + dp(1.5f), y0 + s[1] * cell + dp(1.5f),
                    x0 + (s[0] + 1) * cell - dp(1.5f), y0 + (s[1] + 1) * cell - dp(1.5f), dp(5), col);
        }
        if (!started) centeredText(cv, "Tap to start — swipe to steer", w / 2, y0 + board + dp(32), 14, Glass.TEXT_DIM, false);
        else if (dead) centeredText(cv, "Game Over — tap to play again", w / 2, y0 + board + dp(32), 15, Glass.NEON_VIOLET, true);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) { swipe.down(e.getX(), e.getY()); return true; }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            if (!started) { started = true; return true; }
            if (dead) { reset(); dead = false; started = true; return true; }
            int d = swipe.dir(e.getX(), e.getY(), dp(18));
            if (d >= 0) {
                // forbid instant reversal
                if (!((dir == 0 && d == 1) || (dir == 1 && d == 0) || (dir == 2 && d == 3) || (dir == 3 && d == 2)))
                    nextDir = d;
            }
            return true;
        }
        return true;
    }
}
