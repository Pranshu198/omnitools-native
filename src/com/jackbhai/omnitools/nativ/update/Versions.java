package com.jackbhai.omnitools.nativ.update;

/**
 * Dotted version compare for OmniTools Native version names.
 *
 * <p>Version names look like {@code "3.2-native.1"} and release tags like
 * {@code "v3.2-native.1"}. Rules (a superset of the old WebView app's
 * UpdateCheck, which compared plain numbers per segment):
 * <ul>
 *   <li>A leading {@code v}/{@code V} is ignored.</li>
 *   <li>Segments split on {@code .}; a missing segment counts as 0.</li>
 *   <li>Each segment is compared by its leading number first
 *       ({@code "2-native"} -&gt; 2, {@code "native"} -&gt; 0).</li>
 *   <li>On a numeric tie, a segment WITHOUT a suffix outranks the same
 *       number WITH one ({@code "2"} &gt; {@code "2-native"} — a release
 *       beats its pre-release); two suffixes compare lexicographically.</li>
 * </ul>
 * Pure Java — JVM-tested by UpdateTestMain.
 */
public final class Versions {

    private Versions() {}

    /** True iff {@code latest} is strictly newer than {@code current}.
     *  Null/empty on either side is never "newer" (matches the old app's
     *  UpdateCheck contract: an unreadable current version stays silent). */
    public static boolean isNewer(String latest, String current) {
        if (split(latest).length == 0 || split(current).length == 0) return false;
        return compare(latest, current) > 0;
    }

    /** Negative / zero / positive as {@code a} is older / equal / newer than {@code b}. */
    public static int compare(String a, String b) {
        String[] as = split(a), bs = split(b);
        int n = Math.max(as.length, bs.length);
        for (int i = 0; i < n; i++) {
            int c = compareSegment(i < as.length ? as[i] : "0",
                                   i < bs.length ? bs[i] : "0");
            if (c != 0) return c;
        }
        return 0;
    }

    private static String[] split(String v) {
        if (v == null) return new String[0];
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        if (s.isEmpty()) return new String[0];
        return s.split("\\.");
    }

    private static int compareSegment(String a, String b) {
        int na = leadingNumber(a), nb = leadingNumber(b);
        if (na != nb) return na < nb ? -1 : 1;
        String sa = suffix(a), sb = suffix(b);
        if (sa.equals(sb)) return 0;
        if (sa.isEmpty()) return 1;    // plain release beats suffixed pre-release
        if (sb.isEmpty()) return -1;
        return sa.compareTo(sb) < 0 ? -1 : 1;
    }

    private static int leadingNumber(String seg) {
        int i = 0;
        while (i < seg.length() && Character.isDigit(seg.charAt(i))) i++;
        if (i == 0) return 0;
        try {
            return Integer.parseInt(seg.substring(0, i));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE; // absurdly long digit run: treat as huge
        }
    }

    private static String suffix(String seg) {
        int i = 0;
        while (i < seg.length() && Character.isDigit(seg.charAt(i))) i++;
        return seg.substring(i);
    }
}
