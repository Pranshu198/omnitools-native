package com.jackbhai.omnitools.nativ.games;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Vertical Hopper (original code): a ball bounces on platforms and the
 *  world scrolls as it climbs. Field is 1.0 wide; heights are in world
 *  units (1 unit = one screen height, y grows upward for altitude).
 *  Hold left/right side of the screen to steer; the ball wraps around
 *  the edges. Score = max altitude in metres (1 unit = 10 m). */
public final class HopperLogic {
    public static final class Plat {
        public float x, y, w; public boolean broken; public int type; // 0 normal, 1 spring, 2 fragile
    }
    public static final float GRAVITY = -2.6f, BOUNCE = 1.25f, SPRING = 1.9f;
    public final List<Plat> plats = new ArrayList<>();
    public float ballX = 0.5f, ballY = 0.15f, ballVY = BOUNCE, ballVX;
    public float cameraY = 0; // world y at the bottom of the screen
    public float maxAlt = 0;
    public boolean over;
    private final Random rnd;

    public HopperLogic(Random rnd) {
        this.rnd = rnd;
        Plat base = new Plat(); base.x = 0.35f; base.y = 0.05f; base.w = 0.3f; base.type = 0;
        plats.add(base);
        for (float y = 0.25f; y < 2f; y += gapFor(y)) addPlat(y);
    }

    private float gapFor(float alt) { return 0.13f + Math.min(0.07f, alt * 0.004f) + rnd.nextFloat() * 0.04f; }

    private void addPlat(float y) {
        Plat p = new Plat();
        p.w = 0.16f;
        p.x = rnd.nextFloat() * (1 - p.w);
        p.y = y;
        int roll = rnd.nextInt(20);
        p.type = roll == 0 ? 1 : roll < 4 ? 2 : 0;
        plats.add(p);
    }

    /** steer: -1 left, 0 none, 1 right (touch halves). */
    public void step(float dt, int steer) {
        if (over) return;
        ballVX = steer * 0.85f;
        ballX += ballVX * dt;
        if (ballX < 0) ballX += 1; if (ballX > 1) ballX -= 1;
        ballVY += GRAVITY * dt;
        float prevBottom = ballY - 0.02f;
        ballY += ballVY * dt;
        if (ballVY < 0) { // falling: check landings
            for (Plat p : plats) {
                if (p.broken) continue;
                float top = p.y + 0.015f;
                if (prevBottom >= top - 0.001f && ballY - 0.02f <= top
                        && ballX + 0.02f >= p.x && ballX - 0.02f <= p.x + p.w) {
                    ballY = top + 0.02f;
                    if (p.type == 2) { p.broken = true; /* falls through next frame */ }
                    else ballVY = p.type == 1 ? SPRING : BOUNCE;
                    break;
                }
            }
        }
        // camera follows upward only
        float target = ballY - 0.45f;
        if (target > cameraY) cameraY = target;
        maxAlt = Math.max(maxAlt, ballY);
        // generate + prune platforms
        float topY = plats.isEmpty() ? cameraY : plats.get(plats.size() - 1).y;
        while (topY < cameraY + 1.3f) { topY += gapFor(topY); addPlat(topY); }
        Iterator<Plat> it = plats.iterator();
        while (it.hasNext()) if (it.next().y < cameraY - 0.1f) it.remove();
        if (ballY < cameraY - 0.05f) over = true;
    }

    public int score() { return (int) (maxAlt * 10); }
}
