package com.jackbhai.omnitools.nativ.tools;

import android.app.Activity;
import android.content.Intent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Entry point for the native tools batch (Track A). The coordinator
 * routes Menu taps here exactly like NativeGames: if
 * {@link #isNative(String)} is true for a Registry id, call
 * {@link #launch(Activity, String, String)} instead of WebFrameActivity.
 *
 * Ids "recorder" and "scan" already exist in Registry.TOOLS. The other
 * six (flashlight, compass, level, noise, qrscan, files) are new tools
 * with no Registry row yet — see the batch report for the exact
 * Registry / MenuSection changes the coordinator must make.
 */
public final class NativeTools {

    public static final String EXTRA_ID = "tool_id";
    public static final String EXTRA_TITLE = "tool_title";

    public static final class Entry {
        public final String id, title;
        public final Class<? extends Activity> activity;
        Entry(String id, String title, Class<? extends Activity> activity) {
            this.id = id; this.title = title; this.activity = activity;
        }
    }

    private static final Map<String, Entry> TABLE = new LinkedHashMap<>();
    static {
        add(new Entry("recorder", "Voice Recorder", RecorderActivity.class));
        add(new Entry("scan", "Doc Scanner", ScannerActivity.class));
        add(new Entry("flashlight", "Flashlight + SOS", FlashlightActivity.class));
        add(new Entry("compass", "Compass", CompassActivity.class));
        add(new Entry("level", "Level", LevelActivity.class));
        add(new Entry("noise", "Noise Meter", NoiseActivity.class));
        add(new Entry("qrscan", "QR / Barcode Scanner", QrActivity.class));
        add(new Entry("files", "Files", FilesActivity.class));
    }
    private static void add(Entry e) { TABLE.put(e.id, e); }

    private NativeTools() {}

    public static boolean isNative(String registryId) {
        return registryId != null && TABLE.containsKey(registryId);
    }

    public static Entry entry(String registryId) {
        return registryId == null ? null : TABLE.get(registryId);
    }

    public static String[] nativeIds() {
        return TABLE.keySet().toArray(new String[0]);
    }

    public static Entry[] entries() {
        return TABLE.values().toArray(new Entry[0]);
    }

    /** Launch by registry id; the table supplies the title. */
    public static void launch(Activity act, String registryId) {
        Entry e = entry(registryId);
        if (e != null) launch(act, registryId, e.title);
    }

    public static void launch(Activity act, String registryId, String title) {
        Entry e = entry(registryId);
        if (e == null) return;
        Intent it = new Intent(act, e.activity);
        it.putExtra(EXTRA_ID, registryId);
        it.putExtra(EXTRA_TITLE, title != null ? title : e.title);
        act.startActivity(it);
    }
}
