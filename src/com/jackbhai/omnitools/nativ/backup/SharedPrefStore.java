package com.jackbhai.omnitools.nativ.backup;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Set;

/** {@link PrefStore} backed by a real SharedPreferences file. */
public final class SharedPrefStore implements PrefStore {

    private final SharedPreferences sp;

    public SharedPrefStore(Context ctx, String prefsName) {
        this.sp = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
    }

    @Override public boolean contains(String key) { return sp.contains(key); }

    @Override public String getString(String key, String def) {
        try { return sp.getString(key, def); } catch (ClassCastException e) { return def; }
    }

    @Override public boolean getBoolean(String key, boolean def) {
        try { return sp.getBoolean(key, def); } catch (ClassCastException e) { return def; }
    }

    @Override public int getInt(String key, int def) {
        try { return sp.getInt(key, def); } catch (ClassCastException e) { return def; }
    }

    @Override public float getFloat(String key, float def) {
        try { return sp.getFloat(key, def); } catch (ClassCastException e) { return def; }
    }

    @Override public Set<String> keys() { return sp.getAll().keySet(); }

    @Override public void putString(String key, String value) {
        sp.edit().putString(key, value).apply();
    }

    @Override public void putBoolean(String key, boolean value) {
        sp.edit().putBoolean(key, value).apply();
    }

    @Override public void putInt(String key, int value) {
        sp.edit().putInt(key, value).apply();
    }

    @Override public void putFloat(String key, float value) {
        sp.edit().putFloat(key, value).apply();
    }
}
