package com.jackbhai.omnitools.nativ;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * The bottom navigation bar — glassmorphism with a Neon Wave over the
 * active tab (me20.studio glass-nav variant 4, re-implemented natively).
 *
 * The bar itself is a translucent frosted panel (its backdrop is a blurred
 * copy of the ambient background, set up by MainActivity on API 31+ via
 * RenderEffect; the tint drawn here is the sub-31 fallback and the sheen
 * on every API level). The wave is a living sine stroke in neon cyan with
 * a violet under-glow: it continuously ripples, and when the tab changes
 * it slides to the new tab with a decelerating glide.
 */
public final class GlassNavView extends View {

    public interface OnTab {
        void onTab(int index);
    }

    public static final String[] LABELS = {"TV", "Downloader", "Games", "Songs", "Menu"};
    public static final int TAB_TV = 0, TAB_DOWNLOADER = 1, TAB_GAMES = 2,
            TAB_SONGS = 3, TAB_MENU = 4;

    private final float density;
    private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint waveGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int selected = TAB_SONGS;
    private float waveCenterX = -1;   // px, animated
    private float wavePhase = 0f;     // 0..2pi, animated forever
    private ValueAnimator slideAnim;
    private ValueAnimator phaseAnim;
    private OnTab listener;

    public GlassNavView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(2.6f * density);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
        wavePaint.setColor(Glass.NEON_CYAN);
        wavePaint.setShadowLayer(11 * density, 0, 0, Glass.NEON_CYAN);
        waveGlowPaint.setStyle(Paint.Style.STROKE);
        waveGlowPaint.setStrokeWidth(8 * density);
        waveGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        waveGlowPaint.setColor(Color.argb(52, 179, 136, 255));
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(1.8f * density);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(11 * density);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // shadowLayer needs it
    }

    public void setOnTab(OnTab l) { listener = l; }

    public int getSelected() { return selected; }

    public void select(int index, boolean animate) {
        if (index == selected && waveCenterX >= 0) return;
        selected = index;
        float target = tabCenter(index);
        if (!animate || waveCenterX < 0 || target < 0) {
            waveCenterX = target;
            invalidate();
            return;
        }
        if (slideAnim != null) slideAnim.cancel();
        final float from = waveCenterX;
        slideAnim = ValueAnimator.ofFloat(from, target);
        slideAnim.setDuration(380);
        slideAnim.setInterpolator(new DecelerateInterpolator());
        slideAnim.addUpdateListener(a -> {
            waveCenterX = (float) a.getAnimatedValue();
            invalidate();
        });
        slideAnim.start();
    }

    private float tabCenter(int i) {
        int w = getWidth();
        if (w == 0) return -1;
        float tabW = w / 5f;
        return tabW * i + tabW / 2f;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (phaseAnim == null) {
            phaseAnim = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
            phaseAnim.setDuration(1700);
            phaseAnim.setRepeatCount(ValueAnimator.INFINITE);
            phaseAnim.setRepeatMode(ValueAnimator.RESTART);
            phaseAnim.setInterpolator(null);
            phaseAnim.addUpdateListener(a -> {
                wavePhase = (float) a.getAnimatedValue();
                invalidate();
            });
            phaseAnim.start();
        }
    }

    @Override protected void onDetachedFromWindow() {
        if (phaseAnim != null) { phaseAnim.cancel(); phaseAnim = null; }
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (waveCenterX < 0) waveCenterX = tabCenter(selected);
    }

    private float waveBandH() { return 13 * density; }

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        float band = waveBandH();
        float barTop = band * 0.55f;
        RectF bar = new RectF(0, barTop, w, h + 24 * density);

        // Frosted tint + sheen.
        glassPaint.setShader(new LinearGradient(0, barTop, 0, h,
                new int[]{Color.argb(64, 255, 255, 255),
                          Color.argb(30, 190, 210, 255),
                          Color.argb(44, 255, 255, 255)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        glassPaint.setStyle(Paint.Style.FILL);
        cv.drawRoundRect(bar, 22 * density, 22 * density, glassPaint);
        glassPaint.setShader(null);

        // Hairline border along the top edge.
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(Math.max(1f, density * 0.8f));
        borderPaint.setColor(Color.argb(96, 255, 255, 255));
        Path top = new Path();
        top.moveTo(18 * density, barTop + 0.5f);
        top.lineTo(w - 18 * density, barTop + 0.5f);
        cv.drawPath(top, borderPaint);

        drawWave(cv, w, barTop);

        // Tabs: icon + label.
        float tabW = w / 5f;
        for (int i = 0; i < 5; i++) {
            float cx = tabW * i + tabW / 2f;
            boolean active = i == selected;
            int col = active ? Glass.NEON_CYAN : Color.argb(178, 205, 218, 235);
            iconPaint.setColor(col);
            labelPaint.setColor(col);
            labelPaint.setFakeBoldText(active);
            float iconCy = barTop + 21 * density;
            drawIcon(cv, i, cx, iconCy, active);
            cv.drawText(LABELS[i], cx, barTop + 47 * density, labelPaint);
        }
    }

    private void drawWave(Canvas cv, int w, float barTop) {
        if (waveCenterX < 0) return;
        float tabW = w / 5f;
        float half = tabW * 0.44f;
        float x0 = Math.max(2, waveCenterX - half);
        float x1 = Math.min(w - 2, waveCenterX + half);
        float baseY = barTop + 1.5f * density;
        Path p = new Path();
        int steps = 36;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float x = x0 + (x1 - x0) * t;
            double env = Math.sin(Math.PI * t);                    // hump envelope
            double ripple = Math.sin(wavePhase + t * Math.PI * 2.2);
            float y = baseY - (float) (env * (2.6 + 1.9 * ripple) * density);
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        cv.drawPath(p, waveGlowPaint);
        cv.drawPath(p, wavePaint);
        // Bright crest dot riding the middle of the wave.
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(Color.WHITE);
        dot.setShadowLayer(8 * density, 0, 0, Glass.NEON_CYAN);
        double midRipple = Math.sin(wavePhase + Math.PI * 1.1);
        float dotY = baseY - (float) ((2.6 + 1.9 * midRipple) * density);
        cv.drawCircle(waveCenterX, dotY, 2.1f * density, dot);
    }

    private void drawIcon(Canvas cv, int idx, float cx, float cy, boolean active) {
        float s = density; // scale unit
        Path p = new Path();
        switch (idx) {
            case TAB_TV: {
                RectF r = new RectF(cx - 10 * s, cy - 6 * s, cx + 10 * s, cy + 8 * s);
                cv.drawRoundRect(r, 2.5f * s, 2.5f * s, iconPaint);
                p.moveTo(cx - 6 * s, cy - 8 * s); p.lineTo(cx, cy - 13 * s); p.lineTo(cx + 6 * s, cy - 8 * s);
                cv.drawPath(p, iconPaint);
                break;
            }
            case TAB_DOWNLOADER: {
                p.moveTo(cx, cy - 11 * s); p.lineTo(cx, cy + 3 * s);
                p.moveTo(cx - 5 * s, cy - 2 * s); p.lineTo(cx, cy + 3 * s); p.lineTo(cx + 5 * s, cy - 2 * s);
                p.moveTo(cx - 9 * s, cy + 9 * s); p.lineTo(cx + 9 * s, cy + 9 * s);
                p.moveTo(cx - 9 * s, cy + 4 * s); p.lineTo(cx - 9 * s, cy + 9 * s);
                p.moveTo(cx + 9 * s, cy + 4 * s); p.lineTo(cx + 9 * s, cy + 9 * s);
                cv.drawPath(p, iconPaint);
                break;
            }
            case TAB_GAMES: {
                RectF r = new RectF(cx - 12 * s, cy - 7 * s, cx + 12 * s, cy + 7 * s);
                cv.drawRoundRect(r, 6 * s, 6 * s, iconPaint);
                p.moveTo(cx - 8 * s, cy); p.lineTo(cx - 3 * s, cy);
                p.moveTo(cx - 5.5f * s, cy - 2.5f * s); p.lineTo(cx - 5.5f * s, cy + 2.5f * s);
                cv.drawPath(p, iconPaint);
                Paint fill = new Paint(iconPaint);
                fill.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx + 4.5f * s, cy - 2 * s, 1.5f * s, fill);
                cv.drawCircle(cx + 7.5f * s, cy + 2 * s, 1.5f * s, fill);
                break;
            }
            case TAB_SONGS: {
                // Quaver: two note heads, stems, beam.
                Paint fill = new Paint(iconPaint);
                fill.setStyle(Paint.Style.FILL);
                cv.drawOval(new RectF(cx - 10 * s, cy + 4 * s, cx - 3.5f * s, cy + 9.5f * s), fill);
                cv.drawOval(new RectF(cx + 2 * s, cy + 2 * s, cx + 8.5f * s, cy + 7.5f * s), fill);
                p.moveTo(cx - 3.5f * s, cy + 6 * s); p.lineTo(cx - 3.5f * s, cy - 9 * s);
                p.lineTo(cx + 8.5f * s, cy - 11 * s); p.lineTo(cx + 8.5f * s, cy + 4.5f * s);
                cv.drawPath(p, iconPaint);
                break;
            }
            case TAB_MENU: {
                float g = 5.2f * s, r2 = 1.8f * s;
                for (int dx = -1; dx <= 1; dx += 2) {
                    for (int dy = -1; dy <= 1; dy += 2) {
                        RectF cell = new RectF(cx + dx * g - 4.4f * s, cy + dy * g - 4.4f * s,
                                cx + dx * g + 4.4f * s, cy + dy * g + 4.4f * s);
                        cv.drawRoundRect(cell, r2, r2, iconPaint);
                    }
                }
                break;
            }
            default: break;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            int idx = Math.min(4, Math.max(0, (int) (e.getX() / (getWidth() / 5f))));
            if (listener != null) listener.onTab(idx);
            performClick();
            return true;
        }
        return e.getAction() == MotionEvent.ACTION_DOWN || super.onTouchEvent(e);
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
