package com.jackbhai.omnitools.nativ;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * SMOKE-HARNESS SHADOW of the app's Net — same public API, but get()
 * serves REAL responses captured live with curl minutes earlier
 * (smoke/fixtures/). Why: this sandbox blocks raw TCP from the JVM
 * ("Other TCP connections is turned off for this assistant"), so the
 * JVM cannot fetch live; curl can, and did. The app's real parsing,
 * shaping and DES-decryption code runs against the genuine payloads.
 * The live transport itself (20 lines of stock HttpURLConnection in
 * the real Net) is the only part not exercised here.
 */
public final class Net {

    public static final String UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";

    private Net() {}

    private static String fixture(String name) throws Exception {
        File f = new File(System.getProperty("smoke.fixtures", "fixtures"), name);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    public static String get(String url, int timeoutMs) throws Exception {
        String route;
        if (url.contains("/search/songs")) route = "mirror.json";
        else if (url.contains("alldl")) route = "alldl.json";
        else if (url.contains("/streams/")) route = "piped_streams.json";
        else if (url.contains("/search?")) route = "piped_search.json";
        else if (url.contains("api.php")) route = "official.json";
        else throw new Exception("no fixture routed for URL host: "
                + url.replaceAll("^(https?://[^/]+).*", "$1"));
        System.out.println("      [fixture] " + route + "  <-  "
                + url.substring(0, Math.min(96, url.length())) + (url.length() > 96 ? "…" : ""));
        return fixture(route);
    }

    public static JSONObject getJsonObject(String url, int timeoutMs) throws Exception {
        return new JSONObject(get(url, timeoutMs));
    }

    public static JSONArray getJsonArray(String url, int timeoutMs) throws Exception {
        return new JSONArray(get(url, timeoutMs));
    }

    /** org.json optString with several candidate keys. */
    public static String opt(JSONObject o, String... keys) {        if (o == null) return "";
        for (String k : keys) {
            String v = o.optString(k, "");
            if (v != null && !v.isEmpty() && !"null".equals(v)) return v;
        }
        return "";
    }

    public static String readFile(java.io.File f) throws Exception {
        byte[] buf = new byte[(int) f.length()];
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
        }
        return new String(buf, "UTF-8");
    }

    /** Decode the handful of HTML entities the catalogues ship in titles. */
    public static String clean(String s) {
        if (s == null) return "";
        return s.replace("&quot;", "\"").replace("&amp;", "&")
                .replace("&#039;", "'").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").trim();
    }

    public static String ep(String b64) {
        if (b64 == null || b64.isEmpty()) return "";
        try {
            return new String(java.util.Base64.getDecoder().decode(b64),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
