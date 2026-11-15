package com.jackbhai.omnitools.nativ;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Link resolution for the Downloader section — the same broad resolver the
 * PWA's downloader uses (freeapi.pro /api/alldl, see src/core/endpoints.js
 * RESOLVE_API and src/tools/downloader.jsx mapResolver).
 *
 * A browser reaches it through a proxy chain because the resolver sends no
 * CORS header. A native app has no CORS, so the first attempt is DIRECT;
 * the app's own relay is the fallback, mirroring the PWA's stage order.
 */
public final class AlldlApi {

    private AlldlApi() {}

    public static final String RESOLVE_API = "https://freeapi.pro/api/alldl?url=";
    public static final String RELAY = "https://omni-proxy.omni-jackbhai.workers.dev";

    public static final class Option {
        public String label = "";
        public String url = "";
        public long size = 0;
        public boolean audio = false;
    }

    public static final class Result {
        public String title = "";
        public String author = "";
        public String platform = "";
        public String thumb = "";
        public final List<Option> options = new ArrayList<>();
    }

    public static Result resolve(String pageUrl) throws Exception {
        String target = RESOLVE_API + URLEncoder.encode(pageUrl, "UTF-8");
        Exception last = null;
        // Stage 0: direct.
        try {
            Result r = map(Net.getJsonObject(target, 20000));
            if (r != null) return r;
            last = new Exception("resolver returned no media");
        } catch (Exception e) { last = e; }
        // Stage 1: the app's relay.
        try {
            String via = RELAY + "/?url=" + URLEncoder.encode(target, "UTF-8");
            Result r = map(Net.getJsonObject(via, 25000));
            if (r != null) return r;
            last = new Exception("relay returned no media");
        } catch (Exception e) { last = e; }
        throw last != null ? last : new Exception("resolve failed");
    }

    private static Result map(JSONObject d) {
        if (d == null) return null;
        JSONObject m = d.optJSONObject("mediaInfo");
        if (m == null) m = new JSONObject();
        Result r = new Result();
        r.title = Net.clean(Net.opt(m, "title"));
        if (r.title.isEmpty()) r.title = Net.clean(d.optString("title", ""));
        if (r.title.isEmpty()) r.title = "Media";
        r.author = Net.clean(Net.opt(m, "author"));
        r.platform = Net.opt(m, "platform");
        if (r.platform.isEmpty()) r.platform = d.optString("platform", "");
        r.thumb = Net.opt(m, "thumbnail", "coverImage");
        if (r.thumb.isEmpty()) r.thumb = d.optString("thumbnail", "");

        String videoUrl = Net.opt(m, "videoUrl");
        if (videoUrl.isEmpty()) videoUrl = d.optString("url", "");
        if (videoUrl.isEmpty()) {
            JSONArray links = d.optJSONArray("links");
            if (links != null && links.length() > 0) videoUrl = links.optString(0, "");
        }
        String audioUrl = Net.opt(m, "audioUrl");

        JSONArray qs = m.optJSONArray("qualities");
        if (qs != null) {
            for (int i = 0; i < qs.length(); i++) {
                JSONObject q = qs.optJSONObject(i);
                if (q == null) continue;
                String u = q.optString("url", "");
                if (u.isEmpty()) continue;
                Option o = new Option();
                o.label = Net.opt(q, "quality", "q");
                if (o.label.isEmpty()) o.label = "Video";
                o.url = u;
                o.size = q.optLong("size", q.optLong("contentLength", 0));
                r.options.add(o);
            }
        }
        JSONArray aos = m.optJSONArray("audioOptions");
        if (aos != null) {
            for (int i = 0; i < aos.length(); i++) {
                JSONObject a = aos.optJSONObject(i);
                if (a == null) continue;
                String u = a.optString("url", "");
                if (u.isEmpty()) continue;
                Option o = new Option();
                o.label = Net.opt(a, "quality", "q");
                if (o.label.isEmpty()) o.label = "Audio";
                o.url = u;
                o.size = a.optLong("size", a.optLong("contentLength", 0));
                o.audio = true;
                r.options.add(o);
            }
        }
        if (r.options.isEmpty() && !videoUrl.isEmpty()) {
            Option o = new Option();
            o.label = "Video (best)";
            o.url = videoUrl;
            r.options.add(o);
        }
        if (r.options.isEmpty() && !audioUrl.isEmpty()) {
            Option o = new Option();
            o.label = "Audio";
            o.url = audioUrl;
            o.audio = true;
            r.options.add(o);
        } else if (!audioUrl.isEmpty()) {
            Option o = new Option();
            o.label = "Audio only";
            o.url = audioUrl;
            o.audio = true;
            r.options.add(o);
        }
        return r.options.isEmpty() ? null : r;
    }
}
