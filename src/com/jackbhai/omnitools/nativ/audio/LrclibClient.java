package com.jackbhai.omnitools.nativ.audio;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * LRCLIB client (https://lrclib.net — no key, MIT service). Tries the
 * exact /api/get first (track + artist + duration), then /api/search and
 * picks the candidate whose duration is closest. Results are cached on
 * disk per track by the caller-facing helpers here, so a song's lyrics
 * are fetched at most once. Blocking — call from a worker thread.
 */
public final class LrclibClient {

    public static final String UA = "OmniTools Native";

    public static final class Result {
        public String synced;   // LRC text, may be null
        public String plain;    // plain lyrics, may be null
        public boolean found() { return synced != null || plain != null; }
    }

    private LrclibClient() {}

    public static Result fetch(String title, String artist, long durationSec) {
        Result r = new Result();
        try {
            String get = "https://lrclib.net/api/get?track_name=" + enc(title)
                    + "&artist_name=" + enc(artist == null ? "" : artist)
                    + (durationSec > 0 ? "&duration=" + durationSec : "");
            JSONObject o = getJson(get);
            if (o != null) fill(o, r);
            if (r.found()) return r;
        } catch (Exception ignored) {}
        try {
            String search = "https://lrclib.net/api/search?track_name=" + enc(title)
                    + "&artist_name=" + enc(artist == null ? "" : artist);
            String body = httpGet(search);
            if (body != null) {
                JSONArray arr = new JSONArray(body);
                JSONObject best = null;
                long bestDelta = Long.MAX_VALUE;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    long d = Math.abs(o.optLong("duration", 0) - durationSec);
                    boolean hasSync = o.optString("syncedLyrics", null) != null;
                    if (best == null || (hasSync && d < bestDelta)) {
                        best = o;
                        if (hasSync) bestDelta = d;
                    }
                }
                if (best != null) fill(best, r);
            }
        } catch (Exception ignored) {}
        return r;
    }

    private static void fill(JSONObject o, Result r) {
        String s = o.optString("syncedLyrics", null);
        String p = o.optString("plainLyrics", null);
        if (s != null && !s.trim().isEmpty()) r.synced = s;
        if (p != null && !p.trim().isEmpty()) r.plain = p;
    }

    /* ------------------------------------------------------------ disk cache */

    public static File cacheFile(File dir, String key) {
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, key.replaceAll("[^A-Za-z0-9_-]", "_") + ".json");
    }

    public static Result readCache(File dir, String key) {
        try {
            File f = cacheFile(dir, key);
            if (!f.exists()) return null;
            String body;
            try (InputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[(int) f.length()];
                int off = 0, n;
                while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
                body = new String(buf, 0, off, "UTF-8");
            }
            JSONObject o = new JSONObject(body);
            Result r = new Result();
            r.synced = o.optString("synced", null);
            r.plain = o.optString("plain", null);
            if ("null".equals(r.synced)) r.synced = null;
            if ("null".equals(r.plain)) r.plain = null;
            return r;
        } catch (Exception e) { return null; }
    }

    public static void writeCache(File dir, String key, Result r) {
        try {
            JSONObject o = new JSONObject();
            o.put("synced", r.synced == null ? JSONObject.NULL : r.synced);
            o.put("plain", r.plain == null ? JSONObject.NULL : r.plain);
            try (FileOutputStream fos = new FileOutputStream(cacheFile(dir, key))) {
                fos.write(o.toString().getBytes("UTF-8"));
            }
        } catch (Exception ignored) {}
    }

    /* ------------------------------------------------------------ http */

    private static String enc(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }

    private static JSONObject getJson(String url) throws Exception {
        String body = httpGet(url);
        return body == null ? null : new JSONObject(body);
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection cn = (HttpURLConnection) new URL(url).openConnection();
        cn.setConnectTimeout(10000);
        cn.setReadTimeout(15000);
        cn.setRequestProperty("User-Agent", UA);
        try {
            if (cn.getResponseCode() != 200) return null;
            try (InputStream in = cn.getInputStream()) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                return bos.toString("UTF-8");
            }
        } finally {
            cn.disconnect();
        }
    }
}
