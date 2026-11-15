package com.jackbhai.omnitools.nativ.tools;

import java.util.Comparator;
import java.util.Locale;

/**
 * Pure maths shared by the native tools batch — no Android imports, so
 * every function here is covered by the JVM tests in tests/tools/.
 */
public final class ToolMath {

    private ToolMath() {}

    // ------------------------------------------------------------- audio dB

    /** RMS of 16-bit PCM samples. 0 for an empty buffer. */
    public static double rmsOf(short[] buf, int n) {
        if (buf == null || n <= 0) return 0;
        double sum = 0;
        for (int i = 0; i < n; i++) { double v = buf[i]; sum += v * v; }
        return Math.sqrt(sum / n);
    }

    /**
     * Estimated sound pressure level from a PCM RMS value. The +94 dB
     * offset is the usual full-scale reference for a typical phone mic;
     * it is an ESTIMATE, never a calibrated measurement (the Noise Meter
     * UI says so). Silence maps to 0.
     */
    public static double dbFromRms(double rmsAbs) {
        if (rmsAbs <= 0) return 0;
        double db = 20.0 * Math.log10(rmsAbs / 32767.0) + 94.0;
        return db < 0 ? 0 : db;
    }

    /** Recorder level meter position 0..1 for a PCM RMS value
     *  (-50 dBFS at the bottom, 0 dBFS at the top). */
    public static double level01(double rmsAbs) {
        if (rmsAbs <= 0) return 0;
        double dbfs = 20.0 * Math.log10(rmsAbs / 32767.0);
        double v = (dbfs + 50.0) / 50.0;
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /** Same meter mapping for MediaRecorder.getMaxAmplitude() (0..32767). */
    public static double level01FromAmplitude(int amplitude) {
        return level01(amplitude);
    }

    // ------------------------------------------------------------- compass

    /** Normalise any degree value into [0, 360). */
    public static double normaliseDeg(double deg) {
        double d = deg % 360.0;
        if (d < 0) d += 360.0;
        return d;
    }

    private static final String[] CARDINALS16 = {
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};

    /** 16-wind cardinal label for a heading in degrees. */
    public static String cardinal(double deg) {
        int idx = (int) Math.floor((normaliseDeg(deg) + 11.25) / 22.5) % 16;
        return CARDINALS16[idx];
    }

    /**
     * Circular low-pass: move {@code prev} toward {@code next} by
     * {@code alpha} along the shortest arc (handles the 359 -> 0 wrap).
     * Result is normalised to [0, 360).
     */
    public static double angleSmooth(double prev, double next, double alpha) {
        double diff = ((next - prev + 540.0) % 360.0) - 180.0;
        return normaliseDeg(prev + alpha * diff);
    }

    // ------------------------------------------------------------- level

    /**
     * Tilt angles (degrees) from a gravity vector. Returns
     * {tiltX, tiltY}: tiltX is the left/right lean (rotation about the
     * device's Y axis as seen holding it upright), tiltY the
     * forward/back lean. A perfectly flat device reads {0, 0}.
     */
    public static double[] levelAngles(double ax, double ay, double az) {
        double tiltX = Math.toDegrees(Math.atan2(ax, Math.sqrt(ay * ay + az * az)));
        double tiltY = Math.toDegrees(Math.atan2(ay, Math.sqrt(ax * ax + az * az)));
        return new double[]{tiltX, tiltY};
    }

    /** True when both tilts are within {@code tolDeg} of flat. */
    public static boolean isLevel(double tiltX, double tiltY, double tolDeg) {
        return Math.abs(tiltX) <= tolDeg && Math.abs(tiltY) <= tolDeg;
    }

    // ------------------------------------------------------------- formatting

    /** Human byte size: "0 B", "512 B", "1.0 KB", "1.5 MB", "2.3 GB". */
    public static String formatBytes(long bytes) {
        if (bytes < 0) return "?";
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double v = bytes;
        int u = -1;
        while (v >= 1024 && u < units.length - 1) { v /= 1024.0; u++; }
        return String.format(Locale.US, "%.1f %s", v, units[u]);
    }

    /** "0:00", "1:05", "12:34", "1:02:03" for durations >= 1 hour. */
    public static String formatDurationMs(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000;
        long h = totalSec / 3600, m = (totalSec % 3600) / 60, s = totalSec % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    // ------------------------------------------------------------- file sort

    public static final int SORT_NAME = 0, SORT_SIZE = 1, SORT_DATE = 2;

    /**
     * File ordering: directories first, then by the chosen key
     * (name ascending case-insensitive, size descending, newest first).
     * Name is always the final tie-break.
     */
    public static Comparator<FileItem> fileComparator(final int mode) {
        return new Comparator<FileItem>() {
            @Override public int compare(FileItem a, FileItem b) {
                if (a.isDir != b.isDir) return a.isDir ? -1 : 1;
                int c;
                switch (mode) {
                    case SORT_SIZE: c = Long.compare(b.size, a.size); break;
                    case SORT_DATE: c = Long.compare(b.modifiedMs, a.modifiedMs); break;
                    default: c = 0;
                }
                if (c != 0) return c;
                return a.name.compareToIgnoreCase(b.name);
            }
        };
    }
}
