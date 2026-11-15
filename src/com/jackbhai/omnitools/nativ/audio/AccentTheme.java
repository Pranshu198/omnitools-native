package com.jackbhai.omnitools.nativ.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.jackbhai.omnitools.nativ.Glass;
import com.jackbhai.omnitools.nativ.audio.mcu.hct.Hct;
import com.jackbhai.omnitools.nativ.audio.mcu.quantize.QuantizerCelebi;
import com.jackbhai.omnitools.nativ.audio.mcu.scheme.SchemeTonalSpot;
import com.jackbhai.omnitools.nativ.audio.mcu.score.Score;

import java.util.List;
import java.util.Map;

/**
 * Dynamic glass accent, powered by the vendored Material Color Utilities
 * (Apache-2.0, see mcu/LICENSE.txt). On a track change the dominant colour
 * of the album art is extracted (Celebi quantizer + MCU Score), an MCU
 * tonal-spot scheme is built from it, and the scheme's primary becomes
 * the app accent through the Glass indirection.
 *
 * A manual choice always wins over the dynamic colour; turning dynamic
 * off restores the manual (or default neon cyan) accent. Persisted in
 * the "omnitools_audio" preferences.
 */
public final class AccentTheme {

    public static final class Choice {
        public final String name;
        public final int color;
        Choice(String name, int color) { this.name = name; this.color = color; }
    }

    /** Manual accents: the Phase-1 neon cyan default plus six more. */
    public static final Choice[] MANUAL = {
            new Choice("Neon Cyan", Color.rgb(0, 229, 255)),
            new Choice("Violet", Color.rgb(179, 136, 255)),
            new Choice("Pink", Color.rgb(255, 77, 141)),
            new Choice("Amber", Color.rgb(255, 179, 0)),
            new Choice("Lime", Color.rgb(118, 255, 3)),
            new Choice("Blue", Color.rgb(68, 138, 255)),
            new Choice("Coral", Color.rgb(255, 110, 64)),
    };

    private final SharedPreferences prefs;
    private boolean dynamicOn;
    private int manualColor;
    private int dynamicColor;   // last art-derived accent, 0 = none yet

    public AccentTheme(Context ctx) {
        prefs = ctx.getSharedPreferences(EqController.PREFS, Context.MODE_PRIVATE);
        dynamicOn = prefs.getBoolean("accent_dynamic", true);
        manualColor = prefs.getInt("accent_manual", MANUAL[0].color);
        dynamicColor = prefs.getInt("accent_dynamic_color", 0);
        applyCurrent();
    }

    public boolean isDynamicOn() { return dynamicOn; }
    public int manualColor() { return manualColor; }
    public int currentAccent() { return Glass.ACCENT; }

    public void setDynamicOn(boolean on) {
        dynamicOn = on;
        prefs.edit().putBoolean("accent_dynamic", on).apply();
        applyCurrent();
    }

    /** Picking a manual colour wins over dynamic until dynamic re-picks
     *  a track colour — the UI labels this honestly. */
    public void setManualColor(int color) {
        manualColor = color;
        prefs.edit().putInt("accent_manual", color).apply();
        setAccent(color);
    }

    /** Called on track change with the now-playing art (any size). */
    public void onArtwork(Bitmap art) {
        if (art == null) return;
        int seed = dominantColor(art);
        if (seed == 0) return;
        int primary = schemePrimary(seed);
        dynamicColor = primary;
        prefs.edit().putInt("accent_dynamic_color", primary).apply();
        if (dynamicOn) setAccent(primary);
    }

    private void applyCurrent() {
        if (dynamicOn && dynamicColor != 0) setAccent(dynamicColor);
        else setAccent(manualColor);
    }

    private static void setAccent(int color) {
        Glass.ACCENT = color;
        Glass.NEON_CYAN = color;
    }

    /** MCU tonal-spot scheme primary for a seed colour (dark scheme —
     *  the app is a dark glass UI). Pure and JVM-testable. */
    public static int schemePrimary(int seedArgb) {
        try {
            SchemeTonalSpot scheme = new SchemeTonalSpot(Hct.fromInt(seedArgb), true, 0.0);
            return scheme.getPrimary();
        } catch (Throwable t) {
            return seedArgb;
        }
    }

    /** Dominant colour of a bitmap via MCU Celebi quantization + Score;
     *  falls back to a saturation-weighted average. Returns 0 on failure. */
    public static int dominantColor(Bitmap art) {
        try {
            Bitmap small = Bitmap.createScaledBitmap(art, 32, 32, false);
            int[] pixels = new int[32 * 32];
            small.getPixels(pixels, 0, 32, 0, 0, 32, 32);
            if (small != art) small.recycle();
            Map<Integer, Integer> counts = QuantizerCelebi.quantize(pixels, 32);
            List<Integer> ranked = Score.score(counts);
            if (ranked != null && !ranked.isEmpty()) return ranked.get(0);
        } catch (Throwable ignored) {}
        try {
            Bitmap small = Bitmap.createScaledBitmap(art, 16, 16, false);
            long r = 0, g = 0, b = 0, w = 0;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    int px = small.getPixel(x, y);
                    float[] hsv = new float[3];
                    Color.colorToHSV(px, hsv);
                    long weight = 1 + (int) (hsv[1] * 8);
                    r += Color.red(px) * weight;
                    g += Color.green(px) * weight;
                    b += Color.blue(px) * weight;
                    w += weight;
                }
            }
            if (small != art) small.recycle();
            if (w == 0) return 0;
            return Color.rgb((int) (r / w), (int) (g / w), (int) (b / w));
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Seed colour -> scheme map for tests/diagnostics: primary plus a
     *  few tonal steps of the primary palette. */
    public static int[] schemeProbe(int seedArgb) {
        SchemeTonalSpot scheme = new SchemeTonalSpot(Hct.fromInt(seedArgb), true, 0.0);
        return new int[]{
                scheme.getPrimary(),
                scheme.getPrimaryContainer(),
                scheme.getSecondary(),
                scheme.getSurface(),
        };
    }
}
