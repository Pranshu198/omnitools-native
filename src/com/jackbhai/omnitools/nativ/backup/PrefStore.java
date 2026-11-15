package com.jackbhai.omnitools.nativ.backup;

import java.util.Set;

/**
 * Minimal key/value view over one SharedPreferences file — the seam that
 * lets {@link BackupCodec} run (and be JVM-tested) without Android.
 * The Android implementation is {@link SharedPrefStore}; tests use a
 * HashMap-backed fake with identical semantics.
 *
 * <p>One instance per real prefs file:
 * <ul>
 *   <li>{@code "omnitools_native"} — favourites + UI-sound setting</li>
 *   <li>{@code "omnitools_audio"} — EQ / audio / accent settings</li>
 *   <li>{@code "omni_widgets"} — per-widget customisation</li>
 * </ul>
 */
public interface PrefStore {

    boolean contains(String key);

    String getString(String key, String def);
    boolean getBoolean(String key, boolean def);
    int getInt(String key, int def);
    float getFloat(String key, float def);

    /** All keys currently present (widget settings use dynamic key names). */
    Set<String> keys();

    void putString(String key, String value);
    void putBoolean(String key, boolean value);
    void putInt(String key, int value);
    void putFloat(String key, float value);
}
