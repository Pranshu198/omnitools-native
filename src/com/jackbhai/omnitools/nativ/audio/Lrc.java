package com.jackbhai.omnitools.nativ.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Own LRC parser (pure Java, no Android deps — JVM-testable).
 *
 * Supports: [mm:ss.xx] / [mm:ss.xxx] / [mm:ss:xx] timestamps, several
 * timestamps on one line (each becomes its own line with the same text),
 * the [offset:±ms] tag (a positive offset DELAYS every line by that many
 * ms — i.e. it is added to the raw timestamps), ID tags ([ti:], [ar:],
 * [al:], [by:]) and an unsynced plain-text fallback: when a document has
 * no timed lines at all, its non-empty lines are kept as plain text and
 * {@code synced} is false.
 */
public final class Lrc {

    public static final class Line {
        public final long timeMs;
        public final String text;
        public Line(long timeMs, String text) { this.timeMs = timeMs; this.text = text; }
    }

    public static final class Doc {
        public final List<Line> lines;       // sorted by timeMs (synced docs)
        public final List<String> plain;     // unsynced fallback lines
        public final boolean synced;
        public final long offsetMs;
        public final Map<String, String> tags;

        Doc(List<Line> lines, List<String> plain, boolean synced,
            long offsetMs, Map<String, String> tags) {
            this.lines = lines; this.plain = plain; this.synced = synced;
            this.offsetMs = offsetMs; this.tags = tags;
        }

        /** Index of the line active at {@code posMs}, or -1 before the first. */
        public int indexAt(long posMs) {
            int lo = 0, hi = lines.size() - 1, ans = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (lines.get(mid).timeMs <= posMs) { ans = mid; lo = mid + 1; }
                else hi = mid - 1;
            }
            return ans;
        }
    }

    private static final Pattern TIME =
            Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern TAG =
            Pattern.compile("^\\[([A-Za-z#]+):(.*)\\]\\s*$");

    private Lrc() {}

    public static Doc parse(String raw) {
        Map<String, String> tags = new HashMap<>();
        List<Line> timed = new ArrayList<>();
        List<String> plain = new ArrayList<>();
        long offset = 0;
        if (raw != null) {
            for (String row : raw.split("\\r?\\n")) {
                String line = row.trim();
                if (line.isEmpty()) continue;
                Matcher tm = TIME.matcher(line);
                List<Long> stamps = new ArrayList<>();
                int lastEnd = 0;
                while (tm.find()) {
                    long min = Long.parseLong(tm.group(1));
                    long sec = Long.parseLong(tm.group(2));
                    long frac = 0;
                    if (tm.group(3) != null) {
                        String f = tm.group(3);
                        frac = Long.parseLong(f) * (f.length() == 1 ? 100 : f.length() == 2 ? 10 : 1);
                    }
                    stamps.add(((min * 60) + sec) * 1000 + frac);
                    lastEnd = tm.end();
                }
                if (!stamps.isEmpty()) {
                    String text = line.substring(lastEnd).trim();
                    for (long t : stamps) timed.add(new Line(t, text));
                    continue;
                }
                Matcher gm = TAG.matcher(line);
                if (gm.matches()) {
                    String key = gm.group(1).toLowerCase();
                    String val = gm.group(2).trim();
                    tags.put(key, val);
                    if (key.equals("offset")) {
                        try { offset = Long.parseLong(val.replace("+", "")); }
                        catch (NumberFormatException ignored) {}
                    }
                    continue;
                }
                plain.add(line);
            }
        }
        if (offset != 0 && !timed.isEmpty()) {
            List<Line> shifted = new ArrayList<>(timed.size());
            for (Line l : timed) shifted.add(new Line(Math.max(0, l.timeMs + offset), l.text));
            timed = shifted;
        }
        Collections.sort(timed, Comparator.comparingLong(l -> l.timeMs));
        boolean synced = !timed.isEmpty();
        return new Doc(timed, synced ? plain : plain, synced, offset, tags);
    }
}
