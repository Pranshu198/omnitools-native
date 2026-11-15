package com.jackbhai.omnitools.nativ;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Video search + stream resolution through the same public front-end API
 * pool the PWA uses (src/core/ytmusic.js / downloader.jsx): three mirrors,
 * first answer wins. The wireway mirror is the only one whose /streams
 * returns audio streams, so it is tried first for resolution.
 */
public final class PipedApi {

    private PipedApi() {}

    /* Hosts stored encoded, decoded at runtime via Sec (see SaavnApi). */
    private static String host(String enc) { return Sec.decode(enc); }

    static final String[] POOL = {
        "aHR0cHM6Ly9hcGkucGlwZWQucHJpdmF0ZS5jb2ZmZWU=",
        "aHR0cHM6Ly9waXBlZGFwaS53aXJld2F5LmNo",
        "aHR0cHM6Ly9waXBlZGFwaS5kdWNrcy5wYXJ0eQ==",
    };

    public static final class Video {
        public String id = "";
        public String title = "";
        public String uploader = "";
        public long durSec = 0;
        public String thumb = "";
        public long views = 0;
    }

    public static final class Streams {
        public String via = "";              // route that produced this (Track B)
        public String title = "";
        public String uploader = "";
        public String thumb = "";
        public String progressive = "";      // video+audio in one file, if any
        public String bestVideo = "";
        public String bestAudio = "";
        public final List<Option> videoOptions = new ArrayList<>();
        public final List<Option> audioOptions = new ArrayList<>();
    }

    public static final class Option {
        public String label = "";
        public String url = "";
        public long size = 0;
        public boolean videoOnly = false;
    }

    public static List<Video> search(String query) throws Exception {
        Exception last = null;
        for (String encBase : POOL) {
            String base = host(encBase);
            try {
                String url = base + "/search?q=" + URLEncoder.encode(query, "UTF-8")
                        + "&filter=all";
                JSONObject d = Net.getJsonObject(url, 8000);
                JSONArray items = d.optJSONArray("items");
                List<Video> out = new ArrayList<>();
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject v = items.optJSONObject(i);
                        if (v == null) continue;
                        String u = v.optString("url", "");
                        String type = v.optString("type", "");
                        if (!u.contains("watch") && !"stream".equals(type)) continue;
                        String id = "";
                        int k = u.indexOf("v=");
                        if (k >= 0) {
                            id = u.substring(k + 2);
                            int amp = id.indexOf('&');
                            if (amp >= 0) id = id.substring(0, amp);
                        }
                        if (id.isEmpty()) continue;
                        Video vid = new Video();
                        vid.id = id;
                        vid.title = Net.clean(v.optString("title", ""));
                        vid.uploader = Net.clean(Net.opt(v, "uploaderName", "uploader"));
                        vid.durSec = v.optLong("duration", 0);
                        vid.thumb = v.optString("thumbnail", "");
                        if (vid.thumb.startsWith("//")) vid.thumb = "https:" + vid.thumb;
                        vid.views = v.optLong("views", 0);
                        out.add(vid);
                    }
                }
                if (!out.isEmpty()) return out;
                last = new Exception("empty from " + base);
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new Exception("every video source failed");
    }

    public static Streams streams(String videoId) throws Exception {
        // wireway first: measured as the only mirror returning audio streams.
        String[] order = {host(POOL[1]), host(POOL[0]), host(POOL[2])};
        Exception last = null;
        for (String base : order) {
            try {
                JSONObject d = Net.getJsonObject(base + "/streams/" + videoId, 8000);
                Streams s = new Streams();
                s.via = "piped/" + base.replace("https://", "");
                s.title = Net.clean(d.optString("title", ""));
                s.uploader = Net.clean(d.optString("uploader", ""));
                s.thumb = d.optString("thumbnailUrl", "");
                JSONArray vs = d.optJSONArray("videoStreams");
                long bestVBitrate = -1;
                if (vs != null) {
                    for (int i = 0; i < vs.length(); i++) {
                        JSONObject v = vs.optJSONObject(i);
                        if (v == null) continue;
                        String u = v.optString("url", "");
                        if (u.isEmpty()) continue;
                        Option o = new Option();
                        o.label = v.optString("quality", "Video");
                        o.url = u;
                        o.size = v.optLong("contentLength", 0);
                        o.videoOnly = v.optBoolean("videoOnly", false);
                        s.videoOptions.add(o);
                        if (!o.videoOnly && s.progressive.isEmpty()) s.progressive = u;
                        long br = v.optLong("bitrate", 0);
                        if (br > bestVBitrate) { bestVBitrate = br; s.bestVideo = u; }
                    }
                }
                JSONArray as = d.optJSONArray("audioStreams");
                long bestABitrate = -1;
                if (as != null) {
                    for (int i = 0; i < as.length(); i++) {
                        JSONObject a = as.optJSONObject(i);
                        if (a == null) continue;
                        String u = a.optString("url", "");
                        if (u.isEmpty()) continue;
                        Option o = new Option();
                        long br = a.optLong("bitrate", 0);
                        o.label = Math.round(br / 1000.0) + " kbps";
                        o.url = u;
                        o.size = a.optLong("contentLength", 0);
                        s.audioOptions.add(o);
                        if (br > bestABitrate) { bestABitrate = br; s.bestAudio = u; }
                    }
                }
                if (!s.videoOptions.isEmpty() || !s.audioOptions.isEmpty()) return s;
                last = new Exception("no streams from " + base);
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new Exception("stream resolution failed");
    }
}
