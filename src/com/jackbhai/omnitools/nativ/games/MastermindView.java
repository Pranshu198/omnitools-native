package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Mastermind: crack the 4-peg code in 10 guesses. After each guess,
 *  dark pegs = right colour + place, light pegs = right colour only.
 *  Tap the palette to fill a row, tap a filled peg to clear from it. */
public final class MastermindView extends BaseGameView {
    private static final int[] PALETTE = {
            Color.rgb(255, 92, 92), Color.rgb(255, 176, 66), Color.rgb(255, 235, 120),
            Color.rgb(92, 220, 140), Color.rgb(0, 229, 255), Color.rgb(179, 136, 255)};
    private final Random rnd = new Random();
    private MastermindLogic g = new MastermindLogic(new Random());
    private final List<int[]> rows = new ArrayList<>();
    private final List<int[]> scores = new ArrayList<>();
    private final int[] cur = {-1, -1, -1, -1};
    private boolean won;

    public MastermindView(Context c) { super(c); }

    private void reset() {
        g = new MastermindLogic(rnd);
        rows.clear(); scores.clear();
        for (int i = 0; i < MastermindLogic.PEGS; i++) cur[i] = -1;
        won = false;
    }
    private int curFilled() { int n = 0; for (int v : cur) if (v >= 0) n++; return n; }
    private boolean over() { return g.isOver(won); }

    @Override protected void update(long dt) {}
    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        leftText(cv, "Mastermind    Guess " + Math.min(g.guesses() + 1, MastermindLogic.MAX_GUESSES)
                + " of " + MastermindLogic.MAX_GUESSES, pad, dp(34), 15, Glass.TEXT, true);
        leftText(cv, "Tap: [New Game]", pad, dp(58), 12, Glass.NEON_CYAN, false);
        String st = won ? "Code cracked in " + g.guesses() + " — you win"
                : over() ? "Out of guesses — code was hidden above"
                : "Fill 4 pegs, then tap Check";
        leftText(cv, st, pad, dp(84), 14, Glass.TEXT_DIM, false);
        float x0 = pad + dp(8), rowH = dp(40), y0 = dp(100);
        // history + current rows
        for (int i = 0; i <= rows.size(); i++) {
            if (i == rows.size() && over()) break;
            boolean isCur = i == rows.size();
            int[] row = isCur ? cur : rows.get(i);
            float cy = y0 + i * rowH + rowH / 2;
            if (isCur) glassCell(cv, x0 - dp(6), cy - rowH / 2 + dp(3), x0 + dp(4) * dp(38) + dp(6),
                    cy + rowH / 2 - dp(3), dp(10));
            for (int p = 0; p < MastermindLogic.PEGS; p++) {
                float cx = x0 + p * dp(38) + dp(17);
                if (row[p] >= 0) { paint.setColor(PALETTE[row[p]]); cv.drawCircle(cx, cy, dp(13), paint); }
                else {
                    paint.setStyle(android.graphics.Paint.Style.STROKE);
                    paint.setColor(Glass.GLASS_BORDER); paint.setStrokeWidth(dp(1.5f));
                    cv.drawCircle(cx, cy, dp(13), paint);
                    paint.setStyle(android.graphics.Paint.Style.FILL);
                }
            }
            if (!isCur) {
                int[] sc = scores.get(i);
                float sx = x0 + dp(4) * dp(38) + dp(26);
                for (int k = 0; k < MastermindLogic.PEGS; k++) {
                    float px = sx + (k % 2) * dp(15), py = cy - dp(8) + (k / 2) * dp(15);
                    if (k < sc[0]) { paint.setColor(Glass.TEXT); cv.drawCircle(px, py, dp(5), paint); }
                    else if (k < sc[0] + sc[1]) {
                        paint.setStyle(android.graphics.Paint.Style.STROKE);
                        paint.setColor(Glass.TEXT); paint.setStrokeWidth(dp(1.5f));
                        cv.drawCircle(px, py, dp(5), paint);
                        paint.setStyle(android.graphics.Paint.Style.FILL);
                    }
                }
            }
        }
        if (over() && !won) { // reveal the code
            float cy = y0 + MastermindLogic.MAX_GUESSES * rowH + dp(22);
            leftText(cv, "Code:", pad, cy + dp(5), 14, Glass.TEXT_DIM, false);
            int[] sec = g.secret();
            for (int p = 0; p < MastermindLogic.PEGS; p++) {
                paint.setColor(PALETTE[sec[p]]);
                cv.drawCircle(x0 + dp(40) + p * dp(38) + dp(17), cy, dp(13), paint);
            }
        }
        // palette + Check button
        float py = getHeight() - dp(120);
        for (int i = 0; i < MastermindLogic.COLORS; i++) {
            float cx = pad + dp(20) + i * dp(44);
            paint.setColor(PALETTE[i]);
            cv.drawCircle(cx, py, dp(17), paint);
        }
        glassCell(cv, pad, py + dp(34), w - pad, py + dp(34) + dp(46), dp(12));
        centeredText(cv, curFilled() == MastermindLogic.PEGS ? "Check" : "Pick " + (4 - curFilled()) + " more",
                w / 2, py + dp(34) + dp(23), 16, Glass.NEON_CYAN, true);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float w = getWidth(), pad = dp(16);
        if (e.getY() >= dp(44) && e.getY() < dp(70)) { reset(); invalidate(); return true; }
        if (over()) return true;
        float py = getHeight() - dp(120);
        // Check button
        if (e.getY() >= py + dp(34) && e.getY() <= py + dp(80)) {
            if (curFilled() == MastermindLogic.PEGS) {
                int[] guess = cur.clone();
                int[] sc = g.score(guess);
                rows.add(guess); scores.add(sc);
                won = g.isWin(sc);
                for (int i = 0; i < MastermindLogic.PEGS; i++) cur[i] = -1;
            }
            invalidate(); return true;
        }
        // palette
        if (Math.abs(e.getY() - py) <= dp(20)) {
            int i = (int) ((e.getX() - pad - dp(3)) / dp(44));
            if (i >= 0 && i < MastermindLogic.COLORS) {
                for (int p = 0; p < MastermindLogic.PEGS; p++)
                    if (cur[p] < 0) { cur[p] = i; break; }
            }
            invalidate(); return true;
        }
        // tap a filled peg in the current row to clear it and the rest
        float x0 = pad + dp(8), rowH = dp(40);
        float cy = dp(100) + rows.size() * rowH + rowH / 2;
        if (Math.abs(e.getY() - cy) <= rowH / 2) {
            int p = (int) ((e.getX() - x0) / dp(38));
            if (p >= 0 && p < MastermindLogic.PEGS && cur[p] >= 0) {
                for (int k = p; k < MastermindLogic.PEGS; k++) cur[k] = -1;
            }
            invalidate(); return true;
        }
        return true;
    }
}
