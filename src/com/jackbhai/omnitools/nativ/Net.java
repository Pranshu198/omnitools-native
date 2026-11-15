package com.jackbhai.omnitools.nativ;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal blocking HTTP+JSON helper on java.net only, so the API classes
 * that use it (SaavnApi, PipedApi, AlldlApi) also run unchanged in the JVM
 * smoke-test harness — no Android APIs in the data path.
 */
public final class Net {

    public static final String UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";

    private Net() {}

    public static String get(String url, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "application/json, */*");
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        if (in == null) throw new Exception("HTTP " + code + " (no body)");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
        } finally {
            c.disconnect();
        }
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        return sb.toString();
    }

    public static JSONObject getJsonObject(String url, int timeoutMs) throws Exception {
        return new JSONObject(get(url, timeoutMs));
    }

    public static JSONArray getJsonArray(String url, int timeoutMs) throws Exception {
        return new JSONArray(get(url, timeoutMs));
    }

    /** org.json optString with several candidate keys. */
    public static String opt(JSONObject o, String... keys) {
        if (o == null) return "";
        for (String k : keys) {
            String v = o.optString(k, "");
            if (v != null && !v.isEmpty() && !"null".equals(v)) return v;
        }
        return "";
    }

    /** Decode the handful of HTML entities the catalogues ship in titles. */
    public static String clean(String s) {
        if (s == null) return "";
        return s.replace("&quot;", "\"").replace("&amp;", "&")
                .replace("&#039;", "'").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").trim();
    }
}
