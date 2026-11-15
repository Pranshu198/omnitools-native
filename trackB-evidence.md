# Track B — TV video-stream extraction: live evidence (2026-11-15, sandbox curl)

Test videos: `dQw4w9WgXcQ` (Rick Astley) and `jNQXAC9IVRw` (Me at the zoo).
"Playable" = range GET (bytes 0-1023/2047, redirects followed) returns
206/200 with a media content-type AND real media magic bytes (MP4 `ftyp`
or WebM EBML header), fetched from this sandbox — a different egress IP
than any resolver. An HTML/bot/401/403 answer is a FAIL.

## Route 4 first (it decided the design): omni-proxy Worker /yt

Worker: `https://omni-proxy.omni-jackbhai.workers.dev/yt?v=<id>`

| Attempt | HTTP | Time | Result |
|---|---|---|---|
| /yt?v=dQw4w9WgXcQ | 200 | 0.66 s | success, via=ANDROID_VR, muxed 360p + 8 qualities (2160p→144p) + audio |
| /yt?v=jNQXAC9IVRw | 200 | 0.71 s | success, via=ANDROID, muxed 240p + adaptive + audio |
| /yt?v=dQw4w9WgXcQ ×3 repeats | 200 ×3 | 0.67–0.88 s | via=ANDROID / ANDROID_VR, muxed + 8 qualities + audio every time |

### IP-binding check on the returned googlevideo urls (CRITICAL)

| URL kind | Direct range GET from sandbox (different IP) | Via Worker generic proxy `/?url=` |
|---|---|---|
| Muxed itag-18 videoUrl | 302 → **403**, 0 bytes — **FAIL, IP-bound** | **403** — FAIL |
| Adaptive video-only (2160p/1440p webm, 1080p/720p/480p mp4) | 302 → **206**, `video/webm` / `video/mp4`, EBML / `ftypdash` bytes — **PASS** | **403** — FAIL |
| Audio itag-140 audioUrl | 302 → **206**, `audio/mp4`, `ftypdash` bytes — **PASS** | (not needed) |
| VID2 adaptive 240p mp4 | 302 → **206**, `video/mp4`, `ftypdash` — **PASS** | — |

Verdict: **the app must play the Worker's ADAPTIVE video + AUDIO urls
DIRECT from googlevideo (merged in ExoPlayer), never the muxed url, and
never proxied through the Worker** (its generic relay gets 403 from
googlevideo). No new Worker route is needed — no patch file.

## Route 1: Piped instances

Existing pool first:

| Instance | /streams dQw4 | /search | Stream-url range GET |
|---|---|---|---|
| api.piped.private.coffee | 200, but only 3 videoStreams / **0 audioStreams** (2 are LBRY/Odysee mirrors, 1 is instance-proxied googlevideo) | 200, real items | **401** "this content can…" (text/plain, 45 B) on both the proxied googlevideo url and the LBRY mp4 — FAIL |
| pipedapi.ducks.party | 200, same 3/0 shape | 200, real items | **401**, same body — FAIL |
| pipedapi.wireway.ch | **418** (empty) | **418** | — FAIL |

8 more instances NOT in PipedApi.java (from the official instance lists /
worker's allow-list):

| Instance | /streams | /search |
|---|---|---|
| pipedapi.kavin.rocks | 403 (Cloudflare HTML) / TLS timeout | connection closed |
| pipedapi.adminforge.de | 403 Forbidden | 404 (parked frontend HTML) |
| pipedapi.drgns.space | TLS EOF / closed | closed |
| api.piped.projectsegfau.lt | 200 "Piped has shutdown" | 200 "Piped has shutdown" |
| pipedapi.orangenet.cc | 502 | 502 |
| pipedapi.leptons.xyz | TLS timeout | 502 (Cloudflare) |
| piped-api.lunar.icu | 502 | 502 |
| pipedapi.reallyaweso.me | 200 spam HTML (not Piped) | 200 spam HTML |
| pipedapi.smnz.de / piped-api.garudalinux.org / pa.il.ax / pipedapi-libre.kavin.rocks | timeout / empty reply ×4 | — |

Instance-list fetches: `piped.video/api/v1/instances` returned the
frontend HTML (200, not JSON) from here; `api.invidious.io/instances.json`
returned real JSON (200, 7,375 B).

**Piped verdict: search still works on 2 of 3 pooled mirrors; /streams
playback FAILS today** (degraded 3-stream/0-audio answers whose urls
401 cross-IP; every other instance dead). Kept as fallback #2 only.

## Route 2: Invidious instances (from api.invidious.io list)

| Instance | GET /api/v1/videos/dQw4w9WgXcQ |
|---|---|
| inv.nadeko.net | 403 "Endpoint disabled" |
| yewtu.be | 403 Forbidden (openresty) |
| invidious.f5.si | 530 (error 1033) |
| invidious.nerdvpn.de | timeout (15 s) |
| invidious.privacyredirect.com | empty reply |
| iv.datura.network | empty reply |

**Invidious verdict: FAIL today, all 6.** Kept as bounded last-resort
fallback #3 (health rotates; short 6 s timeouts).

## Route 3: Cobalt-style public APIs

| Attempt | Result |
|---|---|
| instances.cobalt.best/api/instances.json | timeout, HTTP 000 |
| api.cobalt.tools POST {url} | timeout, HTTP 000 |
| cobalt.canine.tools / api.cobalt.kwiatekmiki.com / cobaltapi.kittycat.boo POST | timeout ×3, HTTP 000 |

**Cobalt verdict: FAIL / unprovable from this egress** — not shipped.

## What shipped (app side)

Chain in `StreamRoutes.resolve()` (order = evidence above):
1. `WorkerYtApi` — Worker /yt; picks best adaptive **mp4 ≤1080p**
   video-only quality + `audioUrl` (muxed only if no adaptive exists).
2. `PipedApi.streams` — existing pool, unchanged search.
3. `InvidiousApi` — 6 instances from the official list.

`TvSection.openVideo()` now calls `StreamRoutes.resolve()`; the result
flows to the existing `WatchActivity` (ExoPlayer `MergingMediaSource`
for split video+audio, direct googlevideo urls, no proxy prefix).
Full source tree compile-checked with javac (android-34 android.jar +
libs/*.jar + generated R from build/manual/gen): clean.

Caveats: adaptive googlevideo mp4s are DASH-fragmented (`ftypdash`) —
the same shape the previous Piped flow already fed ExoPlayer's
ProgressiveMediaSource; on-device playback confirmation is the
coordinator's step. Worker /yt remains subject to YouTube's
LOGIN_REQUIRED bursts on shared egress (Round 5 notes); the chain's
fallbacks cover total Worker failure, not a simultaneous YouTube-wide
flag.
