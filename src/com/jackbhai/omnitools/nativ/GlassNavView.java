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
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * The bottom navigation bar — glassmorphism with a Neon Wave over the
 * active tab (me20.studio glass-nav variant 4, re-implemented natively).
 *
 * The bar lives inside a BlurView (MainActivity wires it), so the frost
 * under this tint is a real live blur of the content behind. The wave is
 * a living sine stroke in neon cyan with a violet under-glow: it ripples
 * continuously, and when the tab changes it glides to the new tab.
 *
 * Icons are Lucide (ISC) vector drawables. The active tab borrows Chip
 * Navigation Bar's idea: it expands into a glass pill where the label is
 * revealed next to the icon; inactive tabs stay icon + small caption.
 */
public final class GlassNavView extends View {

    public interface OnTab {
        void onTab(int index);
    }

    public static final String[] LABELS = {"TV", "Downloader", "Games", "Songs", "Menu"};
    public static final int TAB_TV = 0, TAB_DOWNLOADER = 1, TAB_GAMES = 2,
            TAB_SONGS = 3, TAB_MENU = 4;

    private static final int[] ICONS = {
        R.drawable.ic_tv, R.drawable.ic_download, R.drawable.ic_games,
        R.drawable.ic_songs, R.drawable.ic_menu,
    };

    private final float density;
    private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint waveGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable[] icons = new Drawable[5];

    private int selected = TAB_SONGS;
    private float waveCenterX = -1;   // px, animated
    private float wavePhase = 0f;     // 0..2pi, animated forever
    private ValueAnimator slideAnim;
    private ValueAnimator phaseAnim;
    private OnTab listener;

    public GlassNavView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        for (int i = 0; i < 5; i++) {
            icons[i] = c.getResources().getDrawable(ICONS[i], null).mutate();
        }
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(2.6f * density);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
        wavePaint.setColor(Glass.NEON_CYAN);
        wavePaint.setShadowLayer(11 * density, 0, 0, Glass.NEON_CYAN);
        waveGlowPaint.setStyle(Paint.Style.STROKE);
        waveGlowPaint.setStrokeWidth(8 * density);
        waveGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        waveGlowPaint.setColor(Color.argb(52, 179, 136, 255));
        labelPaint.setTextAlign(Paint.Align.CENTER);
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

        // Frosted tint + sheen over the BlurView's live blur.
        glassPaint.setShader(new LinearGradient(0, barTop, 0, h,
                new int[]{Color.argb(58, 255, 255, 255),
                          Color.argb(26, 190, 210, 255),
                          Color.argb(40, 255, 255, 255)},
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

        float tabW = w / 5f;
        for (int i = 0; i < 5; i++) {
            float cx = tabW * i + tabW / 2f;
            if (i == selected) drawActiveTab(cv, i, cx, barTop);
            else drawInactiveTab(cv, i, cx, barTop);
        }
    }

    private void drawIcon(Canvas cv, int idx, float cx, float cy, float sizeDp, int color) {
        Drawable d = icons[idx];
        d.setTint(color);
        int half = (int) (sizeDp * density / 2);
        d.setBounds((int) cx - half, (int) cy - half, (int) cx + half, (int) cy + half);
        d.draw(cv);
    }

    /** Chip-Navigation-Bar move: the active tab expands into a pill and
     *  its label reveals next to the icon. */
    private void drawActiveTab(Canvas cv, int idx, float cx, float barTop) {
        labelPaint.setTextSize(12.5f * density);
        labelPaint.setTypeface(Glass.font(getContext(), true));
        float textW = labelPaint.measureText(LABELS[idx]);
        float iconSize = 20 * density;
        float gap = 7 * density;
        float padX = 13 * density;
        float pillW = padX * 2 + iconSize + gap + textW;
        float pillH = 36 * density;
        float pillTop = barTop + 8 * density;
        float left = cx - pillW / 2f;
        // Keep the pill on screen even for edge tabs with long labels.
        left = Math.max(6 * density, Math.min(left, getWidth() - pillW - 6 * density));
        RectF pill = new RectF(left, pillTop, left + pillW, pillTop + pillH);
        pillPaint.setStyle(Paint.Style.FILL);
        pillPaint.setColor(Color.argb(44, 0, 229, 255));
        cv.drawRoundRect(pill, pillH / 2, pillH / 2, pillPaint);
        pillPaint.setStyle(Paint.Style.STROKE);
        pillPaint.setStrokeWidth(Math.max(1f, density));
        pillPaint.setColor(Color.argb(170, 0, 229, 255));
        cv.drawRoundRect(pill, pillH / 2, pillH / 2, pillPaint);

        float cy = pillTop + pillH / 2f;
        drawIcon(cv, idx, left + padX + iconSize / 2f, cy, 20, Glass.NEON_CYAN);
        labelPaint.setTextAlign(Paint.Align.LEFT);
        labelPaint.setColor(Glass.NEON_CYAN);
        float baseline = cy - (labelPaint.ascent() + labelPaint.descent()) / 2f;
        cv.drawText(LABELS[idx], left + padX + iconSize + gap, baseline, labelPaint);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    private void drawInactiveTab(Canvas cv, int idx, float cx, float barTop) {
        int col = Color.argb(178, 205, 218, 235);
        drawIcon(cv, idx, cx, barTop + 20 * density, 21, col);
        labelPaint.setTextSize(10 * density);
        labelPaint.setTypeface(Glass.font(getContext(), false));
        labelPaint.setColor(col);
        cv.drawText(LABELS[idx], cx, barTop + 47 * density, labelPaint);
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
