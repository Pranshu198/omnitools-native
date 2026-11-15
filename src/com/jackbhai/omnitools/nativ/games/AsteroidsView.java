package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.Random;

/** Asteroid Blaster: hold and drag to aim the ship toward your
 *  finger — it thrusts and auto-fires while held. Blast rocks; they
 *  split until small. 3 lives. */
public final class AsteroidsView extends BaseGameView {
    private final AsteroidsLogic g = new AsteroidsLogic(new Random());
    private final Path shipPath = new Path();
    private boolean touching;
    private float touchX, touchY, lastTapAt;
    private float fireCooldown;

    public AsteroidsView(Context c) { super(c); }

    @Override protected void onGameResume() { startLoop(); }

    private float fieldTop() { return dp(104); }
    private float fieldH() { return getHeight() - dp(140); }
    private float fieldW() { return getWidth() - dp(32); }
    private float sx(float ux) { return dp(16) + ux * fieldW(); }
    private float sy(float uy) { return fieldTop() + uy * fieldH(); }

    @Override protected void update(long dt) {
        float step = Math.min(dt, 50) / 1000f;
        if (touching && !g.isOver()) {
            float dx = (touchX - dp(16)) / fieldW() - g.shipX;
            float dy = (touchY - fieldTop()) / fieldH() - g.shipY;
            g.aim((float) Math.toDegrees(Math.atan2(dy, dx)));
            g.thrust(step);
            fireCooldown -= step;
            if (fireCooldown <= 0) { g.fire(); fireCooldown = 0.22f; }
        }
        g.step(step);
    }

    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        leftText(cv, "Asteroid Blaster    Score: " + g.score + "  •  Lives: " + Math.max(0, g.lives)
                + "  •  Wave " + g.wave, pad, dp(34), 15, Glass.TEXT, true);
        leftText(cv, "Tap: [New Game]", pad, dp(58), 12, Glass.NEON_CYAN, false);
        String st = g.isOver() ? "Game over — tap New Game" : "Hold + drag: aim, thrust and auto-fire";
        if (g.isOver()) saveBest("asteroids_best", g.score);
        leftText(cv, st, pad, dp(84), 14, Glass.TEXT_DIM, false);
        glassCell(cv, pad, fieldTop(), w - pad, fieldTop() + fieldH(), dp(10));
        // rocks (irregular polygons from rotation)
        paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f)); paint.setColor(Glass.TEXT_DIM);
        for (AsteroidsLogic.Rock r : g.rocks) {
            float cx = sx(r.x), cy = sy(r.y), rad = r.radius() * fieldW();
            Path p = new Path();
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(r.rot + i * 45);
                float rr = rad * (0.75f + 0.25f * ((i * 37 + (int) (r.rot)) % 3) / 2f);
                float px = cx + (float) Math.cos(a) * rr, py = cy + (float) Math.sin(a) * rr;
                if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
            }
            p.close();
            cv.drawPath(p, paint);
        }
        paint.setStyle(android.graphics.Paint.Style.FILL);
        // bullets
        paint.setColor(Glass.NEON_CYAN);
        for (AsteroidsLogic.Bullet b : g.bullets) cv.drawCircle(sx(b.x), sy(b.y), dp(2.5f), paint);
        // ship
        if (g.shipAlive) {
            float cx = sx(g.shipX), cy = sy(g.shipY);
            double a = Math.toRadians(g.shipAngle);
            float nose = dp(13), wing = dp(9);
            shipPath.reset();
            shipPath.moveTo(cx + (float) Math.cos(a) * nose, cy + (float) Math.sin(a) * nose);
            shipPath.lineTo(cx + (float) Math.cos(a + 2.5) * wing, cy + (float) Math.sin(a + 2.5) * wing);
            shipPath.lineTo(cx + (float) Math.cos(a - 2.5) * wing, cy + (float) Math.sin(a - 2.5) * wing);
            shipPath.close();
            paint.setColor(Glass.NEON_CYAN);
            cv.drawPath(shipPath, paint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (e.getY() >= dp(44) && e.getY() < dp(70)) { g.reset(); touching = false; return true; }
                touching = true; touchX = e.getX(); touchY = e.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                touchX = e.getX(); touchY = e.getY(); return true;
            case MotionEvent.ACTION_UP:
                touching = false; return true;
            default: return true;
        }
    }
}
