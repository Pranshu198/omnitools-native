package com.jackbhai.omnitools.nativ;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;

/**
 * Song search against the same catalogue the PWA uses (src/core/saavn.js),
 * with the same two independent routes:
 *
 *  1. Community mirrors that return ready-made stream links (no decryption).
 *     They are raced in waves of four, first playable answer wins — the same
 *     shape as the PWA, for the same reason: one slow mirror must never hold
 *     up fifteen fast ones.
 *  2. The catalogue's own API, called DIRECTLY. A browser cannot do this
 *     (no CORS header), so the PWA needs a relay for it; a native app has no
 *     CORS, so the official route works first-class here, including the DES
 *     decryption of encrypted_media_url with the catalogue's long-published
 *     static key (javax.crypto has DES built in — the PWA has to hand-roll
 *     it in JS because Web Crypto dropped DES).
 *
 * Pure java.net + org.json + javax.crypto: runs unchanged in the JVM smoke
 * harness (Base64 is the one Android class used, and only on the official
 * route; the harness supplies its own decoder path via java.util.Base64
 * fallback below).
 */
public final class SaavnApi {

    private SaavnApi() {}

    /* Endpoint hosts are stored Base64-encoded and decoded at runtime via
     * Sec (native-preferred) — no sensitive host is a plain string in the
     * dex. This is the same light obfuscation the PWA uses for its
     * endpoint table (src/core/endpoints.js). */
    private static String api() {
        return Sec.decode("aHR0cHM6Ly93d3cuamlvc2Fhdm4uY29tL2FwaS5waHA=");
    }
    private static String relay() {
        return Sec.decode("aHR0cHM6Ly9vbW5pLXByb3h5Lm9tbmktamFja2JoYWkud29ya2Vycy5kZXY=");
    }

    private static final class Mirror {
        final String base, path, linkKey;
        Mirror(String encBase, String p, String k) { base = Sec.decode(encBase); path = p; linkKey = k; }
    }

    /* Ordered by the PWA's measured latency (2026-11-12 re-measure). */
    private static final Mirror[] MIRRORS = {
        new Mirror("aHR0cHM6Ly9zaG53YXpkZXYtamlvc2Fhdm4tYXBpaS52ZXJjZWwuYXBw", "/api/search/songs?query=", "url"),
        new Mirror("aHR0cHM6Ly9qaW8tc2Fhdm4tYXBpLWlvdGEudmVyY2VsLmFwcA==", "/api/search/songs?query=", "url"),
        new Mirror("aHR0cHM6Ly9zYWF2bngudmVyY2VsLmFwcA==", "/api/search/songs?query=", "url"),
        new Mirror("aHR0cHM6Ly9qaW9zYWF2bi1hcGktaW5zdGFuY2UtbXUudmVyY2VsLmFwcA==", "/api/search/songs?query=", "url"),
        new Mirror("aHR0cHM6Ly9qaW9zYWF2bi1hcGktbG92YXQudmVyY2VsLmFwcA==", "/api/search/songs?query=", "url"),
        new Mirror("aHR0cHM6Ly9qaW9zYWF2bi1hcGktc2V2ZW4teGkudmVyY2VsLmFwcA==", "/api/search/songs?query=", "url"),
        new Mirror("aHR0cHM6Ly9tdXNpYzQ1LWFwaS52ZXJjZWwuYXBw", "/api/search/songs?query=", "url"),
        new Mirror("aHR0cHM6Ly9qaW9zYWF2bi1hcGkuc2hhcm1hb2ZmaWNpYWwud29ya2Vycy5kZXY=", "/api/search/songs?query=", "url"),
        new Mirror("aHR0cHM6Ly9qaW9zYWF2bnNlYXJjaC52ZXJjZWwuYXBw", "/search/songs?query=", "link"),
        new Mirror("aHR0cHM6Ly9zYWF2bi1hcGktZWlnaHQudmVyY2VsLmFwcA==", "/api/search/songs?query=", "url"),
        new Mirror("aHR0cHM6Ly9qaW9zYWF2bi1hcGktdG1raC5vbnJlbmRlci5jb20=", "/api/search/songs?query=", "url"),
        new Mirror("aHR0cHM6Ly9zYWF2bmFwaS1jaGkudmVyY2VsLmFwcA==", "/api/search/songs?query=", "url"),
        new Mirror("aHR0cHM6Ly9zYWF2bi1hcGktbW9jaGEudmVyY2VsLmFwcA==", "/api/search/songs?query=", "url"),
        new Mirror("aHR0cHM6Ly9qaW8tc2Fhdm4tYXBpLXNpZ21hLnZlcmNlbC5hcHA=", "/search/songs?query=", "link"),
        new Mirror("aHR0cHM6Ly9qaW9zYWF2bi1hcGkuYW5tb2xtYWFuNTQ2OC53b3JrZXJzLmRldg==", "/api/search/songs?query=", "url"),
        new Mirror("aHR0cHM6Ly9zYWF2bmFwaS1wc2kudmVyY2VsLmFwcA==", "/api/search/songs?query=", "url"),
        new Mirror("aHR0cHM6Ly9zYWF2bi1hcGktc2FibGUudmVyY2VsLmFwcA==", "/api/search/songs?query=", "url"),
        new Mirror("aHR0cHM6Ly9zYWF2bi1hcGktbXVrZXNoLnZlcmNlbC5hcHA=", "/api/search/songs?query=", "url"),
        new Mirror("aHR0cHM6Ly9qaW8tc2Fhdm4tYXBpLW51LnZlcmNlbC5hcHA=", "/search/songs?query=", "link"),
        new Mirror("aHR0cHM6Ly9qaW9zYWF2bi1hcGktY29keWFuZGVyc2FuLnZlcmNlbC5hcHA=", "/search/songs?query=", "link"),
    };

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    /* ------------------------------------------------------------ mirrors */

    private static List<Song> mirrorRows(Object root) {
        List<Object> rows = new ArrayList<>();
        JSONArray arr = null;
        if (root instanceof JSONArray) {
            arr = (JSONArray) root;
        } else if (root instanceof JSONObject) {
            JSONObject o = (JSONObject) root;
            Object data = o.opt("data");
            if (data instanceof JSONArray) arr = (JSONArray) data;
            else if (data instanceof JSONObject) arr = ((JSONObject) data).optJSONArray("results");
            if (arr == null) arr = o.optJSONArray("results");
        }
        if (arr != null) for (int i = 0; i < arr.length(); i++) rows.add(arr.opt(i));
        List<Song> out = new ArrayList<>();
        for (Object r : rows) if (r instanceof JSONObject) {
            // linkKey is per-mirror in the PWA; both key spellings are tried
            // here, which is equivalent for every mirror in the list.
            Song s = shapeMirrorSong((JSONObject) r);
            if (s != null && !s.stream.isEmpty()) out.add(s);
        }
        return out;
    }

    private static Song shapeMirrorSong(JSONObject x) {
        try {
            Song s = new Song();
            String mediaUrl = x.optString("media_url", "");
            if (!mediaUrl.isEmpty()) {
                // Flask-fork family: official row shape, stream pre-decrypted.
                s.id = x.optString("id", "");
                s.title = Net.clean(Net.opt(x, "song", "title", "name"));
                s.artist = Net.clean(Net.opt(x, "primary_artists", "singers"));
                s.album = Net.clean(x.optString("album", ""));
                s.year = x.optString("year", "");
                s.durSec = x.optLong("duration", 0);
                s.art = x.optString("image", "");
                s.stream = mediaUrl.replaceAll("_\\d+\\.mp[34]$", "_320.mp4");
                return s.title.isEmpty() ? null : s;
            }
            JSONArray dl = x.optJSONArray("downloadUrl");
            String best = "";
            if (dl != null) {
                String[] want = {"320", "160", "96", "48"};
                for (String w : want) {
                    for (int i = 0; i < dl.length(); i++) {
                        JSONObject q = dl.optJSONObject(i);
                        if (q == null) continue;
                        if (q.optString("quality", "").startsWith(w)) {
                            best = Net.opt(q, "url", "link");
                            if (!best.isEmpty()) break;
                        }
                    }
                    if (!best.isEmpty()) break;
                }
                if (best.isEmpty() && dl.length() > 0) {
                    JSONObject last = dl.optJSONObject(dl.length() - 1);
                    if (last != null) best = Net.opt(last, "url", "link");
                }
            }
            if (best.isEmpty()) return null;
            s.id = x.optString("id", "");
            s.title = Net.clean(Net.opt(x, "name", "title", "song"));
            String artists = x.optString("primaryArtists", "");
            if (artists.isEmpty()) {
                JSONObject a = x.optJSONObject("artists");
                if (a != null) {
                    JSONArray prim = a.optJSONArray("primary");
                    if (prim != null) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < prim.length(); i++) {
                            JSONObject p = prim.optJSONObject(i);
                            if (p != null) {
                                if (sb.length() > 0) sb.append(", ");
                                sb.append(p.optString("name", ""));
                            }
                        }
                        artists = sb.toString();
                    }
                }
                if (artists.isEmpty() && x.opt("artists") instanceof String) {
                    artists = x.optString("artists", "");
                }
            }
            s.artist = Net.clean(artists);
            Object alb = x.opt("album");
            s.album = Net.clean(alb instanceof JSONObject
                    ? ((JSONObject) alb).optString("name", "")
                    : x.optString("album", ""));
            s.year = x.optString("year", "");
            s.durSec = x.optLong("duration", 0);
            Object img = x.opt("image");
            if (img instanceof JSONArray) {
                JSONArray ia = (JSONArray) img;
                if (ia.length() > 0) {
                    JSONObject last = ia.optJSONObject(ia.length() - 1);
                    if (last != null) s.art = Net.opt(last, "link", "url");
                }
            } else if (img instanceof String) {
                s.art = (String) img;
            }
            s.stream = best;
            return s.title.isEmpty() ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    /** Race the mirrors in waves of four; first wave with playable rows wins. */
    public static List<Song> searchMirrors(String query, int limit) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            for (int i = 0; i < MIRRORS.length; i += 4) {
                java.util.concurrent.ExecutorCompletionService<List<Song>> ecs =
                        new java.util.concurrent.ExecutorCompletionService<>(pool);
                int inWave = 0;
                for (int j = i; j < Math.min(i + 4, MIRRORS.length); j++) {
                    final Mirror m = MIRRORS[j];
                    Callable<List<Song>> task = () -> {
                        String url = m.base + m.path + enc(query) + "&limit=" + limit;
                        String body = Net.get(url, 5000);
                        Object root;
                        try { root = new JSONObject(body); }
                        catch (Exception e) { root = new JSONArray(body); }
                        List<Song> rows = mirrorRows(root);
                        if (rows.isEmpty()) throw new Exception("no playable rows");
                        return rows;
                    };
                    ecs.submit(task);
                    inWave++;
                }
                List<Song> winner = null;
                for (int k = 0; k < inWave && winner == null; k++) {
                    Future<List<Song>> f = ecs.poll(6, TimeUnit.SECONDS);
                    if (f == null) break; // wave budget spent
                    try {
                        List<Song> r = f.get();
                        if (r != null && !r.isEmpty()) winner = r;
                    } catch (Exception ignored) { /* this mirror failed; keep waiting */ }
                }
                if (winner != null) return winner;
            }
        } finally {
            pool.shutdownNow();
        }
        throw new Exception("every mirror failed");
    }

    /* ------------------------------------------------------------ official */

    /** Decrypt one base64 encrypted_media_url (DES-ECB, static published key). */
    static String decryptUrl(String b64) {
        if (b64 == null || b64.isEmpty()) return "";
        try {
            byte[] ct = java.util.Base64.getMimeDecoder().decode(b64);
            if (ct.length == 0 || ct.length % 8 != 0) return "";
            DESKeySpec spec = new DESKeySpec(Sec.decode("MzgzNDY1OTE=").getBytes(StandardCharsets.US_ASCII));
            SecretKey key = SecretKeyFactory.getInstance("DES").generateSecret(spec);
            Cipher c = Cipher.getInstance("DES/ECB/PKCS5Padding");
            c.init(Cipher.DECRYPT_MODE, key);
            String url = new String(c.doFinal(ct), StandardCharsets.UTF_8).trim();
            return url.startsWith("http") ? url : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static Song shapeOfficial(JSONObject x) {
        try {
            JSONObject mi = x.optJSONObject("more_info");
            if (mi == null) return null;
            String base = decryptUrl(mi.optString("encrypted_media_url", ""));
            if (base.isEmpty()) return null;
            Song s = new Song();
            s.id = x.optString("id", "");
            s.title = Net.clean(Net.opt(x, "title", "song"));
            String artists = mi.optString("primary_artists", "");
            if (artists.isEmpty()) {
                JSONObject am = mi.optJSONObject("artistMap");
                if (am != null) {
                    JSONArray prim = am.optJSONArray("primary_artists");
                    if (prim != null) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < prim.length(); i++) {
                            JSONObject p = prim.optJSONObject(i);
                            if (p != null) {
                                if (sb.length() > 0) sb.append(", ");
                                sb.append(p.optString("name", ""));
                            }
                        }
                        artists = sb.toString();
                    }
                }
            }
            if (artists.isEmpty()) artists = x.optString("subtitle", "");
            s.artist = Net.clean(artists);
            s.album = Net.clean(mi.optString("album", ""));
            s.year = x.optString("year", "");
            s.durSec = mi.optLong("duration", 0);
            s.art = x.optString("image", "").replace("150x150", "500x500");
            s.stream = base.replaceAll("_\\d+\\.mp[34]$", "_320.mp4");
            return s.title.isEmpty() ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    /** The catalogue's own API, direct. Native has no CORS, so no relay. */
    public static List<Song> searchOfficial(String query, int limit) throws Exception {
        String url = api() + "?_format=json&_marker=0&api_version=4&ctx=web6dot0"
                + "&__call=search.getResults&q=" + enc(query)
                + "&n=" + limit + "&p=1";
        JSONObject d;
        try {
            // The PWA's pattern: the catalogue call goes through the app's
            // own relay worker first; direct is the fallback (native has
            // no CORS, so direct genuinely works here).
            d = Net.getJsonObject(relay() + "/?url=" + enc(url), 8000);
        } catch (Exception relayDown) {
            d = Net.getJsonObject(url, 8000);
        }
        JSONArray results = d.optJSONArray("results");
        List<Song> out = new ArrayList<>();
        if (results != null) {
            for (int i = 0; i < results.length(); i++) {
                JSONObject x = results.optJSONObject(i);
                if (x == null) continue;
                Song s = shapeOfficial(x);
                if (s != null && !s.stream.isEmpty()) out.add(s);
            }
        }
        if (out.isEmpty()) throw new Exception("official route returned no playable rows");
        return out;
    }
}
