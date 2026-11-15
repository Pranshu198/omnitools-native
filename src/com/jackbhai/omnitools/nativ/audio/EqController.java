package com.jackbhai.omnitools.nativ.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.EnvironmentalReverb;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Virtualizer;
import android.os.Build;

import androidx.media3.common.AuxEffectInfo;
import androidx.media3.exoplayer.ExoPlayer;

import com.jackbhai.omnitools.nativ.PlaybackService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Poweramp layer's sound engine. Attaches to the playing player's
 * audio session and owns: a platform Equalizer (real device band count
 * and centre frequencies), a DynamicsProcessing "Pro EQ" (API 28+) that
 * mirrors the same slider gains through its multiband Eq + Mbc stages,
 * BassBoost, Virtualizer and one aux EnvironmentalReverb hung off the
 * ExoPlayer's aux-effect slot.
 *
 * Exactly one EQ engine is live at a time; {@link #liveEngine()} says
 * which, honestly. Everything persists in SharedPreferences
 * ("omnitools_audio") and is re-applied whenever the audio session
 * changes. Master off = every effect disabled and the aux slot cleared.
 * All audiofx behaviour is device-dependent: every call is guarded and
 * any failure falls back to the standard Equalizer.
 */
public final class EqController {

    public static final String PREFS = "omnitools_audio";
    public static final String ENGINE_STANDARD = "Standard (Equalizer)";
    public static final String ENGINE_PRO = "Pro EQ (DynamicsProcessing)";

    // Reverb choices. EnvironmentalReverb has no preset API (that is
    // PresetReverb); each named space below is realized as an
    // EnvironmentalReverb parameter bundle (room level / decay /
    // reflections / reverb level, in millibels, ms and per-mille),
    // approximating the platform's room characters.
    public static final String[] REVERB_NAMES =
            {"None", "Small Room", "Medium Room", "Large Hall", "Plate"};
    // {roomLevel, roomHFLevel, decayTimeMs, decayHFRatio, reflectionsLevel,
    //  reflectionsDelayMs, reverbLevel, reverbDelayMs, diffusion, density}
    private static final int[][] REVERB_PARAMS = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {-800, -1200, 1100, 800, -1500, 20, -800, 30, 1000, 1000},
            {-600, -1000, 1800, 800, -1200, 30, -600, 40, 1000, 1000},
            {-400, -800, 3400, 700, -1000, 40, -300, 60, 1000, 1000},
            {-500, -200, 2500, 900, -800, 15, -400, 25, 1000, 1000},
    };

    private final SharedPreferences prefs;

    private int sessionId = -1;
    private Equalizer equalizer;
    private DynamicsProcessing proEq;
    private BassBoost bassBoost;
    private Virtualizer virtualizer;
    private EnvironmentalReverb reverb;

    private int bandCount = 5;
    private int[] centerFreqHz = {60, 230, 910, 3600, 14000};
    private short levelMinMb = -1500, levelMaxMb = 1500;

    private boolean masterOn;
    private boolean proRequested;
    private boolean proLive;
    private String presetName;
    private float[] gainsDb;
    private int bassStrength;       // 0..1000
    private int virtStrength;       // 0..1000
    private int reverbIndex;        // index into REVERB_NAMES

    public EqController(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        masterOn = prefs.getBoolean("eq_on", true);
        proRequested = prefs.getBoolean("eq_pro", false);
        presetName = prefs.getString("eq_preset", "Flat");
        bassStrength = prefs.getInt("bass", 0);
        virtStrength = prefs.getInt("virt", 0);
        reverbIndex = prefs.getInt("reverb", 0);
        gainsDb = loadGains();
    }

    /* ------------------------------------------------------------ session */

    public int sessionId() { return sessionId; }
    public int bandCount() { return bandCount; }
    public int centerFreqHz(int band) {
        return band >= 0 && band < centerFreqHz.length ? centerFreqHz[band] : 0;
    }
    public boolean isMasterOn() { return masterOn; }
    public boolean isProRequested() { return proRequested; }
    public boolean isProAvailable() { return Build.VERSION.SDK_INT >= 28; }
    public String liveEngine() {
        if (!masterOn) return "Off";
        return proLive ? ENGINE_PRO : ENGINE_STANDARD;
    }
    public String presetName() { return presetName; }
    public float gainDb(int band) {
        return gainsDb != null && band < gainsDb.length ? gainsDb[band] : 0f;
    }
    public int bassStrength() { return bassStrength; }
    public int virtStrength() { return virtStrength; }
    public int reverbIndex() { return reverbIndex; }

    /** (Re)attach to an audio session. Safe to call repeatedly; a no-op
     *  when the session is unchanged and effects already exist. */
    public synchronized void attach(int newSession) {
        if (newSession <= 0) return;
        if (newSession == sessionId && equalizer != null) { applyAll(); return; }
        releaseEffects();
        sessionId = newSession;
        try {
            equalizer = new Equalizer(0, sessionId);
            bandCount = equalizer.getNumberOfBands();
            centerFreqHz = new int[bandCount];
            for (int i = 0; i < bandCount; i++) {
                centerFreqHz[i] = equalizer.getCenterFreq((short) i) / 1000;
            }
            short[] range = equalizer.getBandLevelRange();
            if (range != null && range.length == 2) {
                levelMinMb = range[0]; levelMaxMb = range[1];
            }
            if (gainsDb == null || gainsDb.length != bandCount) {
                float[] cur = loadGains();
                gainsDb = cur != null && cur.length == bandCount
                        ? cur : EqPresets.gainsDb(presetName, bandCount);
            }
        } catch (Exception e) {
            equalizer = null;
        }
        try { bassBoost = new BassBoost(0, sessionId); } catch (Exception e) { bassBoost = null; }
        try { virtualizer = new Virtualizer(0, sessionId); } catch (Exception e) { virtualizer = null; }
        try { reverb = new EnvironmentalReverb(0, 0); } catch (Exception e) { reverb = null; }
        applyAll();
    }

    public synchronized void release() {
        releaseEffects();
        sessionId = -1;
    }

    private void releaseEffects() {
        try { if (equalizer != null) equalizer.release(); } catch (Exception ignored) {}
        try { if (proEq != null) proEq.release(); } catch (Exception ignored) {}
        try { if (bassBoost != null) bassBoost.release(); } catch (Exception ignored) {}
        try { if (virtualizer != null) virtualizer.release(); } catch (Exception ignored) {}
        try { if (reverb != null) reverb.release(); } catch (Exception ignored) {}
        equalizer = null; proEq = null; bassBoost = null; virtualizer = null; reverb = null;
        proLive = false;
        clearAux();
    }

    /* ------------------------------------------------------------ apply */

    private void applyAll() {
        if (sessionId <= 0) return;
        if (!masterOn) {
            try { if (equalizer != null) equalizer.setEnabled(false); } catch (Exception ignored) {}
            try { if (proEq != null) proEq.setEnabled(false); } catch (Exception ignored) {}
            try { if (bassBoost != null) bassBoost.setEnabled(false); } catch (Exception ignored) {}
            try { if (virtualizer != null) virtualizer.setEnabled(false); } catch (Exception ignored) {}
            clearAux();
            return;
        }
        proLive = false;
        if (proRequested && isProAvailable()) {
            proLive = buildProEq();
        }
        if (proLive) {
            try { if (equalizer != null) equalizer.setEnabled(false); } catch (Exception ignored) {}
        } else if (equalizer != null) {
            applyToEqualizer();
        }
        try {
            if (bassBoost != null) {
                bassBoost.setStrength((short) bassStrength);
                bassBoost.setEnabled(bassStrength > 0);
            }
        } catch (Exception ignored) {}
        try {
            if (virtualizer != null) {
                virtualizer.setStrength((short) virtStrength);
                virtualizer.setEnabled(virtStrength > 0);
            }
        } catch (Exception ignored) {}
        applyReverb();
    }

    private void applyToEqualizer() {
        if (equalizer == null) return;
        try {
            // A named device preset wins when the platform offers one with
            // the same name — the sliders then read back its real levels.
            if (EqPresets.isKnown(presetName)) {
                short n = equalizer.getNumberOfPresets();
                for (short i = 0; i < n; i++) {
                    if (presetName.equalsIgnoreCase(equalizer.getPresetName(i))) {
                        equalizer.usePreset(i);
                        equalizer.setEnabled(true);
                        for (int b = 0; b < bandCount; b++) {
                            gainsDb[b] = equalizer.getBandLevel((short) b) / 100f;
                        }
                        return;
                    }
                }
            }
            for (int b = 0; b < bandCount; b++) {
                short mb = (short) Math.max(levelMinMb,
                        Math.min(levelMaxMb, Math.round(gainsDb[b] * 100f)));
                equalizer.setBandLevel((short) b, mb);
            }
            equalizer.setEnabled(true);
        } catch (Exception ignored) {}
    }

    private boolean buildProEq() {
        if (Build.VERSION.SDK_INT < 28 || sessionId <= 0) return false;
        try {
            if (proEq != null) { proEq.release(); proEq = null; }
            int bands = bandCount;
            DynamicsProcessing.Eq preEq = new DynamicsProcessing.Eq(true, false, bands);
            DynamicsProcessing.Mbc mbc = new DynamicsProcessing.Mbc(true, false, bands);
            DynamicsProcessing.Eq postEq = new DynamicsProcessing.Eq(true, false, bands);
            for (int i = 0; i < bands; i++) {
                float cutoff = Math.max(20f, centerFreqHz[i]);
                preEq.setBand(i, new DynamicsProcessing.EqBand(true, cutoff, gainsDb[i]));
                // Gentle multiband compression: slow, low ratio, no gain makeup.
                mbc.setBand(i, new DynamicsProcessing.MbcBand(true, cutoff,
                        10f, 200f, 1.5f, -24f, 6f, -60f, 1f, 0f, 0f));
                postEq.setBand(i, new DynamicsProcessing.EqBand(true, cutoff, 0f));
            }
            DynamicsProcessing.Limiter limiter = new DynamicsProcessing.Limiter(
                    true, false, 0, 1f, 60f, 20f, -2f, 0f);
            DynamicsProcessing.Config config = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, true, bands, true, bands, true, bands, true)
                    .setPreEqAllChannelsTo(preEq)
                    .setMbcAllChannelsTo(mbc)
                    .setPostEqAllChannelsTo(postEq)
                    .setLimiterAllChannelsTo(limiter)
                    .build();
            proEq = new DynamicsProcessing(0, sessionId, config);
            proEq.setEnabled(true);
            return true;
        } catch (Exception e) {
            try { if (proEq != null) proEq.release(); } catch (Exception ignored) {}
            proEq = null;
            return false;
        }
    }

    private void pushGainsToLiveEngine() {
        if (!masterOn || sessionId <= 0) return;
        if (proLive && proEq != null && Build.VERSION.SDK_INT >= 28) {
            try {
                for (int i = 0; i < bandCount; i++) {
                    proEq.setPreEqBandAllChannelsTo(i, new DynamicsProcessing.EqBand(
                            true, Math.max(20f, centerFreqHz[i]), gainsDb[i]));
                }
                return;
            } catch (Exception e) {
                // Fall through to a full re-apply (may drop to Equalizer).
            }
        }
        applyAll();
    }

    private void applyReverb() {
        ExoPlayer player = PlaybackService.sharedPlayer();
        if (reverb == null || player == null) return;
        try {
            if (reverbIndex <= 0 || reverbIndex >= REVERB_NAMES.length) { clearAux(); return; }
            int[] p = REVERB_PARAMS[reverbIndex];
            // Each setter is individually guarded: some devices reject
            // individual parameters while accepting the rest.
            try { reverb.setRoomLevel((short) p[0]); } catch (Exception ignored) {}
            try { reverb.setRoomHFLevel((short) p[1]); } catch (Exception ignored) {}
            try { reverb.setDecayTime(p[2]); } catch (Exception ignored) {}
            try { reverb.setDecayHFRatio((short) p[3]); } catch (Exception ignored) {}
            try { reverb.setReflectionsLevel((short) p[4]); } catch (Exception ignored) {}
            try { reverb.setReflectionsDelay(p[5]); } catch (Exception ignored) {}
            try { reverb.setReverbLevel((short) p[6]); } catch (Exception ignored) {}
            try { reverb.setReverbDelay(p[7]); } catch (Exception ignored) {}
            try { reverb.setDiffusion((short) p[8]); } catch (Exception ignored) {}
            try { reverb.setDensity((short) p[9]); } catch (Exception ignored) {}
            reverb.setEnabled(true);
            player.setAuxEffectInfo(new AuxEffectInfo(reverb.getId(), 1.0f));
        } catch (Exception ignored) {}
    }

    private void clearAux() {
        ExoPlayer player = PlaybackService.sharedPlayer();
        if (player == null) return;
        try { player.clearAuxEffectInfo(); } catch (Exception ignored) {}
    }

    /* ------------------------------------------------------------ changes (persisted) */

    public void setMasterOn(boolean on) {
        masterOn = on;
        prefs.edit().putBoolean("eq_on", on).apply();
        applyAll();
    }

    public void setProRequested(boolean pro) {
        proRequested = pro;
        prefs.edit().putBoolean("eq_pro", pro).apply();
        applyAll();
    }

    public void applyPreset(String name) {
        presetName = name;
        gainsDb = EqPresets.gainsDb(name, bandCount);
        prefs.edit().putString("eq_preset", name).apply();
        saveGains();
        applyAll();
    }

    /** One slider moved: the preset becomes Custom and gains persist. */
    public void setGainDb(int band, float db) {
        if (gainsDb == null || band < 0 || band >= gainsDb.length) return;
        gainsDb[band] = db;
        presetName = EqPresets.CUSTOM;
        prefs.edit().putString("eq_preset", EqPresets.CUSTOM).apply();
        saveGains();
        pushGainsToLiveEngine();
    }

    public void setBassStrength(int v) {
        bassStrength = Math.max(0, Math.min(1000, v));
        prefs.edit().putInt("bass", bassStrength).apply();
        applyAll();
    }

    public void setVirtStrength(int v) {
        virtStrength = Math.max(0, Math.min(1000, v));
        prefs.edit().putInt("virt", virtStrength).apply();
        applyAll();
    }

    public void setReverbIndex(int idx) {
        reverbIndex = Math.max(0, Math.min(REVERB_NAMES.length - 1, idx));
        prefs.edit().putInt("reverb", reverbIndex).apply();
        applyAll();
    }

    /* ------------------------------------------------------------ user presets */

    public Map<String, float[]> userPresets() {
        Map<String, float[]> out = new LinkedHashMap<>();
        try {
            JSONObject o = new JSONObject(prefs.getString("eq_user_presets", "{}"));
            JSONArray names = o.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String name = names.getString(i);
                    JSONArray arr = o.getJSONArray(name);
                    float[] g = new float[arr.length()];
                    for (int b = 0; b < g.length; b++) g[b] = (float) arr.getDouble(b);
                    out.put(name, g);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    public void saveUserPreset(String name) {
        if (name == null || name.trim().isEmpty() || gainsDb == null) return;
        try {
            JSONObject o = new JSONObject(prefs.getString("eq_user_presets", "{}"));
            JSONArray arr = new JSONArray();
            for (float g : gainsDb) arr.put(Math.round(g * 10) / 10.0);
            o.put(name.trim(), arr);
            prefs.edit().putString("eq_user_presets", o.toString()).apply();
        } catch (Exception ignored) {}
    }

    public void applyUserPreset(String name) {
        float[] g = userPresets().get(name);
        if (g == null) return;
        if (g.length != bandCount) {
            // Resample the saved curve onto the current band count.
            float[] rs = new float[bandCount];
            for (int i = 0; i < bandCount; i++) {
                double pos = bandCount == 1 ? 0 : (double) i / (bandCount - 1) * (g.length - 1);
                int lo = (int) Math.floor(pos);
                int hi = Math.min(g.length - 1, lo + 1);
                rs[i] = (float) (g[lo] * (1 - (pos - lo)) + g[hi] * (pos - lo));
            }
            g = rs;
        }
        gainsDb = g;
        presetName = name;
        prefs.edit().putString("eq_preset", name).apply();
        saveGains();
        applyAll();
    }

    private float[] loadGains() {
        try {
            JSONArray arr = new JSONArray(prefs.getString("eq_gains", "[]"));
            if (arr.length() == 0) return null;
            float[] g = new float[arr.length()];
            for (int i = 0; i < g.length; i++) g[i] = (float) arr.getDouble(i);
            return g;
        } catch (Exception e) { return null; }
    }

    private void saveGains() {
        try {
            JSONArray arr = new JSONArray();
            if (gainsDb != null) for (float g : gainsDb) arr.put(Math.round(g * 10) / 10.0);
            prefs.edit().putString("eq_gains", arr.toString()).apply();
        } catch (Exception ignored) {}
    }
}
