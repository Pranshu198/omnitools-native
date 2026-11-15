package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.Random;

/** Bubble Shooter: drag to aim (guide line shows the angle, with one
 *  wall bounce), release to fire. Pop clusters of 3+, drop hangers. */
public final class BubbleShooterView extends BaseGameView {
    private static final int[] COLORS = {0, Color.rgb(255, 92, 92), Color.rgb(255, 176, 66),
            Color.rgb(255, 235, 120), Color.rgb(92, 220, 140), Color.rgb(0, 229, 255), Color.rgb(179, 136, 255)};
    private final BubbleShooterLogic g = new BubbleShooterLogic(new Random());
    private BubbleShooterLogic game = g;
    private boolean aiming;
    private float aimX, aimY;
    // flying bubble state
    private boolean flying;
    private float flyX, flyY, flyVX, flyVY;
    private int flyColor;

    public BubbleShooterView(Context c) { super(c); }

    @Override protected void onGameResume() { startLoop(); }

    private float cellR() { return (getWidth() - dp(32)) / (BubbleShooterLogic.COLS * 2f); }
    private float gridTop() { return dp(104); }
    private float cellX(int r, int c) {
        float rad = cellR();
        return dp(16) + rad + c * rad * 2 + (r % 2 == 1 ? rad : 0);
    }
    private float cellY(int r) { return gridTop() + cellR() + r * cellR() * 1.74f; }
    private float shooterX() { return getWidth() / 2f; }
    private float shooterY() { return cellY(BubbleShooterLogic.ROWS) + dp(6); }

    @Override protected void update(long dt) {
        if (!flying || game.isOver()) return;
        float step = Math.min(dt, 50) / 1000f * dp(520);
        flyX += flyVX * step; flyY += flyVY * step;
        float rad = cellR();
        if (flyX < rad + dp(2)) { flyX = rad + dp(2); flyVX = -flyVX; }
        if (flyX > getWidth() - rad - dp(2)) { flyX = getWidth() - rad - dp(2); flyVX = -flyVX; }
        // collision with ceiling or a bubble -> snap to nearest empty cell
        int hitR = -1, hitC = -1;
        if (flyY <= gridTop() + rad) { hitR = 0; hitC = nearestCol(0, flyX); }
        else {
            outer:
            for (int r = 0; r < BubbleShooterLogic.ROWS; r++)
                for (int c = 0; c < BubbleShooterLogic.COLS; c++)
                    if (game.occupied(r, c)) {
                        float dx = flyX - cellX(r, c), dy = flyY - cellY(r);
                        if (dx * dx + dy * dy < (rad * 1.8f) * (rad * 1.8f)) { hitR = r; hitC = c; break outer; }
                    }
        }
        if (hitR >= 0) {
            int[] cell = nearestEmpty(hitR, hitC, flyX, flyY);
            if (cell != null) game.place(cell[0], cell[1]);
            flying = false;
        }
        if (flyY > getHeight()) flying = false;
    }

    private int nearestCol(int r, float x) {
        int best = 0; float bd = Float.MAX_VALUE;
        for (int c = 0; c < BubbleShooterLogic.COLS - (r % 2); c++) {
            float d = Math.abs(cellX(r, c) - x);
            if (d < bd) { bd = d; best = c; }
        }
        return best;
    }
    private int[] nearestEmpty(int r, int c, float x, float y) {
        // candidates: the hit cell's empty neighbours, plus the hit cell itself if free
        int[] best = null; float bd = Float.MAX_VALUE;
        int[][] cand = new int[7][];
        cand[0] = new int[]{r, c};
        System.arraycopy(BubbleShooterLogic.neighbours(r, c), 0, cand, 1, 6);
        for (int[] p : cand) {
            if (p == null || p[0] < 0 || p[0] >= BubbleShooterLogic.ROWS) continue;
            if (p[1] < 0 || p[1] >= BubbleShooterLogic.COLS - (p[0] % 2)) continue;
            if (game.occupied(p[0], p[1])) continue;
            float dx = cellX(p[0], p[1]) - x, dy = cellY(p[0]) - y, d = dx * dx + dy * dy;
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16), rad = cellR();
        leftText(cv, "Bubble Shooter    Score: " + game.score
                + (best("bubble_best") > 0 ? "   •   Best: " + best("bubble_best") : ""), pad, dp(34), 15, Glass.TEXT, true);
        leftText(cv, "Tap: [New Game]", pad, dp(58), 12, Glass.NEON_CYAN, false);
        String st = game.isWon() ? "Board cleared — you win" : game.isLost() ? "Bubbles crossed the line — game over"
                : "Drag to aim, release to shoot";
        if (game.isOver()) saveBest("bubble_best", game.score);
        leftText(cv, st, pad, dp(84), 14, Glass.TEXT_DIM, false);
        // deadline
        float dy = cellY(BubbleShooterLogic.ROWS - 2) + rad;
        paint.setColor(Color.rgb(255, 92, 92));
        cv.drawRect(pad, dy, w - pad, dy + dp(1.5f), paint);
        for (int r = 0; r < BubbleShooterLogic.ROWS; r++)
            for (int c = 0; c < BubbleShooterLogic.COLS; c++) {
                int v = game.at(r, c);
                if (v == 0) continue;
                paint.setColor(COLORS[v]);
                cv.drawCircle(cellX(r, c), cellY(r), rad - dp(1), paint);
            }
        // aim guide
        if (aiming && !flying && !game.isOver()) {
            float sx = shooterX(), sy = shooterY();
            float dx = aimX - sx, dyv = aimY - sy;
            float len = (float) Math.sqrt(dx * dx + dyv * dyv);
            if (len > 1) {
                dx /= len; dyv /= len;
                paint.setColor(Glass.TEXT_DIM);
                float px = sx, py = sy;
                for (int i = 0; i < 12; i++) {
                    px += dx * dp(16); py += dyv * dp(16);
                    if (px < rad || px > w - rad) { dx = -dx; px += dx * dp(16); }
                    cv.drawCircle(px, py, dp(2), paint);
                }
            }
        }
        // shooter + next
        if (flying) { paint.setColor(COLORS[flyColor]); cv.drawCircle(flyX, flyY, rad - dp(1), paint); }
        else if (!game.isOver()) {
            paint.setColor(COLORS[game.current]);
            cv.drawCircle(shooterX(), shooterY(), rad - dp(1), paint);
        }
        paint.setColor(COLORS[game.next]);
        cv.drawCircle(shooterX() + dp(44), shooterY(), rad * 0.6f, paint);
        leftText(cv, "Next", shooterX() + dp(44) - dp(13), shooterY() + rad + dp(14), 10, Glass.TEXT_DIM, false);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (e.getY() >= dp(44) && e.getY() < dp(70)) {
                    game = new BubbleShooterLogic(new Random()); flying = false; aiming = false;
                    return true;
                }
                if (!flying && !game.isOver() && e.getY() > dp(90)) { aiming = true; aimX = e.getX(); aimY = e.getY(); }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (aiming) { aimX = e.getX(); aimY = e.getY(); }
                return true;
            case MotionEvent.ACTION_UP:
                if (aiming) {
                    aiming = false;
                    float sx = shooterX(), sy = shooterY();
                    float dx = e.getX() - sx, dyv = e.getY() - sy;
                    float len = (float) Math.sqrt(dx * dx + dyv * dyv);
                    if (len > dp(10) && dyv < -dp(4)) { // must aim upward
                        flyVX = dx / len; flyVY = dyv / len;
                        flyX = sx; flyY = sy; flyColor = game.current; flying = true;
                    }
                }
                return true;
            default: return true;
        }
    }
}
