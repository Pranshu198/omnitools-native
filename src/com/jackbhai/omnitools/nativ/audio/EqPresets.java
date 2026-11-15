package com.jackbhai.omnitools.nativ.audio;

/**
 * EQ preset shapes (pure Java — JVM-testable). Each preset is a gain curve
 * in dB defined on a canonical 5-band grid (60 / 230 / 910 / 3600 / 14000
 * Hz centres); {@link #gainsDb} linearly interpolates the curve onto any
 * real band count so the same shapes drive both the platform Equalizer
 * and the DynamicsProcessing Pro engine.
 */
public final class EqPresets {

    public static final String CUSTOM = "Custom";

    /** UI preset order: Custom is implicit (any manual slider move). */
    public static final String[] NAMES = {
            "Flat", "Bass Booster", "Vocal", "Treble", "Dance",
            "Rock", "Pop", "Jazz", "Classical",
    };

    // Canonical 5-point curves in dB.
    private static float[] curve(String name) {
        switch (name) {
            case "Bass Booster": return new float[]{7f, 5f, 1f, 0f, 0f};
            case "Vocal":        return new float[]{-2f, 1f, 4f, 4f, 1f};
            case "Treble":       return new float[]{0f, 0f, 1f, 5f, 7f};
            case "Dance":        return new float[]{6f, 4f, 0f, 2f, 5f};
            case "Rock":         return new float[]{5f, 3f, -1f, 3f, 5f};
            case "Pop":          return new float[]{-1f, 2f, 4f, 2f, -1f};
            case "Jazz":         return new float[]{4f, 2f, 1f, 3f, 5f};
            case "Classical":    return new float[]{3f, 2f, 0f, 2f, 4f};
            case "Flat":
            default:             return new float[]{0f, 0f, 0f, 0f, 0f};
        }
    }

    public static boolean isKnown(String name) {
        if (name == null) return false;
        for (String n : NAMES) if (n.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** Gains in dB for {@code bandCount} bands (unknown name = Flat). */
    public static float[] gainsDb(String name, int bandCount) {
        float[] out = new float[Math.max(0, bandCount)];
        if (bandCount == 0) return out;
        float[] c = curve(name == null ? "Flat" : name);
        if (bandCount == 1) { out[0] = c[2]; return out; }
        for (int i = 0; i < bandCount; i++) {
            double pos = (double) i / (bandCount - 1) * (c.length - 1);
            int lo = (int) Math.floor(pos);
            int hi = Math.min(c.length - 1, lo + 1);
            double f = pos - lo;
            out[i] = (float) (c[lo] * (1 - f) + c[hi] * f);
        }
        return out;
    }

    private EqPresets() {}
}
