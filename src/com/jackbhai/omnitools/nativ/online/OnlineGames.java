package com.jackbhai.omnitools.nativ.online;

import android.content.Context;
import android.content.SharedPreferences;

import com.jackbhai.omnitools.nativ.Net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Online Games (GamePix) — config, catalogue model, feed fetch + cache,
 * favourites / recently-played storage, and the offline fallback list.
 *
 * GamePix is an official publisher distribution program whose docs cover
 * exactly this use: games loaded directly in a mobile-app WebView, with a
 * public JSON catalogue feed. See omnitools-native-onlinegames-spec.md.
 *
 * Endpoint hosts are stored Base64-encoded and decoded at runtime via
 * Net.ep — the same light obfuscation the rest of the app uses, so no
 * third-party host appears as a plain string in the dex. Every feed and
 * embed URL is built from the single SID constant below.
 */
public final class OnlineGames {

    private OnlineGames() {}

    /**
     * GamePix publisher id ("sid"). This is the documentation's example
     * placeholder; swapping in the registered dashboard sid is a one-line
     * change here — feed and embed URLs are all templated on it.
     */
    public static final String SID = "1";

    /* Obfuscated endpoints (decoded with Net.ep at runtime):
     *   FEED  = the v2 JSON catalogue feed base
     *   EMBED = the player host base; a game lives at EMBED + namespace
     *           + "/embed?sid=" + SID */
    private static final String FEED_B64 =
            "aHR0cHM6Ly9mZWVkcy5nYW1lcGl4LmNvbS92Mi9qc29u";
    private static final String EMBED_B64 =
            "aHR0cHM6Ly9wbGF5LmdhbWVwaXguY29tLw==";

    private static final int FEED_TIMEOUT_MS = 15000;
    private static final long CACHE_TTL_MS = 24L * 60L * 60L * 1000L;
    private static final int RECENT_MAX = 12;

    private static final String PREFS = "omnitools_native";
    private static final String KEY_CACHE = "online_games_cache_v1";
    private static final String KEY_CACHE_TS = "online_games_cache_ts_v1";
    private static final String KEY_FAVS = "online_games_favs_v1";
    private static final String KEY_RECENT = "online_games_recent_v1";
    private static final String KEY_NOTICE = "online_games_notice_seen_v1";

    /** Category pulls merged on top of the page-1 quality feed. */
    private static final String[] CATEGORY_PULLS = {"runner", "racing", "io", "puzzle"};

    // ---------------------------------------------------------------- URLs

    public static String feedUrl(String category, int page, int perPage) {
        StringBuilder u = new StringBuilder(Net.ep(FEED_B64));
        u.append("?sid=").append(SID).append("&order=quality");
        if (category != null && !category.isEmpty()) {
            u.append("&category=").append(category);
        }
        u.append("&pagination=").append(perPage).append("&page=").append(page);
        return u.toString();
    }

    /** The documented WebView pattern: the embed page loaded top-level. */
    public static String embedUrl(String namespace) {
        return Net.ep(EMBED_B64) + namespace + "/embed?sid=" + SID;
    }

    // ---------------------------------------------------------------- model

    public static final class Game {
        public final String namespace;
        public final String title;
        public final String category;
        public final String orientation; // "landscape" | "portrait" | "all" | ""
        public final String image;       // icon (square)
        public final String banner;      // cover art (wide), may equal image

        public Game(String namespace, String title, String category,
                    String orientation, String image, String banner) {
            this.namespace = namespace == null ? "" : namespace;
            this.title = title == null ? "" : title;
            this.category = category == null ? "" : category;
            this.orientation = orientation == null ? "" : orientation;
            this.image = image == null ? "" : image;
            this.banner = banner == null ? "" : banner;
        }
    }

    // ------------------------------------------------------------- fallback

    /* The 20 embed-verified games from the spec (section 3.1): namespace,
     * title, category, orientation. Shown whenever the feed and the cache
     * are both unavailable (e.g. first run with no connection). */
    private static final String[][] FALLBACK = {
        {"moto-x3m-spooky-land", "Moto X3M: Spooky Land", "racing", "landscape"},
        {"slope-racing-3d", "Slope Racing 3D", "runner", "landscape"},
        {"worm-hunt-snake-game-io-zone", "Worm Hunt \u2014 Snake iO", "io", "landscape"},
        {"endless-car-chase", "Endless Car Chase", "racing", "landscape"},
        {"mr-racer-car-racing-game", "MR RACER", "racing", "landscape"},
        {"spider-evolution-runner-game", "Spider Evolution Runner", "runner", "landscape"},
        {"runner", "Runner", "runner", "landscape"},
        {"flip-trickster-parkour-simulator", "Flip Trickster Parkour", "sports", "landscape"},
        {"yenemy-io", "Yenemy", "io", "landscape"},
        {"motocross-racing", "Motocross Racing", "racing", "landscape"},
        {"subway-princess-run-1", "Subway Princess Run", "runner", "portrait"},
        {"count-masters-1", "Count Masters", "runner", "portrait"},
        {"number-master-run-and-merge", "Number Master: Run and Merge", "runner", "portrait"},
        {"diep-io", "Diep.io", "io", "landscape"},
        {"archers-io-1", "Archers.io", "io", "landscape"},
        {"stickman-io", "Stickman.io", "io", "landscape"},
        {"jump-rally", "Jump Rally", "racing", "landscape"},
        {"ramp-racing", "Ramp Racing", "racing", "landscape"},
        {"long-neck-run-1", "Long Neck Run", "runner", "portrait"},
        {"layer-man-3d-run-collect", "Layer Man 3D", "runner", "portrait"},
    };

    public static List<Game> fallback() {
        List<Game> out = new ArrayList<>();
        for (String[] r : FALLBACK) out.add(new Game(r[0], r[1], r[2], r[3], "", ""));
        return out;
    }

    // ----------------------------------------------------------------- feed

    /** Parse one feed document (JSON Feed shape: a root object whose
     *  "items" array carries the games). Dedupes by namespace. */
    public static List<Game> parseFeed(String json) throws Exception {
        List<Game> out = new ArrayList<>();
        JSONArray items;
        String t = json == null ? "" : json.trim();
        if (t.startsWith("[")) {
            items = new JSONArray(t);
        } else {
            items = new JSONObject(t).optJSONArray("items");
            if (items == null) return out;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) continue;
            String ns = o.optString("namespace", "").trim();
            String title = Net.clean(o.optString("title", ""));
            if (ns.isEmpty() || title.isEmpty() || !seen.add(ns)) continue;
            String banner = Net.opt(o, "banner_image", "image");
            String image = Net.opt(o, "image", "banner_image");
            out.add(new Game(ns, title,
                    o.optString("category", "").trim().toLowerCase(java.util.Locale.US),
                    o.optString("orientation", "").trim().toLowerCase(java.util.Locale.US),
                    image, banner));
        }
        return out;
    }

    /**
     * Blocking fetch (call off the UI thread): page 1 of the quality feed
     * plus one page of each category pull, merged and deduped by
     * namespace. On success the merged catalogue is written to the cache.
     * Throws if the main feed yields nothing, so the caller can fall back.
     */
    public static List<Game> fetchFresh(Context ctx) throws Exception {
        LinkedHashMap<String, Game> merged = new LinkedHashMap<>();
        List<Game> main = parseFeed(Net.get(feedUrl(null, 1, 96), FEED_TIMEOUT_MS));
        for (Game g : main) merged.put(g.namespace, g);
        if (merged.isEmpty()) throw new Exception("empty feed");
        for (String cat : CATEGORY_PULLS) {
            try {
                List<Game> part = parseFeed(Net.get(feedUrl(cat, 1, 48), FEED_TIMEOUT_MS));
                for (Game g : part) {
                    if (!merged.containsKey(g.namespace)) merged.put(g.namespace, g);
                }
            } catch (Exception ignored) { /* one bad category pull is not fatal */ }
        }
        List<Game> out = new ArrayList<>(merged.values());
        saveCache(ctx, out);
        return out;
    }

    // ---------------------------------------------------------------- cache

    private static void saveCache(Context ctx, List<Game> games) {
        try {
            JSONArray arr = new JSONArray();
            for (Game g : games) {
                JSONObject o = new JSONObject();
                o.put("namespace", g.namespace);
                o.put("title", g.title);
                o.put("category", g.category);
                o.put("orientation", g.orientation);
                o.put("image", g.image);
                o.put("banner_image", g.banner);
                arr.put(o);
            }
            prefs(ctx).edit()
                    .putString(KEY_CACHE, arr.toString())
                    .putLong(KEY_CACHE_TS, System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) { /* cache is best-effort */ }
    }

    /** The cached catalogue, or an empty list when there is none. */
    public static List<Game> loadCached(Context ctx) {
        try {
            String raw = prefs(ctx).getString(KEY_CACHE, "");
            if (raw == null || raw.isEmpty()) return new ArrayList<>();
            JSONArray arr = new JSONArray(raw);
            List<Game> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String ns = o.optString("namespace", "").trim();
                String title = o.optString("title", "").trim();
                if (ns.isEmpty() || title.isEmpty() || !seen.add(ns)) continue;
                out.add(new Game(ns, title, o.optString("category", ""),
                        o.optString("orientation", ""), o.optString("image", ""),
                        o.optString("banner_image", "")));
            }
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static boolean cacheFresh(Context ctx) {
        long ts = prefs(ctx).getLong(KEY_CACHE_TS, 0);
        return ts > 0 && System.currentTimeMillis() - ts < CACHE_TTL_MS;
    }

    // ------------------------------------------------- favourites / recent

    public static Set<String> favourites(Context ctx) {
        Set<String> s = prefs(ctx).getStringSet(KEY_FAVS, null);
        return s == null ? new HashSet<String>() : new HashSet<>(s);
    }

    public static boolean isFavourite(Context ctx, String namespace) {
        return favourites(ctx).contains(namespace);
    }

    public static void setFavourite(Context ctx, String namespace, boolean fav) {
        Set<String> s = favourites(ctx);
        if (fav) s.add(namespace); else s.remove(namespace);
        prefs(ctx).edit().putStringSet(KEY_FAVS, s).apply();
    }

    /** Recently played namespaces, most recent first (max 12). */
    public static List<String> recent(Context ctx) {
        List<String> out = new ArrayList<>();
        try {
            String raw = prefs(ctx).getString(KEY_RECENT, "");
            if (raw == null || raw.isEmpty()) return out;
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                String ns = arr.optString(i, "");
                if (!ns.isEmpty()) out.add(ns);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void recordRecent(Context ctx, String namespace) {
        if (namespace == null || namespace.isEmpty()) return;
        List<String> r = recent(ctx);
        r.remove(namespace);
        r.add(0, namespace);
        while (r.size() > RECENT_MAX) r.remove(r.size() - 1);
        try {
            JSONArray arr = new JSONArray();
            for (String ns : r) arr.put(ns);
            prefs(ctx).edit().putString(KEY_RECENT, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------ one-time

    public static boolean noticeSeen(Context ctx) {
        return prefs(ctx).getBoolean(KEY_NOTICE, false);
    }

    public static void markNoticeSeen(Context ctx) {
        prefs(ctx).edit().putBoolean(KEY_NOTICE, true).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
