package com.jackbhai.omnitools.nativ;

/**
 * Track B multi-route stream resolution for the TV section. Order is set
 * by live evidence (trackB-evidence.md, 2026-11-15), not by hope:
 *
 *   1. WorkerYtApi — omni-proxy /yt (Innertube via Cloudflare egress).
 *      PASS that day: both test videos, ~0.7 s, 3/3 repeats. Adaptive
 *      video+audio urls play direct from a different IP (206 + media
 *      bytes); its muxed url does not (403), so the route itself already
 *      prefers adaptive — see WorkerYtApi.
 *   2. PipedApi.streams — the existing mirror pool. Search still works
 *      there, but /streams that day returned only 3 streams / 0 audio
 *      for the test video and its stream urls range-GET 401 from another
 *      IP, so it is a fallback, never primary.
 *   3. InvidiousApi — all instances failed that day; kept as a cheap,
 *      bounded last resort because instance health rotates.
 *
 * The winning Streams carries `via` naming the route, and TvSection
 * hands its progressive-or-split urls straight to WatchActivity
 * (ExoPlayer, MergingMediaSource for split video+audio). No media proxy
 * prefix: proxying googlevideo through the Worker returned 403.
 */
public final class StreamRoutes {

    private StreamRoutes() {}

    public interface Route {
        PipedApi.Streams get(String videoId) throws Exception;
    }

    private static final Route[] CHAIN = {
        WorkerYtApi::streams,
        PipedApi::streams,
        InvidiousApi::streams,
    };

    /** First route that yields any playable url wins. */
    public static PipedApi.Streams resolve(String videoId) throws Exception {
        Exception last = null;
        for (Route r : CHAIN) {
            try {
                PipedApi.Streams s = r.get(videoId);
                if (s != null && (!s.progressive.isEmpty() || !s.bestVideo.isEmpty())) {
                    return s;
                }
                last = new Exception("route returned no playable url");
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new Exception("every stream route failed");
    }
}
