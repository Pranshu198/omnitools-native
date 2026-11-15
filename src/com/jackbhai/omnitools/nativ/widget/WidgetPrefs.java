package com.jackbhai.omnitools.nativ.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-widget customisation store ("omni_widgets" SharedPreferences, keyed
 * by widget id) plus the fake-glass background renderer.
 *
 * RemoteViews cannot do live blur or animation, so the glass look is a
 * pre-rendered bitmap: a dark, accent-tinted translucent rounded rect with
 * a neon hairline border and a soft top highlight, drawn once per
 * (widget id, size, accent, transparency) and cached in memory. The same
 * bitmap is handed to every refresh until one of those inputs changes.
 */
public final class WidgetPrefs {

    public static final String PREFS = "omni_widgets";

    /** Neon / glass accent palette offered by the config screen. */
    public static final int[] ACCENTS = {
            Color.rgb(0, 229, 255),    // cyan
            Color.rgb(179, 136, 255),  // violet
            Color.rgb(255, 92, 168),   // magenta
            Color.rgb(0, 230, 118),    // emerald
            Color.rgb(255, 196, 0),    // gold
            Color.rgb(68, 138, 255),   // blue
            Color.rgb(255, 110, 64),   // coral
    };
    public static final String[] ACCENT_NAMES = {
            "Cyan", "Violet", "Magenta", "Emerald", "Gold", "Blue", "Coral",
    };

    public static final int DEFAULT_TRANSPARENCY = 20; // percent, 0..90

    private WidgetPrefs() {}

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int accentFor(Context c, int widgetId) {
        return prefs(c).getInt("accent_" + widgetId, ACCENTS[0]);
    }

    /** Background transparency percent, 0 (solid) .. 90 (nearly clear). */
    public static int transparencyFor(Context c, int widgetId) {
        int t = prefs(c).getInt("transp_" + widgetId, DEFAULT_TRANSPARENCY);
        return Math.max(0, Math.min(90, t));
    }

    public static boolean showArtFor(Context c, int widgetId) {
        return prefs(c).getBoolean("showart_" + widgetId, true);
    }

    /** Ordered Quick Tools selection (tool keys), at most 4. */
    public static String[] toolsFor(Context c, int widgetId) {
        String raw = prefs(c).getString("tools_" + widgetId, null);
        if (raw == null || raw.trim().isEmpty()) {
            return new String[]{"songs", "tv", "games", "downloader"};
        }
        String[] parts = raw.split(",");
        int n = Math.min(parts.length, 4);
        String[] out = new String[n];
        System.arraycopy(parts, 0, out, 0, n);
        return out;
    }

    /** Current widget size in dp from its options bundle, with fallbacks. */
    public static int[] sizeDp(Context c, int widgetId, int defW, int defH) {
        int w = defW, h = defH;
        try {
            Bundle o = AppWidgetManager.getInstance(c).getAppWidgetOptions(widgetId);
            if (o != null) {
                w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, defW);
                h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, defH);
                if (w <= 0) w = defW;
                if (h <= 0) h = defH;
            }
        } catch (Exception ignored) {}
        return new int[]{w, h};
    }

    // ------------------------------------------------------------------
    // Fake-glass bitmap rendering (cached)
    // ------------------------------------------------------------------

    private static final Map<String, Bitmap> CACHE = new HashMap<>();

    public static Bitmap glassBitmap(Context c, int widgetId, int wDp, int hDp) {
        int accent = accentFor(c, widgetId);
        int transp = transparencyFor(c, widgetId);
        float density = c.getResources().getDisplayMetrics().density;
        int w = Math.max(8, Math.round(wDp * density));
        int h = Math.max(8, Math.round(hDp * density));
        // Keep the RemoteViews transaction comfortably small.
        float scale = 1f;
        if (w > 1100 || h > 1100) scale = 1100f / Math.max(w, h);
        w = Math.round(w * scale);
        h = Math.round(h * scale);
        String key = widgetId + "_" + w + "x" + h + "_" + accent + "_" + transp;
        synchronized (CACHE) {
            Bitmap hit = CACHE.get(key);
            if (hit != null && !hit.isRecycled()) return hit;
            if (CACHE.size() > 40) CACHE.clear();
            Bitmap b = renderGlass(w, h, accent, transp, density);
            CACHE.put(key, b);
            return b;
        }
    }

    private static Bitmap renderGlass(int w, int h, int accent, int transp, float density) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        float radius = Math.min(w, h) * 0.20f;
        RectF rect = new RectF(1, 1, w - 1, h - 1);
        Path clip = new Path();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);

        // Fill: deep glass base tinted ~16% toward the accent, alpha from
        // the user's transparency choice.
        int alpha = Math.round(255f * (1f - transp / 100f));
        int fill = Color.argb(alpha,
                clamp255(10 + (int) (Color.red(accent) * 0.16f)),
                clamp255(15 + (int) (Color.green(accent) * 0.16f)),
                clamp255(26 + (int) (Color.blue(accent) * 0.16f)));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        cv.drawRoundRect(rect, radius, radius, p);

        // Soft top highlight, clipped to the rounded shape.
        cv.save();
        cv.clipPath(clip);
        p.setShader(new LinearGradient(0, 0, 0, h * 0.55f,
                Color.argb(30, 255, 255, 255), Color.TRANSPARENT,
                Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h * 0.55f, p);
        p.setShader(null);
        cv.restore();

        // Neon hairline border.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, density * 1.1f));
        p.setColor(Color.argb(165, Color.red(accent), Color.green(accent), Color.blue(accent)));
        cv.drawRoundRect(rect, radius, radius, p);
        return b;
    }

    /** Square placeholder shown when a track has no cached artwork. */
    public static Bitmap placeholderArt(Context c, int widgetId, int sizeDp) {
        int accent = accentFor(c, widgetId);
        float density = c.getResources().getDisplayMetrics().density;
        int s = Math.max(16, Math.round(sizeDp * density));
        String key = "art_" + accent + "_" + s;
        synchronized (CACHE) {
            Bitmap hit = CACHE.get(key);
            if (hit != null && !hit.isRecycled()) return hit;
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new LinearGradient(0, 0, s, s,
                    Color.rgb(22, 34, 56), Color.rgb(8, 12, 24), Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, s, s, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2f, density * 2f));
            p.setColor(Color.argb(200, Color.red(accent), Color.green(accent), Color.blue(accent)));
            cv.drawCircle(s / 2f, s / 2f, s * 0.26f, p);
            p.setStyle(Paint.Style.FILL);
            cv.drawCircle(s / 2f, s / 2f, s * 0.07f, p);
            CACHE.put(key, b);
            return b;
        }
    }

    private static int clamp255(int v) { return Math.max(0, Math.min(255, v)); }
}
