package com.jackbhai.omnitools.nativ;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Track B primary route: the first-party omni-proxy Worker's /yt resolver
 * (Innertube, Cloudflare egress). Proven live 2026-11-15 (see
 * trackB-evidence.md): HTTP 200 in ~0.7 s for both test videos, 3/3 repeats.
 *
 * Playback-shape decision, also from that evidence: the resolver's MUXED
 * url (itag 18) is IP-bound — a range GET from a different egress IP
 * returns 403 after redirect. Its ADAPTIVE urls are not: video-only
 * qualities and the audio url returned 206 + real media bytes from a
 * different IP. So this class deliberately prefers an adaptive mp4
 * video-only stream + the separate audio stream (WatchActivity merges
 * them) and only falls back to the muxed url when no adaptive stream
 * exists. Media is played DIRECT from googlevideo — proxying it through
 * the Worker's generic relay returned 403 in testing, so no proxy prefix
 * is ever applied here.
 */
public final class WorkerYtApi {

    private WorkerYtApi() {}

    /* Same relay base AlldlApi/SaavnApi use, encoded the same way. */
    private static String relay() {
        return Sec.decode("aHR0cHM6Ly9vbW5pLXByb3h5Lm9tbmktamFja2JoYWkud29ya2Vycy5kZXY=");
    }

    public static PipedApi.Streams streams(String videoId) throws Exception {
        JSONObject d = Net.getJsonObject(relay() + "/yt?v=" + videoId, 15000);
        if (!d.optBoolean("success", false)) {
            throw new Exception("worker /yt: " + d.optString("error", "failed"));
        }
        JSONObject mi = d.optJSONObject("mediaInfo");
        if (mi == null) throw new Exception("worker /yt: no mediaInfo");

        PipedApi.Streams s = new PipedApi.Streams();
        s.via = "worker-yt/" + d.optString("via", "?");
        s.title = Net.clean(mi.optString("title", ""));
        s.uploader = Net.clean(mi.optString("author", ""));
        s.thumb = mi.optString("thumbnail", "");

        String audio = mi.optString("audioUrl", "");
        if (!audio.isEmpty()) {
            s.bestAudio = audio;
            PipedApi.Option o = new PipedApi.Option();
            o.label = Math.round(mi.optLong("audioBitrate", 0) / 1000.0) + " kbps";
            o.url = audio;
            s.audioOptions.add(o);
        }
        JSONArray ao = mi.optJSONArray("audioOptions");
        if (ao != null) {
            for (int i = 0; i < ao.length(); i++) {
                JSONObject a = ao.optJSONObject(i);
                if (a == null) continue;
                String u = a.optString("url", "");
                if (u.isEmpty() || u.equals(audio)) continue;
                PipedApi.Option o = new PipedApi.Option();
                o.label = a.optString("quality", "Audio");
                o.url = u;
                o.size = a.optLong("size", 0);
                s.audioOptions.add(o);
            }
        }

        /* qualities[]: muxed first (no suffix), then adaptive video-only
           rows labelled "720p (no audio)" etc. Collect both kinds, then
           choose: best adaptive mp4 at or below 1080p wins (proven to
           play cross-IP); otherwise best adaptive of any codec; the
           muxed url only when there is no adaptive row at all. */
        String muxed = mi.optString("videoUrl", "");
        PipedApi.Option bestMp4 = null, bestAny = null;
        int bestMp4H = -1, bestAnyH = -1;
        JSONArray qs = mi.optJSONArray("qualities");
        if (qs != null) {
            for (int i = 0; i < qs.length(); i++) {
                JSONObject q = qs.optJSONObject(i);
                if (q == null) continue;
                String u = q.optString("url", "");
                if (u.isEmpty()) continue;
                String label = q.optString("quality", "Video");
                boolean videoOnly = label.contains("(no audio)");
                PipedApi.Option o = new PipedApi.Option();
                o.label = label;
                o.url = u;
                o.size = q.optLong("size", 0);
                o.videoOnly = videoOnly;
                s.videoOptions.add(o);
                if (!videoOnly) {
                    if (muxed.isEmpty()) muxed = u;
                    continue;
                }
                int h = heightOf(label);
                String mime = q.optString("mime", "");
                if (mime.contains("mp4") && h <= 1080 && h > bestMp4H) {
                    bestMp4H = h; bestMp4 = o;
                }
                if (h > bestAnyH) { bestAnyH = h; bestAny = o; }
            }
        }
        PipedApi.Option pick = bestMp4 != null ? bestMp4 : bestAny;
        if (pick != null) {
            s.bestVideo = pick.url;   // video-only; audio comes from bestAudio
        } else if (!muxed.isEmpty()) {
            /* Last resort inside this route: muxed may 403 on a phone IP
               different from the Worker's, but it is the only video leg
               this route has for such videos. */
            s.progressive = muxed;
            s.bestVideo = muxed;
        }
        if (s.bestVideo.isEmpty() && s.progressive.isEmpty()) {
            throw new Exception("worker /yt: no video stream");
        }
        return s;
    }

    private static int heightOf(String label) {
        int p = label.indexOf('p');
        if (p <= 0) return 0;
        int start = p;
        while (start > 0 && Character.isDigit(label.charAt(start - 1))) start--;
        try { return Integer.parseInt(label.substring(start, p)); }
        catch (Exception e) { return 0; }
    }
}
