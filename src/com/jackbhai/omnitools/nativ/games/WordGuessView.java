package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.Random;

/** Word Guess: 5 letters, 6 tries. On-screen keyboard; cyan = right
 *  place, violet = in the word, dim = not in the word. */
public final class WordGuessView extends BaseGameView {
    private static final String ROWS = "QWERTYUIOPASDFGHJKLZXCVBNM";
    private static final int[] ROW_LEN = {10, 9, 7};
    private final Random rnd = new Random();
    private WordGuessLogic g = new WordGuessLogic(rnd);
    private final StringBuilder cur = new StringBuilder();
    private String msg = "";

    public WordGuessView(Context c) { super(c); }

    private void reset() { g = new WordGuessLogic(rnd); cur.setLength(0); msg = ""; }

    @Override protected void update(long dt) {}
    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        leftText(cv, "Word Guess    Try " + Math.min(g.guessCount() + 1, WordGuessLogic.MAX)
                + " of " + WordGuessLogic.MAX
                + (best("wordguess_best") > 0 ? "   •   Best: " + best("wordguess_best") : ""), pad, dp(34), 15, Glass.TEXT, true);
        leftText(cv, "Tap: [New Game]", pad, dp(58), 12, Glass.NEON_CYAN, false);
        String st = g.isWon() ? "Solved in " + g.guessCount() + " — score " + g.score()
                : g.isOver() ? "The word was " + g.answer().toUpperCase()
                : msg;
        if (g.isWon()) saveBest("wordguess_best", g.score());
        leftText(cv, st, pad, dp(84), 14, Glass.TEXT_DIM, false);

        float cell = dp(52), gap = dp(6);
        float bw = cell * WordGuessLogic.LEN + gap * (WordGuessLogic.LEN - 1);
        float x0 = (w - bw) / 2, y0 = dp(104);
        for (int r = 0; r < WordGuessLogic.MAX; r++) {
            for (int c = 0; c < WordGuessLogic.LEN; c++) {
                float l = x0 + c * (cell + gap), t = y0 + r * (cell + gap);
                char ch = 0; int mark = -1;
                if (r < g.guessCount()) { ch = g.guesses().get(r).charAt(c); mark = g.marks().get(r)[c]; }
                else if (r == g.guessCount() && c < cur.length()) ch = cur.charAt(c);
                if (mark == 2) roundRect(cv, l, t, l + cell, t + cell, dp(8), Glass.NEON_CYAN);
                else if (mark == 1) roundRect(cv, l, t, l + cell, t + cell, dp(8), Glass.NEON_VIOLET);
                else if (mark == 0) roundRect(cv, l, t, l + cell, t + cell, dp(8), Glass.GLASS_FILL);
                else glassCell(cv, l, t, l + cell, t + cell, dp(8));
                if (ch != 0) centeredText(cv, String.valueOf(Character.toUpperCase(ch)), l + cell / 2, t + cell / 2, 20,
                        mark == 0 ? Glass.TEXT_DIM : Color.WHITE, true);
            }
        }

        // keyboard (rows + DEL / ENTER on the last row sides)
        float ky = getHeight() - dp(168), kh = dp(46), kgap = dp(5);
        int idx = 0;
        for (int r = 0; r < 3; r++) {
            int len = ROW_LEN[r];
            float kw = (w - pad * 2 - kgap * (len - 1)) / len;
            for (int i = 0; i < len; i++) {
                char ch = ROWS.charAt(idx++);
                float l = pad + i * (kw + kgap), t = ky + r * (kh + kgap);
                int state = g.letterState(Character.toLowerCase(ch));
                if (state == 2) roundRect(cv, l, t, l + kw, t + kh, dp(8), Glass.NEON_CYAN);
                else if (state == 1) roundRect(cv, l, t, l + kw, t + kh, dp(8), Glass.NEON_VIOLET);
                else if (state == 0) roundRect(cv, l, t, l + kw, t + kh, dp(8), Glass.GLASS_FILL);
                else glassCell(cv, l, t, l + kw, t + kh, dp(8));
                centeredText(cv, String.valueOf(ch), l + kw / 2, t + kh / 2, 14,
                        state == 0 ? Glass.TEXT_DIM : Glass.TEXT, true);
            }
        }
        // DEL + ENTER bar
        float by = ky + 3 * (kh + kgap);
        glassCell(cv, pad, by, w / 2 - dp(3), by + kh, dp(8));
        centeredText(cv, "DELETE", (pad + w / 2 - dp(3)) / 2, by + kh / 2, 14, Glass.TEXT, true);
        glassCell(cv, w / 2 + dp(3), by, w - pad, by + kh, dp(8));
        centeredText(cv, "ENTER", (w / 2 + dp(3) + w - pad) / 2, by + kh / 2, 14, Glass.NEON_CYAN, true);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float w = getWidth(), pad = dp(16);
        if (e.getY() >= dp(44) && e.getY() < dp(70)) { reset(); invalidate(); return true; }
        if (g.isOver()) return true;
        float ky = getHeight() - dp(168), kh = dp(46), kgap = dp(5);
        float by = ky + 3 * (kh + kgap);
        if (e.getY() >= by && e.getY() <= by + kh) {
            if (e.getX() < w / 2) { if (cur.length() > 0) cur.deleteCharAt(cur.length() - 1); msg = ""; }
            else if (cur.length() == WordGuessLogic.LEN) {
                if (g.submit(cur.toString())) { cur.setLength(0); msg = ""; }
                else msg = "Not in the word list";
            } else msg = "Type 5 letters first";
            invalidate(); return true;
        }
        int idx = 0;
        for (int r = 0; r < 3; r++) {
            int len = ROW_LEN[r];
            float kw = (w - pad * 2 - kgap * (len - 1)) / len;
            for (int i = 0; i < len; i++) {
                char ch = ROWS.charAt(idx++);
                float l = pad + i * (kw + kgap), t = ky + r * (kh + kgap);
                if (e.getX() >= l && e.getX() <= l + kw && e.getY() >= t && e.getY() <= t + kh) {
                    if (cur.length() < WordGuessLogic.LEN) { cur.append(Character.toLowerCase(ch)); msg = ""; }
                    invalidate(); return true;
                }
            }
        }
        return true;
    }
}
