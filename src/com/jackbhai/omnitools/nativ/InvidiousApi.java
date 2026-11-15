package com.jackbhai.omnitools.nativ;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Track B last-resort route: public Invidious instances,
 * GET /api/v1/videos/{id} -> formatStreams (muxed) + adaptiveFormats.
 *
 * Honest status (trackB-evidence.md, 2026-11-15): every instance tested
 * FAILED from the sandbox that day (403 "Endpoint disabled", 403, 530,
 * timeouts). It stays in the chain because instance health rotates and
 * the cost is bounded (short timeouts, sequential, only reached when the
 * Worker and Piped both failed). Instance-proxied urls (when the instance
 * rewrites googlevideo through its own /videoplayback) are preferred,
 * since raw googlevideo adaptive urls from this route carry the same
 * cross-IP caveat as elsewhere; either way ExoPlayer gets a direct url.
 */
public final class InvidiousApi {

    private InvidiousApi() {}

    private static String host(String enc) { return Sec.decode(enc); }

    /* From api.invidious.io/instances.json (health-sorted) on 2026-11-15. */
    static final String[] POOL = {
        "aHR0cHM6Ly9pbnYubmFkZWtvLm5ldA==",
        "aHR0cHM6Ly95ZXd0dS5iZQ==",
        "aHR0cHM6Ly9pbnZpZGlvdXMuZjUuc2k=",
        "aHR0cHM6Ly9pbnZpZGlvdXMubmVyZHZwbi5kZQ==",
        "aHR0cHM6Ly9pbnZpZGlvdXMucHJpdmFjeXJlZGlyZWN0LmNvbQ==",
        "aHR0cHM6Ly9pdi5kYXR1cmEubmV0d29yaw==",
    };

    public static PipedApi.Streams streams(String videoId) throws Exception {
        Exception last = null;
        for (String encBase : POOL) {
            String base = host(encBase);
            try {
                JSONObject d = Net.getJsonObject(base + "/api/v1/videos/" + videoId, 6000);
                PipedApi.Streams s = new PipedApi.Streams();
                s.via = "invidious/" + base.replace("https://", "");
                s.title = Net.clean(d.optString("title", ""));
                s.uploader = Net.clean(d.optString("author", ""));
                JSONArray thumbs = d.optJSONArray("videoThumbnails");
                if (thumbs != null && thumbs.length() > 0) {
                    s.thumb = thumbs.optJSONObject(0).optString("url", "");
                }
                List<PipedApi.Option> adaptiveVideo = new ArrayList<>();
                JSONArray af = d.optJSONArray("adaptiveFormats");
                long bestABitrate = -1;
                if (af != null) {
                    for (int i = 0; i < af.length(); i++) {
                        JSONObject f = af.optJSONObject(i);
                        if (f == null) continue;
                        String u = f.optString("url", "");
                        if (u.isEmpty()) continue;
                        String type = f.optString("type", "");
                        PipedApi.Option o = new PipedApi.Option();
                        o.url = u;
                        o.size = f.optLong("contentLength", 0);
                        if (type.startsWith("audio/")) {
                            long br = f.optLong("bitrate", 0);
                            o.label = Math.round(br / 1000.0) + " kbps";
                            s.audioOptions.add(o);
                            if (br > bestABitrate) { bestABitrate = br; s.bestAudio = u; }
                        } else if (type.startsWith("video/")) {
                            o.label = f.optString("qualityLabel", "Video");
                            o.videoOnly = true;
                            adaptiveVideo.add(o);
                        }
                    }
                }
                JSONArray fs = d.optJSONArray("formatStreams");
                if (fs != null) {
                    for (int i = 0; i < fs.length(); i++) {
                        JSONObject f = fs.optJSONObject(i);
                        if (f == null) continue;
                        String u = f.optString("url", "");
                        if (u.isEmpty()) continue;
                        PipedApi.Option o = new PipedApi.Option();
                        o.label = f.optString("qualityLabel", "Video");
                        o.url = u;
                        o.size = f.optLong("contentLength", 0);
                        s.videoOptions.add(o);
                        if (s.progressive.isEmpty()) s.progressive = u;
                    }
                }
                s.videoOptions.addAll(adaptiveVideo);
                if (!s.progressive.isEmpty()) {
                    s.bestVideo = s.progressive;
                    return s;
                }
                if (!adaptiveVideo.isEmpty()) {
                    s.bestVideo = adaptiveVideo.get(0).url;
                    return s;
                }
                last = new Exception("no streams from " + base);
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new Exception("invidious resolution failed");
    }
}
