package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.ArrayList;
import java.util.Random;

/** Pipe Dodge — original Flappy-style game written from scratch:
 *  gravity, tap to flap, pipe gaps, score + best. No third-party code. */
public final class FlappyView extends BaseGameView {
    private static final class Pipe { float x, gapY; boolean scored; }
    private final ArrayList<Pipe> pipes = new ArrayList<>();
    private final Random rnd = new Random();
    private float birdY, vel, spawnAcc;
    private int score;
    private boolean started, dead;

    public FlappyView(Context c) { super(c); reset(); }

    private void reset() {
        pipes.clear(); birdY = 0; vel = 0; score = 0; spawnAcc = 0; started = false; dead = false;
    }
    @Override protected void onGameResume() { startLoop(); if (birdY == 0) birdY = getHeight() / 2f; }

    @Override protected void update(long dt) {
        if (!started || dead || getHeight() == 0) return;
        float s = dt / 16.7f;
        vel += dp(0.55f) * s;
        birdY += vel * s;
        spawnAcc += dt;
        if (spawnAcc > 1500) {
            spawnAcc = 0;
            Pipe p = new Pipe(); p.x = getWidth() + dp(40);
            p.gapY = dp(130) + rnd.nextFloat() * (getHeight() - dp(300));
            pipes.add(p);
        }
        float bx = getWidth() * 0.32f, gapH = dp(168), pw = dp(64);
        for (int i = pipes.size() - 1; i >= 0; i--) {
            Pipe p = pipes.get(i);
            p.x -= dp(3.1f) * s;
            if (!p.scored && p.x + pw < bx) { p.scored = true; score++; saveBest("best_flappy", score); }
            if (p.x + pw < -dp(10)) { pipes.remove(i); continue; }
            boolean inX = bx + dp(13) > p.x && bx - dp(13) < p.x + pw;
            boolean inGap = birdY - dp(13) > p.gapY - gapH / 2 && birdY + dp(13) < p.gapY + gapH / 2;
            if (inX && !inGap) dead = true;
        }
        if (birdY > getHeight() - dp(14) || birdY < dp(14)) dead = true;
        if (dead) saveBest("best_flappy", score);
    }

    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), h = getHeight();
        if (birdY == 0 && h > 0) birdY = h / 2;
        float gapH = dp(168), pw = dp(64);
        for (Pipe p : pipes) {
            roundRect(cv, p.x, dp(8), p.x + pw, p.gapY - gapH / 2, dp(8), Glass.NEON_VIOLET);
            roundRect(cv, p.x, p.gapY + gapH / 2, p.x + pw, h - dp(8), dp(8), Glass.NEON_VIOLET);
        }
        paint.setColor(Glass.NEON_CYAN);
        cv.drawCircle(w * 0.32f, birdY, dp(14), paint);
        paint.setColor(Glass.BG_DEEP);
        cv.drawCircle(w * 0.32f + dp(5), birdY - dp(4), dp(3), paint);
        centeredText(cv, String.valueOf(score), w / 2, dp(64), 34, Glass.TEXT, true);
        centeredText(cv, "Best  " + Math.max(best("best_flappy"), score), w / 2, dp(94), 13, Glass.TEXT_DIM, false);
        if (!started) centeredText(cv, "Tap to flap", w / 2, h * 0.68f, 17, Glass.NEON_CYAN, true);
        else if (dead) centeredText(cv, "Game Over — tap to retry", w / 2, h * 0.68f, 17, Glass.NEON_VIOLET, true);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
        if (dead) { reset(); birdY = getHeight() / 2f; started = true; vel = -dp(8.5f); return true; }
        started = true; vel = -dp(8.5f);
        return true;
    }
}
