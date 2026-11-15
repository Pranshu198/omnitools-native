package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.Random;

/** Vertical Hopper: the ball bounces on its own — hold the left or
 *  right half of the screen to steer. Springs launch you, brown
 *  platforms break. Climb as high as you can. */
public final class HopperView extends BaseGameView {
    private HopperLogic g = new HopperLogic(new Random());
    private int steer;
    private boolean leftHeld, rightHeld;

    public HopperView(Context c) { super(c); }

    @Override protected void onGameResume() { startLoop(); }

    private float fieldTop() { return dp(104); }
    private float fieldH() { return getHeight() - dp(140); }
    private float fieldW() { return getWidth() - dp(32); }
    private float sx(float ux) { return dp(16) + ux * fieldW(); }
    /** world y -> screen y: camera bottom at field bottom. */
    private float sy(float wy) { return fieldTop() + fieldH() - (wy - g.cameraY) * fieldH(); }

    @Override protected void update(long dt) {
        steer = (rightHeld ? 1 : 0) - (leftHeld ? 1 : 0);
        g.step(Math.min(dt, 50) / 1000f, steer);
    }

    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        leftText(cv, "Vertical Hopper    Height: " + g.score() + " m"
                + (best("hopper_best") > 0 ? "   •   Best: " + best("hopper_best") : ""), pad, dp(34), 15, Glass.TEXT, true);
        leftText(cv, "Tap: [New Game]", pad, dp(58), 12, Glass.NEON_CYAN, false);
        String st = g.over ? "You fell — tap New Game" : "Hold left / right half to steer";
        if (g.over) saveBest("hopper_best", g.score());
        leftText(cv, st, pad, dp(84), 14, Glass.TEXT_DIM, false);
        glassCell(cv, pad, fieldTop(), w - pad, fieldTop() + fieldH(), dp(10));
        cv.save();
        cv.clipRect(pad, fieldTop(), w - pad, fieldTop() + fieldH());
        for (HopperLogic.Plat p : g.plats) {
            if (p.broken) continue;
            float l = sx(p.x), t = sy(p.y + 0.015f), r = sx(p.x + p.w), b = sy(p.y);
            int col = p.type == 1 ? Color.rgb(255, 176, 66) : p.type == 2 ? Color.rgb(160, 110, 70) : Glass.NEON_CYAN;
            roundRect(cv, l, t, r, b, dp(4), col);
            if (p.type == 1) { // spring marker
                paint.setColor(Color.rgb(10, 14, 22));
                cv.drawRect(l + (r - l) / 2 - dp(3), t - dp(5), l + (r - l) / 2 + dp(3), t, paint);
            }
        }
        paint.setColor(Glass.TEXT);
        cv.drawCircle(sx(g.ballX), sy(g.ballY), dp(9), paint);
        cv.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int ptr = e.getPointerId(e.getActionIndex());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (e.getY() >= dp(44) && e.getY() < dp(70)) {
                    g = new HopperLogic(new Random()); leftHeld = rightHeld = false; return true;
                }
                setHeld(e.getX(), true);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                setHeld(e.getX(e.getActionIndex()), true);
                return true;
            case MotionEvent.ACTION_MOVE:
                leftHeld = rightHeld = false;
                for (int i = 0; i < e.getPointerCount(); i++) setHeld(e.getX(i), true);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                leftHeld = rightHeld = false;
                for (int i = 0; i < e.getPointerCount(); i++)
                    if (e.getPointerId(i) != ptr) setHeld(e.getX(i), true);
                return true;
            default: return true;
        }
    }
    private void setHeld(float x, boolean v) {
        if (x < getWidth() / 2f) leftHeld = v; else rightHeld = v;
    }
}
