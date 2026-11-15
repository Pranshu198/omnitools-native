import com.jackbhai.omnitools.nativ.backup.BackupCodec;
import com.jackbhai.omnitools.nativ.backup.PrefStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Pure-JVM tests for BackupCodec against a HashMap-backed PrefStore. */
public class BackupTestMain {

    private static int pass = 0, fail = 0;

    private static void check(String name, boolean cond) {
        if (cond) { pass++; System.out.println("PASS " + name); }
        else { fail++; System.out.println("FAIL " + name); }
    }

    /** HashMap-backed PrefStore with SharedPreferences-like defaults. */
    static final class MapStore implements PrefStore {
        final Map<String, Object> m = new HashMap<>();
        @Override public boolean contains(String k) { return m.containsKey(k); }
        @Override public String getString(String k, String d) { Object v = m.get(k); return v instanceof String ? (String) v : d; }
        @Override public boolean getBoolean(String k, boolean d) { Object v = m.get(k); return v instanceof Boolean ? (Boolean) v : d; }
        @Override public int getInt(String k, int d) { Object v = m.get(k); return v instanceof Number ? ((Number) v).intValue() : d; }
        @Override public float getFloat(String k, float d) { Object v = m.get(k); return v instanceof Number ? ((Number) v).floatValue() : d; }
        @Override public Set<String> keys() { return new HashSet<>(m.keySet()); }
        @Override public void putString(String k, String v) { m.put(k, v); }
        @Override public void putBoolean(String k, boolean v) { m.put(k, v); }
        @Override public void putInt(String k, int v) { m.put(k, v); }
        @Override public void putFloat(String k, float v) { m.put(k, v); }
    }

    private static boolean jsonEquals(Object a, Object b) throws Exception {
        if (a instanceof JSONObject && b instanceof JSONObject) {
            JSONObject x = (JSONObject) a, y = (JSONObject) b;
            if (x.length() != y.length()) return false;
            java.util.Iterator<String> it = x.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (!y.has(k) || !jsonEquals(x.get(k), y.get(k))) return false;
            }
            return true;
        }
        if (a instanceof JSONArray && b instanceof JSONArray) {
            JSONArray x = (JSONArray) a, y = (JSONArray) b;
            if (x.length() != y.length()) return false;
            for (int i = 0; i < x.length(); i++) if (!jsonEquals(x.get(i), y.get(i))) return false;
            return true;
        }
        if (a instanceof Number && b instanceof Number) {
            return Math.abs(((Number) a).doubleValue() - ((Number) b).doubleValue()) < 0.001;
        }
        return String.valueOf(a).equals(String.valueOf(b));
    }

    public static void main(String[] args) throws Exception {
        roundTrip();
        playlistsRoundTrip();
        corruptRejection();
        oldVersionAcceptance();
        partialDamage();
        System.out.println("RESULT pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }

    private static void seed(MapStore main, MapStore audio, MapStore widgets) throws Exception {
        JSONArray favs = new JSONArray();
        favs.put(new JSONObject().put("id", "abc").put("title", "Khaab")
                .put("artist", "Akhil").put("album", "").put("year", "2016")
                .put("durSec", 204).put("art", "https://x/a.jpg").put("stream", "https://x/s.mp4"));
        favs.put(new JSONObject().put("id", "").put("title", "Local Song")
                .put("artist", "Me").put("album", "Phone").put("year", "")
                .put("durSec", 0).put("art", "").put("stream", ""));
        main.putString("favourites", favs.toString());
        main.putBoolean("sfx_muted", true);

        audio.putBoolean("eq_on", true);
        audio.putBoolean("eq_pro", true);
        audio.putString("eq_preset", "MyPreset");
        audio.putString("eq_gains", "[3.0,2.0,0.0,-1.0,4.0]");
        audio.putString("eq_user_presets", "{\"MyPreset\":[3,2,0,-1,4],\"Soft\":[1,1,1,1,1]}");
        audio.putInt("bass", 400);
        audio.putInt("virt", 250);
        audio.putInt("reverb", 2);
        audio.putFloat("crossfade_sec", 3.5f);
        audio.putBoolean("skip_silence", true);
        audio.putBoolean("accent_dynamic", false);
        audio.putInt("accent_manual", -65536);
        audio.putInt("accent_dynamic_color", 123456);

        widgets.putInt("accent_7", -16711936);
        widgets.putInt("transp_7", 40);
        widgets.putBoolean("showart_7", false);
        widgets.putString("tools_7", "songs,games");
        widgets.putInt("accent_9", -16776961);
    }

    private static void roundTrip() throws Exception {
        MapStore main = new MapStore(), audio = new MapStore(), widgets = new MapStore();
        seed(main, audio, widgets);

        String file = BackupCodec.exportString(main, audio, widgets, "3.2-native.1");
        JSONObject parsed = new JSONObject(file);
        check("export kind", BackupCodec.KIND.equals(parsed.optString("kind")));
        check("export format", parsed.optInt("format") == BackupCodec.FORMAT_CURRENT);
        check("export playlists empty when none stored", parsed.optJSONArray("playlists") != null
                && parsed.optJSONArray("playlists").length() == 0);

        MapStore main2 = new MapStore(), audio2 = new MapStore(), widgets2 = new MapStore();
        BackupCodec.ImportResult r = BackupCodec.importJson(file, main2, audio2, widgets2);
        check("roundtrip status OK", r.status == BackupCodec.ImportResult.Status.OK);
        check("roundtrip counts", r.favourites == 2 && r.userPresets == 2 && r.widgets == 2);

        check("favourites equal", jsonEquals(
                new JSONArray(main2.getString("favourites", "[]")),
                new JSONArray(main.getString("favourites", "[]"))));
        check("sfx_muted equal", main2.getBoolean("sfx_muted", false));
        check("eq core equal", audio2.getBoolean("eq_on", false) && audio2.getBoolean("eq_pro", false)
                && "MyPreset".equals(audio2.getString("eq_preset", ""))
                && audio2.getInt("bass", 0) == 400 && audio2.getInt("virt", 0) == 250
                && audio2.getInt("reverb", 0) == 2);
        check("eq gains equal", jsonEquals(
                new JSONArray(audio2.getString("eq_gains", "[]")),
                new JSONArray(audio.getString("eq_gains", "[]"))));
        check("user presets equal", jsonEquals(
                new JSONObject(audio2.getString("eq_user_presets", "{}")),
                new JSONObject(audio.getString("eq_user_presets", "{}"))));
        check("audio settings equal", Math.abs(audio2.getFloat("crossfade_sec", 0) - 3.5f) < 0.001
                && audio2.getBoolean("skip_silence", false)
                && !audio2.getBoolean("accent_dynamic", true)
                && audio2.getInt("accent_manual", 0) == -65536
                && audio2.getInt("accent_dynamic_color", 0) == 123456);
        check("widget 7 equal", widgets2.getInt("accent_7", 0) == -16711936
                && widgets2.getInt("transp_7", 0) == 40
                && !widgets2.getBoolean("showart_7", true)
                && "songs,games".equals(widgets2.getString("tools_7", "")));
        check("widget 9 partial equal", widgets2.getInt("accent_9", 0) == -16776961
                && !widgets2.contains("transp_9"));
    }

    private static void playlistsRoundTrip() throws Exception {
        MapStore main = new MapStore(), audio = new MapStore(), widgets = new MapStore();
        JSONArray pls = new JSONArray();
        JSONArray songs = new JSONArray();
        songs.put(new JSONObject().put("id", "p1").put("title", "Pl Song")
                .put("artist", "Singer").put("stream", "https://x/p.mp4"));
        pls.put(new JSONObject().put("id", "pl1").put("name", "Road Trip").put("songs", songs));
        pls.put(new JSONObject().put("id", "pl2").put("name", "Empty One").put("songs", new JSONArray()));
        main.putString("playlists", pls.toString());

        String file = BackupCodec.exportString(main, audio, widgets, "3.3-native.1");
        JSONObject parsed = new JSONObject(file);
        check("playlists exported for real", parsed.optJSONArray("playlists") != null
                && parsed.optJSONArray("playlists").length() == 2);

        MapStore main2 = new MapStore(), audio2 = new MapStore(), widgets2 = new MapStore();
        BackupCodec.ImportResult r = BackupCodec.importJson(file, main2, audio2, widgets2);
        check("playlists roundtrip status OK",
                r.status == BackupCodec.ImportResult.Status.OK);
        check("playlists roundtrip count", r.playlists == 2);
        check("playlists restored equal", jsonEquals(
                new JSONArray(main2.getString("playlists", "[]")),
                new JSONArray(main.getString("playlists", "[]"))));
    }

    private static void corruptRejection() throws Exception {
        MapStore main = new MapStore(), audio = new MapStore(), widgets = new MapStore();
        main.putString("favourites", "SENTINEL");

        BackupCodec.ImportResult r1 = BackupCodec.importJson("this is not json {{{", main, audio, widgets);
        check("garbage -> CORRUPT", r1.status == BackupCodec.ImportResult.Status.CORRUPT);
        check("garbage -> untouched", "SENTINEL".equals(main.getString("favourites", "")));

        BackupCodec.ImportResult r2 = BackupCodec.importJson("", main, audio, widgets);
        check("empty -> CORRUPT", r2.status == BackupCodec.ImportResult.Status.CORRUPT);

        BackupCodec.ImportResult r3 = BackupCodec.importJson(
                "{\"kind\":\"someone-elses-backup\",\"format\":1,\"favourites\":[]}",
                main, audio, widgets);
        check("wrong kind -> NOT_A_BACKUP", r3.status == BackupCodec.ImportResult.Status.NOT_A_BACKUP);
        check("wrong kind -> untouched", "SENTINEL".equals(main.getString("favourites", "")));

        BackupCodec.ImportResult r4 = BackupCodec.importJson(
                "{\"kind\":\"omnitools-native-backup\",\"format\":99,\"favourites\":[]}",
                main, audio, widgets);
        check("future format -> UNSUPPORTED_VERSION",
                r4.status == BackupCodec.ImportResult.Status.UNSUPPORTED_VERSION);
        check("future format -> untouched", "SENTINEL".equals(main.getString("favourites", "")));

        BackupCodec.ImportResult r5 = BackupCodec.importJson("{\"hello\":\"world\"}", main, audio, widgets);
        check("no sections -> NOT_A_BACKUP", r5.status == BackupCodec.ImportResult.Status.NOT_A_BACKUP);
    }

    private static void oldVersionAcceptance() throws Exception {
        // Legacy file: no kind, no format field at all.
        String legacy = "{\"favourites\":[{\"id\":\"z\",\"title\":\"Old Song\",\"artist\":\"A\"}],"
                + "\"eq\":{\"preset\":\"Rock\",\"gains\":[5,3,-1,3,5],\"userPresets\":{}}}";
        MapStore main = new MapStore(), audio = new MapStore(), widgets = new MapStore();
        BackupCodec.ImportResult r = BackupCodec.importJson(legacy, main, audio, widgets);
        check("legacy accepted", r.applied());
        check("legacy favourites", new JSONArray(main.getString("favourites", "[]")).length() == 1);
        check("legacy eq preset", "Rock".equals(audio.getString("eq_preset", "")));
        check("legacy eq gains", jsonEquals(new JSONArray(audio.getString("eq_gains", "[]")),
                new JSONArray("[5,3,-1,3,5]")));
    }

    private static void partialDamage() throws Exception {
        String mixed = "{\"kind\":\"omnitools-native-backup\",\"format\":1,"
                + "\"favourites\":[{\"title\":\"Good\"},{\"artist\":\"No title\"},42],"
                + "\"playlists\":[{\"name\":\"Later\"}],"
                + "\"eq\":{\"userPresets\":{\"Bad\":\"nope\",\"Good\":[1,2,3]}}}";
        MapStore main = new MapStore(), audio = new MapStore(), widgets = new MapStore();
        BackupCodec.ImportResult r = BackupCodec.importJson(mixed, main, audio, widgets);
        check("mixed -> applied with warnings",
                r.status == BackupCodec.ImportResult.Status.OK_WITH_WARNINGS);
        check("mixed -> good favourite kept",
                new JSONArray(main.getString("favourites", "[]")).length() == 1);
        check("mixed -> good preset kept, bad dropped",
                new JSONObject(audio.getString("eq_user_presets", "{}")).has("Good")
                        && !new JSONObject(audio.getString("eq_user_presets", "{}")).has("Bad"));
        check("mixed -> playlists warning present",
                r.warnings.toString().contains("playlists"));
        check("mixed -> playlist without songs array skipped, none restored",
                r.playlists == 0
                        && new JSONArray(main.getString("playlists", "[]")).length() == 0);
    }
}
