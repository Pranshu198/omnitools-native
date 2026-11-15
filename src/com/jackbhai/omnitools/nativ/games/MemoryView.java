package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/** Memory Match — 4x4 pairs of drawn shapes (no emoji). Move counter,
 *  flip-back delay, win state. Tap header to restart. */
public final class MemoryView extends BaseGameView {
    private final int[] cards = new int[16];
    private final boolean[] matched = new boolean[16];
    private int first = -1, second = -1, moves, pairs;
    private long flipBackAt;

    public MemoryView(Context c) { super(c); reset(); }

    private void reset() {
        ArrayList<Integer> ids = new ArrayList<>();
        for (int i = 0; i < 8; i++) { ids.add(i); ids.add(i); }
        Collections.shuffle(ids, new Random());
        for (int i = 0; i < 16; i++) { cards[i] = ids.get(i); matched[i] = false; }
        first = second = -1; moves = 0; pairs = 0;
    }

    @Override protected void onGameResume() { startLoop(); }
    @Override protected void update(long dt) {
        if (second >= 0 && System.currentTimeMillis() > flipBackAt) { first = second = -1; invalidate(); }
    }

    private void drawShape(Canvas cv, int id, float cx, float cy, float s) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(id % 2 == 0 ? Glass.NEON_CYAN : Glass.NEON_VIOLET);
        if (id >= 4) paint.setColor(Color.rgb(255, 190, 80));
        switch (id) {
            case 0: cv.drawCircle(cx, cy, s, paint); break;
            case 1: cv.drawRect(cx - s, cy - s, cx + s, cy + s, paint); break;
            case 2: { Path p = new Path(); p.moveTo(cx, cy - s); p.lineTo(cx + s, cy + s); p.lineTo(cx - s, cy + s); p.close(); cv.drawPath(p, paint); break; }
            case 3: { Path p = new Path(); p.moveTo(cx, cy - s); p.lineTo(cx + s, cy); p.lineTo(cx, cy + s); p.lineTo(cx - s, cy); p.close(); cv.drawPath(p, paint); break; }
            case 4: { paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(4)); cv.drawCircle(cx, cy, s * 0.8f, paint); paint.setStyle(Paint.Style.FILL); break; }
            case 5: cv.drawRect(cx - s, cy - s * 0.35f, cx + s, cy + s * 0.35f, paint); break;
            case 6: { Path p = new Path(); for (int i = 0; i < 5; i++) { double a = -Math.PI / 2 + i * 2 * Math.PI / 5; float x = cx + (float) Math.cos(a) * s, y = cy + (float) Math.sin(a) * s; if (i == 0) p.moveTo(x, y); else p.lineTo(x, y); } p.close(); cv.drawPath(p, paint); break; }
            default: cv.drawRect(cx - s * 0.35f, cy - s, cx + s * 0.35f, cy + s, paint);
        }
    }

    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        String head = pairs == 8 ? "Solved in " + moves + " moves — tap header to play again"
                : "Moves: " + moves + "    Pairs: " + pairs + " / 8";
        leftText(cv, head, pad, dp(36), 15, Glass.TEXT, true);
        float board = Math.min(w - pad * 2, getHeight() - dp(110));
        float x0 = (w - board) / 2, y0 = dp(60), cell = board / 4f;
        for (int i = 0; i < 16; i++) {
            int r = i / 4, c = i % 4;
            float l = x0 + c * cell + dp(4), t = y0 + r * cell + dp(4);
            float rr = l + cell - dp(8), bb = t + cell - dp(8);
            boolean up = matched[i] || i == first || i == second;
            roundRect(cv, l, t, rr, bb, dp(10), up ? Glass.GLASS_FILL_STRONG : Glass.GLASS_FILL);
            if (up) drawShape(cv, cards[i], (l + rr) / 2, (t + bb) / 2, cell * 0.26f);
            else centeredText(cv, "?", (l + rr) / 2, (t + bb) / 2, 20, Glass.TEXT_DIM, false);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        if (e.getY() < dp(52)) { reset(); invalidate(); return true; }
        if (second >= 0) return true;
        float w = getWidth(), pad = dp(16);
        float board = Math.min(w - pad * 2, getHeight() - dp(110));
        float x0 = (w - board) / 2, y0 = dp(60), cell = board / 4f;
        int c = (int) ((e.getX() - x0) / cell), r = (int) ((e.getY() - y0) / cell);
        if (r < 0 || r >= 4 || c < 0 || c >= 4) return true;
        int i = r * 4 + c;
        if (matched[i] || i == first) return true;
        if (first < 0) first = i;
        else {
            second = i; moves++;
            if (cards[first] == cards[second]) {
                matched[first] = matched[second] = true; pairs++; first = second = -1;
            } else flipBackAt = System.currentTimeMillis() + 650;
        }
        invalidate();
        return true;
    }
}
