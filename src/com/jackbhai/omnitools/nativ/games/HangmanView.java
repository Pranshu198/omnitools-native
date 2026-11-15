package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.Random;

/** Hangman: tap letters on the on-screen keyboard. 6 wrong guesses
 *  lose; the gallows is drawn step by step. Win score goes to Best. */
public final class HangmanView extends BaseGameView {
    private final Random rnd = new Random();
    private HangmanLogic g = new HangmanLogic(rnd);
    private static final String ROWS = "QWERTYUIOPASDFGHJKLZXCVBNM";
    private static final int[] ROW_LEN = {10, 9, 7};

    public HangmanView(Context c) { super(c); }

    private void reset() { g = new HangmanLogic(rnd); }

    @Override protected void update(long dt) {}
    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        leftText(cv, "Hangman    Lives: " + g.livesLeft() + " of " + HangmanLogic.MAX_WRONG
                + (best("hangman_best") > 0 ? "   •   Best: " + best("hangman_best") : ""), pad, dp(34), 15, Glass.TEXT, true);
        leftText(cv, "Tap: [New Game]", pad, dp(58), 12, Glass.NEON_CYAN, false);
        String st = g.isWon() ? "You saved them — score " + g.score()
                : g.isOver() ? "Game over — the word was " + g.word().toUpperCase()
                : "Guess a letter";
        if (g.isWon()) saveBest("hangman_best", g.score());
        leftText(cv, st, pad, dp(84), 14, Glass.TEXT_DIM, false);

        // gallows
        float gx = w * 0.5f, gy = dp(130), gh = dp(150);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(3)); paint.setColor(Glass.TEXT_DIM);
        cv.drawLine(gx - dp(60), gy + gh, gx + dp(60), gy + gh, paint);
        int stage = g.wrong();
        if (stage >= 1) cv.drawLine(gx - dp(40), gy + gh, gx - dp(40), gy, paint);
        if (stage >= 2) cv.drawLine(gx - dp(40), gy, gx + dp(35), gy, paint);
        if (stage >= 3) cv.drawLine(gx + dp(35), gy, gx + dp(35), gy + dp(24), paint);
        if (stage >= 4) cv.drawCircle(gx + dp(35), gy + dp(38), dp(14), paint);
        if (stage >= 5) cv.drawLine(gx + dp(35), gy + dp(52), gx + dp(35), gy + dp(95), paint);
        if (stage >= 6) {
            cv.drawLine(gx + dp(35), gy + dp(95), gx + dp(18), gy + dp(122), paint);
            cv.drawLine(gx + dp(35), gy + dp(95), gx + dp(52), gy + dp(122), paint);
            cv.drawLine(gx + dp(35), gy + dp(62), gx + dp(16), gy + dp(78), paint);
            cv.drawLine(gx + dp(35), gy + dp(62), gx + dp(54), gy + dp(78), paint);
        }
        paint.setStyle(Paint.Style.FILL);

        // masked word
        centeredText(cv, g.masked().toUpperCase(), w / 2, gy + gh + dp(46), 22, Glass.TEXT, true);

        // keyboard
        float ky = getHeight() - dp(168), kh = dp(46), gap = dp(5);
        int idx = 0;
        for (int r = 0; r < 3; r++) {
            int len = ROW_LEN[r];
            float kw = (w - pad * 2 - gap * (len - 1)) / len;
            for (int i = 0; i < len; i++) {
                char ch = ROWS.charAt(idx++);
                float l = pad + i * (kw + gap), t = ky + r * (kh + gap);
                boolean used = g.isGuessed(Character.toLowerCase(ch));
                boolean hit = used && g.word().indexOf(Character.toLowerCase(ch)) >= 0;
                if (used) roundRect(cv, l, t, l + kw, t + kh, dp(8), hit ? Glass.NEON_CYAN : Glass.GLASS_FILL);
                else glassCell(cv, l, t, l + kw, t + kh, dp(8));
                centeredText(cv, String.valueOf(ch), l + kw / 2, t + kh / 2, 15,
                        used && !hit ? Glass.TEXT_DIM : Glass.TEXT, true);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float w = getWidth(), pad = dp(16);
        if (e.getY() >= dp(44) && e.getY() < dp(70)) { reset(); invalidate(); return true; }
        if (g.isOver()) return true;
        float ky = getHeight() - dp(168), kh = dp(46), gap = dp(5);
        int idx = 0;
        for (int r = 0; r < 3; r++) {
            int len = ROW_LEN[r];
            float kw = (w - pad * 2 - gap * (len - 1)) / len;
            for (int i = 0; i < len; i++) {
                char ch = ROWS.charAt(idx++);
                float l = pad + i * (kw + gap), t = ky + r * (kh + gap);
                if (e.getX() >= l && e.getX() <= l + kw && e.getY() >= t && e.getY() <= t + kh) {
                    g.guess(ch); invalidate(); return true;
                }
            }
        }
        return true;
    }
}
