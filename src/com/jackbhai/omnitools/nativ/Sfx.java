package com.jackbhai.omnitools.nativ;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.util.HashMap;
import java.util.Map;

/**
 * UI sound effects — a hand-picked set of 20 soft sounds from Kenney's
 * CC0 packs (Interface Sounds: glass tones, plucks, confirmations;
 * UI Audio: soft clicks and switches), fetched from the CC0 mirror the
 * project's shortlist documents because kenney.nl blocks this build
 * sandbox. Combined size 117 KB in res/raw.
 *
 * Everything is deliberately quiet (half volume, sonification stream)
 * and one tap away from silent: the mute toggle lives at the top of the
 * Menu section and is honoured by every call site via muted().
 */
public final class Sfx {

    private Sfx() {}

    private static SoundPool pool;
    private static SharedPreferences prefs;
    private static final Map<Integer, Integer> IDS = new HashMap<>();
    private static boolean ready = false;

    private static final int[] ALL = {
        R.raw.sfx_tap, R.raw.sfx_select, R.raw.sfx_select2, R.raw.sfx_tick,
        R.raw.sfx_toggle_on, R.raw.sfx_toggle_off, R.raw.sfx_scroll, R.raw.sfx_scroll2,
        R.raw.sfx_glass, R.raw.sfx_glass2, R.raw.sfx_confirm, R.raw.sfx_confirm2,
        R.raw.sfx_confirm3, R.raw.sfx_bong, R.raw.sfx_pluck, R.raw.sfx_pluck2,
        R.raw.sfx_drop, R.raw.sfx_drop2, R.raw.sfx_open, R.raw.sfx_close,
    };

    public static synchronized void init(Context c) {
        if (ready) return;
        ready = true;
        prefs = c.getSharedPreferences("omnitools_native", Context.MODE_PRIVATE);
        pool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        for (int res : ALL) IDS.put(res, pool.load(c, res, 1));
    }

    public static boolean muted() {
        return prefs == null || prefs.getBoolean("sfx_muted", false);
    }

    public static void setMuted(boolean m) {
        if (prefs != null) prefs.edit().putBoolean("sfx_muted", m).apply();
    }

    private static void play(int res) {
        if (pool == null || muted()) return;
        Integer id = IDS.get(res);
        if (id != null && id != 0) pool.play(id, 0.5f, 0.5f, 0, 0, 1f);
    }

    /* Semantic call sites — the pack each sound came from is in the name
     * of the raw file; these wrappers keep sections readable. */
    public static void tap()        { play(R.raw.sfx_tap); }
    public static void tab()        { play(R.raw.sfx_scroll); }
    public static void play_()      { play(R.raw.sfx_select); }
    public static void pause()      { play(R.raw.sfx_tick); }
    public static void favourite()  { play(R.raw.sfx_pluck); }
    public static void unfavourite(){ play(R.raw.sfx_drop2); }
    public static void success()    { play(R.raw.sfx_confirm); }
    public static void glass()      { play(R.raw.sfx_glass); }
    public static void open()       { play(R.raw.sfx_open); }
    public static void close()      { play(R.raw.sfx_close); }
    public static void toggleOn()   { play(R.raw.sfx_toggle_on); }
    public static void toggleOff()  { play(R.raw.sfx_toggle_off); }
}
