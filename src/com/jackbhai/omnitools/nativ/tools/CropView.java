package com.jackbhai.omnitools.nativ.tools;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import com.jackbhai.omnitools.nativ.Glass;

/**
 * Draggable-corner crop overlay for the Document Scanner. Shows the
 * photo fitted into the view with a quad whose four corners
 * (bitmap-pixel coordinates, order TL, TR, BR, BL) the user drags;
 * {@link ScannerActivity} warps that quad flat with the pure
 * {@link Perspective} maths. Default quad is inset 6% from each edge.
 */
final class CropView extends View {

    private Bitmap bmp;
    private final double[] quad = new double[8]; // bitmap coords
    private float scale, offX, offY;
    private int dragIdx = -1;

    private final Paint imgPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handle = new Paint(Paint.ANTI_ALIAS_FLAG);

    CropView(Context c) {
        super(c);
        line.setColor(Glass.NEON_CYAN);
        line.setStrokeWidth(Glass.dp(c, 2));
        line.setStyle(Paint.Style.STROKE);
        handle.setStyle(Paint.Style.FILL);
        handle.setColor(Glass.NEON_CYAN);
    }

    void setBitmap(Bitmap b) {
        bmp = b;
        double ix = b.getWidth() * 0.06, iy = b.getHeight() * 0.06;
        quad[0] = ix;               quad[1] = iy;
        quad[2] = b.getWidth() - ix; quad[3] = iy;
        quad[4] = b.getWidth() - ix; quad[5] = b.getHeight() - iy;
        quad[6] = ix;               quad[7] = b.getHeight() - iy;
        requestLayout();
        invalidate();
    }

    /** Current quad in bitmap coordinates {TL, TR, BR, BL} x,y pairs. */
    double[] quad() { return quad.clone(); }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = bmp != null && bmp.getWidth() > 0
                ? (int) (w * (double) bmp.getHeight() / bmp.getWidth()) : w;
        setMeasuredDimension(w, h);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (bmp == null || w == 0) return;
        scale = (float) Math.min((double) w / bmp.getWidth(), (double) h / bmp.getHeight());
        offX = (float) ((w - bmp.getWidth() * scale) / 2.0);
        offY = (float) ((h - bmp.getHeight() * scale) / 2.0);
    }

    private float vx(double bx) { return (float) (offX + bx * scale); }
    private float vy(double by) { return (float) (offY + by * scale); }

    @Override protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        if (bmp == null) return;
        cv.save();
        cv.translate(offX, offY);
        cv.scale(scale, scale);
        cv.drawBitmap(bmp, 0, 0, imgPaint);
        cv.restore();
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(vx(quad[0]), vy(quad[1]));
        for (int i = 1; i < 4; i++) p.lineTo(vx(quad[2 * i]), vy(quad[2 * i + 1]));
        p.close();
        cv.drawPath(p, line);
        float hr = Glass.dp(getContext(), 11);
        for (int i = 0; i < 4; i++) cv.drawCircle(vx(quad[2 * i]), vy(quad[2 * i + 1]), hr, handle);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (bmp == null) return false;
        float touchR = Glass.dp(getContext(), 28);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                dragIdx = -1;
                float best = Float.MAX_VALUE;
                for (int i = 0; i < 4; i++) {
                    float dx = e.getX() - vx(quad[2 * i]);
                    float dy = e.getY() - vy(quad[2 * i + 1]);
                    float d = dx * dx + dy * dy;
                    if (d < best) { best = d; dragIdx = i; }
                }
                if (best > touchR * touchR) dragIdx = -1;
                return dragIdx >= 0;
            }
            case MotionEvent.ACTION_MOVE: {
                if (dragIdx < 0) return false;
                double bx = (e.getX() - offX) / scale;
                double by = (e.getY() - offY) / scale;
                quad[2 * dragIdx] = Math.max(0, Math.min(bmp.getWidth(), bx));
                quad[2 * dragIdx + 1] = Math.max(0, Math.min(bmp.getHeight(), by));
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragIdx = -1;
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }
}
