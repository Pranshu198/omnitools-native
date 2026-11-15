package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.jackbhai.omnitools.nativ.Glass;

/** Shared lean engine for native canvas games: handler game loop (paused on
 *  detach), glass palette paints, dp helper, best-score persistence and a
 *  swipe detector helper. Subclasses implement update(dtMs) + drawGame(). */
public abstract class BaseGameView extends View {
    protected final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final RectF rect = new RectF();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;
    private long lastTick;
    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = System.currentTimeMillis();
            long dt = lastTick == 0 ? 16 : Math.min(50, now - lastTick);
            lastTick = now;
            update(dt);
            invalidate();
            handler.postDelayed(this, 16);
        }
    };

    protected BaseGameView(Context c) {
        super(c);
        setBackgroundColor(Glass.BG_DEEP);
        textPaint.setTypeface(Glass.font(c, false));
    }

    protected final void startLoop() {
        if (running) return;
        running = true; lastTick = 0;
        handler.post(loop);
    }
    protected final void stopLoop() { running = false; handler.removeCallbacks(loop); }
    protected final boolean isLoopRunning() { return running; }
    protected final Handler gameHandler() { return handler; }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); onGameResume(); }
    @Override protected void onDetachedFromWindow() { stopLoop(); super.onDetachedFromWindow(); }
    protected void onGameResume() {}

    protected abstract void update(long dtMs);
    protected abstract void drawGame(Canvas canvas);
    @Override protected final void onDraw(Canvas canvas) { super.onDraw(canvas); drawGame(canvas); }

    protected float dp(float v) { return Glass.dp(getContext(), v); }
    protected SharedPreferences prefs() {
        return getContext().getSharedPreferences("native_games", Context.MODE_PRIVATE);
    }
    protected int best(String key) { return prefs().getInt(key, 0); }
    protected void saveBest(String key, int v) {
        if (v > best(key)) prefs().edit().putInt(key, v).apply();
    }
    protected void saveInt(String key, int v) { prefs().edit().putInt(key, v).apply(); }

    protected void roundRect(Canvas c, float l, float t, float r, float b, float rad, int color) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color);
        rect.set(l, t, r, b); c.drawRoundRect(rect, rad, rad, paint);
    }
    protected void glassCell(Canvas c, float l, float t, float r, float b, float rad) {
        roundRect(c, l, t, r, b, rad, Glass.GLASS_FILL);
        paint.setStyle(Paint.Style.STROKE); paint.setColor(Glass.GLASS_BORDER);
        paint.setStrokeWidth(Math.max(1f, dp(0.75f)));
        rect.set(l, t, r, b); c.drawRoundRect(rect, rad, rad, paint);
        paint.setStyle(Paint.Style.FILL);
    }
    protected void centeredText(Canvas c, String s, float cx, float cy, float sp, int color, boolean bold) {
        textPaint.setTextSize(dp(sp)); textPaint.setColor(color);
        textPaint.setTypeface(Glass.font(getContext(), bold));
        textPaint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint);
    }
    protected void leftText(Canvas c, String s, float x, float yBaseline, float sp, int color, boolean bold) {
        textPaint.setTextSize(dp(sp)); textPaint.setColor(color);
        textPaint.setTypeface(Glass.font(getContext(), bold));
        textPaint.setTextAlign(Paint.Align.LEFT);
        c.drawText(s, x, yBaseline, textPaint);
    }

    /** Minimal swipe detector: call from onTouchEvent down/up. */
    public static final class Swipe {
        private float x0, y0;
        public void down(float x, float y) { x0 = x; y0 = y; }
        /** @return 0 left, 1 right, 2 up, 3 down, -1 none */
        public int dir(float x, float y, float threshold) {
            float dx = x - x0, dy = y - y0;
            if (Math.max(Math.abs(dx), Math.abs(dy)) < threshold) return -1;
            return Math.abs(dx) > Math.abs(dy) ? (dx < 0 ? 0 : 1) : (dy < 0 ? 2 : 3);
        }
    }
}
