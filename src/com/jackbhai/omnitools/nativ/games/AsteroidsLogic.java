package com.jackbhai.omnitools.nativ.games;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Asteroid Blaster (original code): unit field, y down. The ship sits
 *  on a circle that the player rotates/aims by touch; here the ship has
 *  a position + heading, thrust and fire. Asteroids drift and split
 *  (large -> 2 medium -> 2 small). Wrap-around world. 3 lives; score
 *  20/50/100 by size. */
public final class AsteroidsLogic {
    public static final class Rock {
        public float x, y, vx, vy, rot, vrot; public int size; // 3 large, 2 med, 1 small
        public float radius() { return size == 3 ? 0.055f : size == 2 ? 0.036f : 0.02f; }
    }
    public static final class Bullet {
        public float x, y, vx, vy, life;
    }
    public final List<Rock> rocks = new ArrayList<>();
    public final List<Bullet> bullets = new ArrayList<>();
    public float shipX = 0.5f, shipY = 0.5f, shipVX, shipVY, shipAngle = -90f;
    public boolean shipAlive = true;
    public int score, lives = 3, wave = 1;
    private float respawnTimer;
    private final Random rnd;

    public AsteroidsLogic(Random rnd) {
        this.rnd = rnd;
        spawnWave();
    }

    private void spawnWave() {
        rocks.clear();
        int count = 3 + wave;
        for (int i = 0; i < count; i++) {
            Rock r = new Rock();
            // spawn away from the ship
            do { r.x = rnd.nextFloat(); r.y = rnd.nextFloat(); }
            while (dist(r.x, r.y, shipX, shipY) < 0.25f);
            float a = (float) (rnd.nextDouble() * Math.PI * 2);
            float sp = 0.03f + rnd.nextFloat() * 0.05f;
            r.vx = (float) Math.cos(a) * sp; r.vy = (float) Math.sin(a) * sp;
            r.rot = rnd.nextFloat() * 360; r.vrot = rnd.nextFloat() * 60 - 30;
            r.size = 3;
            rocks.add(r);
        }
    }
    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    private static float wrap(float v) { return v - (float) Math.floor(v); }

    public void aim(float angleDeg) { shipAngle = angleDeg; }
    public void thrust(float dt) {
        if (!shipAlive) return;
        double a = Math.toRadians(shipAngle);
        shipVX += (float) Math.cos(a) * 0.35f * dt;
        shipVY += (float) Math.sin(a) * 0.35f * dt;
    }
    public void fire() {
        if (!shipAlive) return;
        Bullet b = new Bullet();
        double a = Math.toRadians(shipAngle);
        b.x = shipX + (float) Math.cos(a) * 0.03f;
        b.y = shipY + (float) Math.sin(a) * 0.03f;
        b.vx = (float) Math.cos(a) * 0.7f; b.vy = (float) Math.sin(a) * 0.7f;
        b.life = 0.9f;
        bullets.add(b);
    }

    public void step(float dt) {
        if (shipAlive) {
            shipX = wrap(shipX + shipVX * dt);
            shipY = wrap(shipY + shipVY * dt);
            shipVX *= (1 - 0.25f * dt); shipVY *= (1 - 0.25f * dt);
        } else if (lives > 0) {
            respawnTimer -= dt;
            if (respawnTimer <= 0 && rocks.stream().noneMatch(r -> dist(r.x, r.y, 0.5f, 0.5f) < 0.2f)) {
                shipX = 0.5f; shipY = 0.5f; shipVX = shipVY = 0; shipAlive = true;
            }
        }
        for (Rock r : rocks) {
            r.x = wrap(r.x + r.vx * dt); r.y = wrap(r.y + r.vy * dt); r.rot += r.vrot * dt;
        }
        Iterator<Bullet> bi = bullets.iterator();
        while (bi.hasNext()) {
            Bullet b = bi.next();
            b.x = wrap(b.x + b.vx * dt); b.y = wrap(b.y + b.vy * dt); b.life -= dt;
            if (b.life <= 0) { bi.remove(); continue; }
            Rock hit = null;
            for (Rock r : rocks) if (dist(b.x, b.y, r.x, r.y) < r.radius()) { hit = r; break; }
            if (hit != null) {
                bi.remove();
                destroyRock(hit);
            }
        }
        if (shipAlive) {
            for (Rock r : rocks) {
                if (dist(shipX, shipY, r.x, r.y) < r.radius() + 0.018f) {
                    lives--;
                    shipAlive = false;
                    respawnTimer = 1.2f;
                    break;
                }
            }
        }
        if (rocks.isEmpty() && lives > 0) { wave++; spawnWave(); }
    }

    /** Destroy a rock, award points, split it if large enough. */
    public void destroyRock(Rock r) {
        rocks.remove(r);
        score += r.size == 3 ? 20 : r.size == 2 ? 50 : 100;
        if (r.size > 1) {
            for (int i = 0; i < 2; i++) {
                Rock s = new Rock();
                s.x = r.x; s.y = r.y; s.size = r.size - 1;
                float a = (float) (rnd.nextDouble() * Math.PI * 2);
                float sp = 0.06f + rnd.nextFloat() * 0.07f;
                s.vx = (float) Math.cos(a) * sp; s.vy = (float) Math.sin(a) * sp;
                s.rot = rnd.nextFloat() * 360; s.vrot = rnd.nextFloat() * 90 - 45;
                rocks.add(s);
            }
        }
    }
    public boolean isOver() { return lives <= 0 && !shipAlive; }

    public void reset() {
        score = 0; lives = 3; wave = 1;
        shipX = 0.5f; shipY = 0.5f; shipVX = shipVY = 0; shipAngle = -90f; shipAlive = true;
        bullets.clear();
        spawnWave();
    }
}
