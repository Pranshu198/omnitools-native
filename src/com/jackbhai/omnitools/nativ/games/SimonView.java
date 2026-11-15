package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.ArrayList;
import java.util.Random;

/** Simon Sequence — 4 glass pads, growing sequence, playback speeds up,
 *  fail state, best streak saved. */
public final class SimonView extends BaseGameView {
    private static final int[] PAD_COLORS = {Glass.NEON_CYAN, Glass.NEON_VIOLET,
            Color.rgb(255, 190, 80), Color.rgb(120, 230, 140)};
    private static final String[] PAD_NAMES = {"Cyan", "Violet", "Amber", "Green"};
    private final ArrayList<Integer> seq = new ArrayList<>();
    private final Random rnd = new Random();
    private int inputIdx, lit = -1;
    private boolean playing, inputOpen, failed, idle = true;

    public SimonView(Context c) { super(c); }

    private void startGame() {
        seq.clear(); failed = false; idle = false; nextRound();
    }
    private void nextRound() {
        seq.add(rnd.nextInt(4)); inputIdx = 0; playSequence();
    }
    private void playSequence() {
        playing = true; inputOpen = false;
        long step = Math.max(240, 560 - seq.size() * 22L);
        for (int i = 0; i < seq.size(); i++) {
            final int idx = i;
            gameHandler().postDelayed(() -> { lit = seq.get(idx); invalidate(); }, 500 + i * step);
            gameHandler().postDelayed(() -> { lit = -1; invalidate(); }, 500 + i * step + (long) (step * 0.62));
        }
        gameHandler().postDelayed(() -> { playing = false; inputOpen = true; invalidate(); },
                500 + seq.size() * step);
    }

    @Override protected void update(long dt) {}
    @Override protected void onGameResume() { invalidate(); }

    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        String head = idle ? "Tap Start, watch the pads, repeat the sequence"
                : failed ? "Wrong pad — streak was " + (seq.size() - 1) + ". Tap Start to retry"
                : playing ? "Watch..." : "Your turn — step " + (inputIdx + 1) + " of " + seq.size();
        leftText(cv, head, pad, dp(36), 14, Glass.TEXT, true);
        leftText(cv, "Level: " + (idle ? 0 : seq.size()) + "    Best streak: " + best("best_simon"), pad, dp(60), 12.5f, Glass.TEXT_DIM, false);
        leftText(cv, "[ Start ]", pad, dp(88), 14, Glass.NEON_CYAN, true);
        float board = Math.min(w - pad * 2, getHeight() - dp(190));
        float x0 = (w - board) / 2, y0 = dp(110), half = board / 2f;
        for (int i = 0; i < 4; i++) {
            float l = x0 + (i % 2) * half + dp(5), t = y0 + (i / 2) * half + dp(5);
            float rr = l + half - dp(10), bb = t + half - dp(10);
            int col = PAD_COLORS[i];
            int fill = lit == i ? col : Color.argb(52, Color.red(col), Color.green(col), Color.blue(col));
            roundRect(cv, l, t, rr, bb, dp(16), fill);
            centeredText(cv, PAD_NAMES[i], (l + rr) / 2, (t + bb) / 2, 14,
                    lit == i ? Glass.BG_DEEP : Glass.TEXT, true);
        }
    }

    private int padAt(float x, float y) {
        float w = getWidth(), pad = dp(16);
        float board = Math.min(w - pad * 2, getHeight() - dp(190));
        float x0 = (w - board) / 2, y0 = dp(110), half = board / 2f;
        if (x < x0 || x > x0 + board || y < y0 || y > y0 + board) return -1;
        int c = (int) ((x - x0) / half), r = (int) ((y - y0) / half);
        return r * 2 + c;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            if (e.getY() >= dp(70) && e.getY() < dp(100) && e.getX() < dp(130)) { startGame(); return true; }
            if (!inputOpen) return true;
            int p = padAt(e.getX(), e.getY());
            if (p < 0) return true;
            lit = p; invalidate();
            if (p != seq.get(inputIdx)) {
                failed = true; inputOpen = false;
                saveBest("best_simon", seq.size() - 1);
                gameHandler().postDelayed(() -> { lit = -1; invalidate(); }, 300);
                return true;
            }
            inputIdx++;
            gameHandler().postDelayed(() -> {
                lit = -1; invalidate();
                if (!failed && inputIdx >= seq.size()) { inputOpen = false; nextRound(); }
            }, 220);
            return true;
        }
        return true;
    }
}
