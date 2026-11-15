package com.jackbhai.omnitools.nativ.tools;

/**
 * The scan clean-up the PWA scanner proved, ported to pure Java over
 * int[] ARGB pixels so it is JVM-testable: a 1%-clip contrast stretch
 * (one shared LUT from the luminance histogram), an optional
 * document-style greyscale mode, and one 3x3 unsharp pass.
 */
public final class Enhance {

    private Enhance() {}

    /** Luminance histogram (0..255 bins) of an ARGB pixel array. */
    public static int[] luminanceHistogram(int[] px) {
        int[] hist = new int[256];
        for (int p : px) {
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            hist[(r * 299 + g * 587 + b * 114) / 1000]++;
        }
        return hist;
    }

    /**
     * Contrast-stretch LUT: clip the darkest and lightest 1% of the
     * histogram, then stretch what remains across 0..255. A flat image
     * (lo == hi) yields the identity LUT.
     */
    public static int[] buildLut(int[] hist, long total) {
        int[] lut = new int[256];
        if (total <= 0) { for (int v = 0; v < 256; v++) lut[v] = v; return lut; }
        double clip = total * 0.01;
        int lo = 0, hi = 255;
        double acc = 0;
        for (int v = 0; v < 256; v++) { acc += hist[v]; if (acc > clip) { lo = v; break; } }
        acc = 0;
        for (int v = 255; v >= 0; v--) { acc += hist[v]; if (acc > clip) { hi = v; break; } }
        int span = Math.max(1, hi - lo);
        for (int v = 0; v < 256; v++)
            lut[v] = Math.min(255, Math.max(0, (v - lo) * 255 / span));
        return lut;
    }

    /** Document mode: greyscale through the LUT (r == g == b after). */
    public static void applyDoc(int[] px, int[] lut) {
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            int lum = (r * 299 + g * 587 + b * 114) / 1000;
            int v = lut[lum];
            px[i] = 0xFF000000 | (v << 16) | (v << 8) | v;
        }
    }

    /** Colour mode: the same LUT applied per channel. */
    public static void applyColour(int[] px, int[] lut) {
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int r = lut[(p >> 16) & 0xFF], g = lut[(p >> 8) & 0xFF], b = lut[p & 0xFF];
            px[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    private static final float[] SHARPEN = {
            0f, -0.6f, 0f, -0.6f, 3.4f, -0.6f, 0f, -0.6f, 0f};

    /** One unsharp pass over an ARGB array (borders copied unchanged). */
    public static void sharpen(int[] px, int w, int h) {
        if (w < 3 || h < 3) return;
        int[] src = px.clone();
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int idx = y * w + x;
                int out = 0xFF000000;
                for (int c = 0; c < 3; c++) {
                    int shift = 16 - 8 * c;
                    float v = 0;
                    int k = 0;
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++)
                            v += ((src[idx + dy * w + dx] >> shift) & 0xFF) * SHARPEN[k++];
                    int iv = Math.round(v);
                    if (iv < 0) iv = 0; else if (iv > 255) iv = 255;
                    out |= iv << shift;
                }
                px[idx] = out;
            }
        }
    }
}
