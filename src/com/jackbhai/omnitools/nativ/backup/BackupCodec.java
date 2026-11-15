package com.jackbhai.omnitools.nativ.backup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

/**
 * Export / import of the user's OmniTools Native data as one JSON file.
 *
 * <p>Written against the app's REAL storage (verified in source — there is
 * no SQLite; everything is SharedPreferences):
 * <ul>
 *   <li>{@code omnitools_native}: {@code favourites} (a JSON-array STRING of
 *       Song objects, written by SongsSection), {@code sfx_muted}.</li>
 *   <li>{@code omnitools_audio}: {@code eq_on, eq_pro, eq_preset, bass, virt,
 *       reverb}, {@code eq_gains} (JSON-array string of dB values),
 *       {@code eq_user_presets} (JSON-object string name -&gt; gains array) —
 *       all from EqController; {@code crossfade_sec, skip_silence} from
 *       SongsSection; {@code accent_dynamic, accent_manual,
 *       accent_dynamic_color} from AccentTheme.</li>
 *   <li>{@code omni_widgets}: per-widget-id keys {@code accent_<id>} (int),
 *       {@code transp_<id>} (int), {@code showart_<id>} (boolean),
 *       {@code tools_<id>} (comma-separated string) from WidgetPrefs.</li>
 * </ul>
 *
 * <p>Playlists live in {@code omnitools_native} key {@code playlists}
 * (a JSON-array string of {id,name,songs[]} written by PlaylistStore)
 * and are exported / restored for real.
 *
 * <p>File shape:
 * <pre>{ "kind":"omnitools-native-backup", "format":1, "appVersion":"...",
 *   "exportedAt":123, "favourites":[...], "playlists":[],
 *   "eq":{...}, "audioSettings":{...}, "appSettings":{...},
 *   "widgets":{"12":{...}} }</pre>
 *
 * Pure Java + org.json (the app's JSON library) — JVM-tested.
 */
public final class BackupCodec {

    public static final String KIND = "omnitools-native-backup";
    public static final int FORMAT_CURRENT = 1;

    // Real prefs file names (must match the app).
    public static final String PREFS_MAIN = "omnitools_native";
    public static final String PREFS_AUDIO = "omnitools_audio";
    public static final String PREFS_WIDGETS = "omni_widgets";

    private static final int MAX_GAINS = 64;
    private static final int MAX_FAVOURITES = 10000;

    private BackupCodec() {}

    /* ------------------------------------------------------------ result */

    public static final class ImportResult {
        public enum Status { OK, OK_WITH_WARNINGS, NOT_A_BACKUP, CORRUPT, UNSUPPORTED_VERSION }

        public final Status status;
        public final int favourites;
        public final int playlists;
        public final int userPresets;
        public final int widgets;
        public final List<String> warnings;

        ImportResult(Status s, int favs, int presets, int widgets, List<String> w) {
            this(s, favs, 0, presets, widgets, w);
        }

        ImportResult(Status s, int favs, int playlists, int presets, int widgets,
                     List<String> w) {
            this.status = s;
            this.favourites = favs;
            this.playlists = playlists;
            this.userPresets = presets;
            this.widgets = widgets;
            this.warnings = Collections.unmodifiableList(w);
        }

        static ImportResult rejected(Status s, String why) {
            List<String> w = new ArrayList<>();
            w.add(why);
            return new ImportResult(s, 0, 0, 0, w);
        }

        public boolean applied() {
            return status == Status.OK || status == Status.OK_WITH_WARNINGS;
        }
    }

    /* ------------------------------------------------------------ export */

    public static JSONObject exportJson(PrefStore main, PrefStore audio,
                                        PrefStore widgets, String appVersion) {
        JSONObject root = new JSONObject();
        try {
            root.put("kind", KIND);
            root.put("format", FORMAT_CURRENT);
            root.put("app", "OmniTools Native");
            root.put("appVersion", appVersion == null ? "" : appVersion);
            root.put("exportedAt", System.currentTimeMillis());

            root.put("favourites", parseArray(main.getString("favourites", "[]")));
            root.put("playlists", exportPlaylists(main.getString("playlists", "[]")));

            JSONObject eq = new JSONObject();
            eq.put("on", audio.getBoolean("eq_on", true));
            eq.put("pro", audio.getBoolean("eq_pro", false));
            eq.put("preset", audio.getString("eq_preset", "Flat"));
            eq.put("gains", parseArray(audio.getString("eq_gains", "[]")));
            eq.put("bass", audio.getInt("bass", 0));
            eq.put("virt", audio.getInt("virt", 0));
            eq.put("reverb", audio.getInt("reverb", 0));
            eq.put("userPresets", parseObject(audio.getString("eq_user_presets", "{}")));
            root.put("eq", eq);

            JSONObject as = new JSONObject();
            as.put("crossfadeSec", (double) audio.getFloat("crossfade_sec", 0f));
            as.put("skipSilence", audio.getBoolean("skip_silence", false));
            as.put("accentDynamic", audio.getBoolean("accent_dynamic", true));
            as.put("accentManual", audio.getInt("accent_manual", 0));
            as.put("accentDynamicColor", audio.getInt("accent_dynamic_color", 0));
            root.put("audioSettings", as);

            JSONObject app = new JSONObject();
            app.put("sfxMuted", main.getBoolean("sfx_muted", false));
            root.put("appSettings", app);

            root.put("widgets", exportWidgets(widgets));
        } catch (Exception ignored) {
            // org.json putters on a fresh object do not throw in practice;
            // if one ever did, returning the partial root beats crashing.
        }
        return root;
    }

    public static String exportString(PrefStore main, PrefStore audio,
                                      PrefStore widgets, String appVersion) {
        return exportJson(main, audio, widgets, appVersion).toString();
    }

    private static JSONObject exportWidgets(PrefStore widgets) {
        JSONObject out = new JSONObject();
        // Group the dynamic keys (accent_12, transp_12, ...) by widget id.
        TreeSet<String> ids = new TreeSet<>();
        for (String k : widgets.keys()) {
            String id = widgetIdOf(k);
            if (id != null) ids.add(id);
        }
        for (String id : ids) {
            try {
                JSONObject w = new JSONObject();
                if (widgets.contains("accent_" + id)) w.put("accent", widgets.getInt("accent_" + id, 0));
                if (widgets.contains("transp_" + id)) w.put("transparency", widgets.getInt("transp_" + id, 0));
                if (widgets.contains("showart_" + id)) w.put("showArt", widgets.getBoolean("showart_" + id, true));
                if (widgets.contains("tools_" + id)) w.put("tools", widgets.getString("tools_" + id, ""));
                out.put(id, w);
            } catch (Exception ignored) {}
        }
        return out;
    }

    /** Widget id for a prefs key, or null if the key is not a widget key. */
    private static String widgetIdOf(String key) {
        for (String p : new String[]{"accent_", "transp_", "showart_", "tools_"}) {
            if (key.startsWith(p)) {
                String id = key.substring(p.length());
                if (id.matches("[0-9]+")) return id;
            }
        }
        return null;
    }

    /* ------------------------------------------------------------ import */

    /**
     * Validates the header BEFORE writing anything; a rejected file leaves
     * every store untouched. Individual bad entries inside a valid file are
     * skipped and reported as warnings rather than failing the whole import.
     */
    public static ImportResult importJson(String text, PrefStore main,
                                          PrefStore audio, PrefStore widgets) {
        if (text == null || text.trim().isEmpty()) {
            return ImportResult.rejected(ImportResult.Status.CORRUPT, "File is empty.");
        }
        JSONObject root;
        try {
            root = new JSONObject(text);
        } catch (Exception e) {
            return ImportResult.rejected(ImportResult.Status.CORRUPT, "File is not valid JSON.");
        }

        String kind = root.optString("kind", "");
        if (!kind.isEmpty() && !KIND.equals(kind)) {
            return ImportResult.rejected(ImportResult.Status.NOT_A_BACKUP,
                    "This is not an OmniTools Native backup.");
        }
        // Files without a format field are legacy (format 0) and accepted;
        // anything newer than we understand is refused, not half-applied.
        int format = root.has("format") ? root.optInt("format", -1) : 0;
        if (format < 0) {
            return ImportResult.rejected(ImportResult.Status.CORRUPT, "Bad format field.");
        }
        if (format > FORMAT_CURRENT) {
            return ImportResult.rejected(ImportResult.Status.UNSUPPORTED_VERSION,
                    "Backup format " + format + " needs a newer app version.");
        }
        boolean anySection = root.has("favourites") || root.has("eq")
                || root.has("audioSettings") || root.has("appSettings")
                || root.has("widgets") || root.has("playlists");
        if (!anySection) {
            return ImportResult.rejected(ImportResult.Status.NOT_A_BACKUP,
                    "No recognisable backup sections found.");
        }

        List<String> warnings = new ArrayList<>();
        int favCount = 0, playlistCount = 0, presetCount = 0, widgetCount = 0;

        // ---- favourites: file holds a real array; prefs hold its string form.
        if (root.has("favourites")) {
            JSONArray arr = root.optJSONArray("favourites");
            if (arr == null) {
                warnings.add("favourites section ignored (not an array).");
            } else {
                JSONArray clean = new JSONArray();
                int skipped = 0;
                for (int i = 0; i < arr.length() && clean.length() < MAX_FAVOURITES; i++) {
                    JSONObject s = arr.optJSONObject(i);
                    if (s == null || s.optString("title", "").trim().isEmpty()) { skipped++; continue; }
                    clean.put(s);
                }
                if (skipped > 0) warnings.add(skipped + " invalid favourite(s) skipped.");
                main.putString("favourites", clean.toString());
                favCount = clean.length();
            }
        }

        // ---- playlists: real storage (prefs "playlists", a JSON-array
        // string of {id,name,songs[]} written by PlaylistStore). An entry
        // is valid only as an object with a non-empty name AND a songs
        // array; songs without a title are dropped like bad favourites.
        if (root.has("playlists")) {
            JSONArray arr = root.optJSONArray("playlists");
            if (arr == null) {
                warnings.add("playlists section ignored (not an array).");
            } else {
                JSONArray clean = new JSONArray();
                int skipped = 0, songsSkipped = 0;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject p = arr.optJSONObject(i);
                    if (p == null || p.optString("name", "").trim().isEmpty()
                            || p.optJSONArray("songs") == null) { skipped++; continue; }
                    JSONArray inSongs = p.optJSONArray("songs");
                    JSONArray songs = new JSONArray();
                    for (int j = 0; j < inSongs.length(); j++) {
                        JSONObject s = inSongs.optJSONObject(j);
                        if (s == null || s.optString("title", "").trim().isEmpty()) {
                            songsSkipped++; continue;
                        }
                        songs.put(s);
                    }
                    try {
                        JSONObject cp = new JSONObject();
                        cp.put("id", p.optString("id", ""));
                        cp.put("name", p.optString("name", ""));
                        cp.put("songs", songs);
                        clean.put(cp);
                    } catch (Exception ignored) {}
                }
                if (skipped > 0) warnings.add(skipped + " invalid entry(ies) in playlists skipped.");
                if (songsSkipped > 0) {
                    warnings.add(songsSkipped + " invalid song(s) skipped in playlists.");
                }
                main.putString("playlists", clean.toString());
                playlistCount = clean.length();
            }
        }

        // ---- EQ + user presets.
        if (root.has("eq")) {
            JSONObject eq = root.optJSONObject("eq");
            if (eq == null) {
                warnings.add("eq section ignored (not an object).");
            } else {
                if (eq.has("on")) audio.putBoolean("eq_on", eq.optBoolean("on", true));
                if (eq.has("pro")) audio.putBoolean("eq_pro", eq.optBoolean("pro", false));
                if (eq.has("preset")) audio.putString("eq_preset", eq.optString("preset", "Flat"));
                if (eq.has("bass")) audio.putInt("bass", eq.optInt("bass", 0));
                if (eq.has("virt")) audio.putInt("virt", eq.optInt("virt", 0));
                if (eq.has("reverb")) audio.putInt("reverb", eq.optInt("reverb", 0));
                if (eq.has("gains")) {
                    JSONArray g = cleanGains(eq.optJSONArray("gains"), warnings, "eq gains");
                    if (g != null) audio.putString("eq_gains", g.toString());
                }
                if (eq.has("userPresets")) {
                    JSONObject up = eq.optJSONObject("userPresets");
                    if (up == null) {
                        warnings.add("EQ user presets ignored (not an object).");
                    } else {
                        JSONObject clean = new JSONObject();
                        Iterator<String> it = up.keys();
                        while (it.hasNext()) {
                            String name = it.next();
                            JSONArray g = cleanGains(up.optJSONArray(name), warnings,
                                    "preset '" + name + "'");
                            if (g != null && g.length() > 0) {
                                try { clean.put(name, g); presetCount++; }
                                catch (Exception ignored) {}
                            }
                        }
                        audio.putString("eq_user_presets", clean.toString());
                    }
                }
            }
        }

        // ---- audio / accent settings.
        if (root.has("audioSettings")) {
            JSONObject as = root.optJSONObject("audioSettings");
            if (as == null) {
                warnings.add("audioSettings section ignored (not an object).");
            } else {
                if (as.has("crossfadeSec")) audio.putFloat("crossfade_sec", (float) as.optDouble("crossfadeSec", 0));
                if (as.has("skipSilence")) audio.putBoolean("skip_silence", as.optBoolean("skipSilence", false));
                if (as.has("accentDynamic")) audio.putBoolean("accent_dynamic", as.optBoolean("accentDynamic", true));
                if (as.has("accentManual")) audio.putInt("accent_manual", as.optInt("accentManual", 0));
                if (as.has("accentDynamicColor")) audio.putInt("accent_dynamic_color", as.optInt("accentDynamicColor", 0));
            }
        }

        // ---- app settings.
        if (root.has("appSettings")) {
            JSONObject app = root.optJSONObject("appSettings");
            if (app == null) {
                warnings.add("appSettings section ignored (not an object).");
            } else if (app.has("sfxMuted")) {
                main.putBoolean("sfx_muted", app.optBoolean("sfxMuted", false));
            }
        }

        // ---- widget settings (dynamic per-id keys).
        if (root.has("widgets")) {
            JSONObject wobj = root.optJSONObject("widgets");
            if (wobj == null) {
                warnings.add("widgets section ignored (not an object).");
            } else {
                Iterator<String> it = wobj.keys();
                while (it.hasNext()) {
                    String id = it.next();
                    if (!id.matches("[0-9]+")) {
                        warnings.add("widget entry '" + id + "' skipped (bad id).");
                        continue;
                    }
                    JSONObject w = wobj.optJSONObject(id);
                    if (w == null) continue;
                    if (w.has("accent")) widgets.putInt("accent_" + id, w.optInt("accent", 0));
                    if (w.has("transparency")) widgets.putInt("transp_" + id, w.optInt("transparency", 0));
                    if (w.has("showArt")) widgets.putBoolean("showart_" + id, w.optBoolean("showArt", true));
                    if (w.has("tools")) widgets.putString("tools_" + id, w.optString("tools", ""));
                    widgetCount++;
                }
            }
        }

        ImportResult.Status st = warnings.isEmpty()
                ? ImportResult.Status.OK : ImportResult.Status.OK_WITH_WARNINGS;
        return new ImportResult(st, favCount, playlistCount, presetCount, widgetCount, warnings);
    }

    /* ------------------------------------------------------------ helpers */

    /** Numbers-only gains array (capped); null when the input is not an array. */
    private static JSONArray cleanGains(JSONArray in, List<String> warnings, String label) {
        if (in == null) {
            warnings.add(label + " ignored (not an array).");
            return null;
        }
        JSONArray out = new JSONArray();
        for (int i = 0; i < in.length() && out.length() < MAX_GAINS; i++) {
            Object v = in.opt(i);
            if (v instanceof Number) {
                try { out.put(Math.round(((Number) v).doubleValue() * 10) / 10.0); }
                catch (Exception ignored) {}
            }
        }
        if (out.length() != in.length()) {
            warnings.add(label + ": kept " + out.length() + " of " + in.length() + " values.");
        }
        return out;
    }

    /** Export playlists: parse the stored array string, dropping entries
     *  that lack a name (light validation; songs are carried as stored). */
    private static JSONArray exportPlaylists(String stored) {
        JSONArray in = parseArray(stored);
        JSONArray out = new JSONArray();
        for (int i = 0; i < in.length(); i++) {
            JSONObject p = in.optJSONObject(i);
            if (p == null || p.optString("name", "").trim().isEmpty()) continue;
            out.put(p);
        }
        return out;
    }

    private static JSONArray parseArray(String s) {
        try { return new JSONArray(s == null ? "[]" : s); }
        catch (Exception e) { return new JSONArray(); }
    }

    private static JSONObject parseObject(String s) {
        try { return new JSONObject(s == null ? "{}" : s); }
        catch (Exception e) { return new JSONObject(); }
    }
}
