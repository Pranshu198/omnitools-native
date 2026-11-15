package com.jackbhai.omnitools.nativ.viz;

import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.Looper;

import com.jackbhai.omnitools.nativ.PlaybackService;

import androidx.media3.exoplayer.ExoPlayer;

/**
 * The provable audio feed for the visualizer: an
 * {@link android.media.audiofx.Visualizer} capture attached to the
 * ExoPlayer's own audio session — the exact same session source
 * SongsSection already uses successfully for the vendored gauravk
 * visualizer ({@code PlaybackService.sharedPlayer().getAudioSessionId()}).
 *
 * Needs RECORD_AUDIO (already declared in the manifest and already
 * requested at runtime by SongsSection's visualizer toggle).
 *
 * Waveform captures are converted from unsigned 8-bit (128 = silence)
 * to float PCM and pushed into projectM via
 * {@link ProjectMBridge#nPcmAddFloat}. FFT captures are reduced to
 * magnitude bands for the built-in fallback renderer.
 *
 * Known platform limit (honest): Visualizer capture tops out at 20 Hz
 * ({@code Visualizer.getMaxCaptureRate()}), so projectM receives PCM in
 * ~1024-sample chunks at 20 Hz rather than a continuous 44.1 kHz stream.
 * projectM's internal PCM buffer + decay smooths this; every Android
 * projectM port that uses the Visualizer API lives with the same limit.
 * The alternative (a custom media3 AudioSink tap) was NOT chosen: it
 * cannot be proven against the vendored media3 1.5.1 jars without
 * replacing the player's whole audio pipeline in PlaybackService,
 * which is outside this track's ownership.
 */
public final class AudioFeed {

    public interface Listener {
        void onFeedReady();
        void onFeedUnavailable(String why);
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Visualizer visualizer;
    private volatile long pmHandle = 0;
    private Listener listener;
    private boolean running = false;

    // Latest data for the fallback renderer (read on the GL thread).
    public final float[] waveform = new float[256];   // -1..1
    public final float[] bands = new float[32];       // 0..1 magnitudes
    public volatile float bassEnergy = 0f;            // 0..1, smoothed

    private final float[] pcmScratch = new float[2048];

    public void setProjectMHandle(long handle) { this.pmHandle = handle; }

    public void start(Listener l) {
        this.listener = l;
        running = true;
        attach(0);
    }

    private void attach(int tries) {
        if (!running) return;
        ExoPlayer p = PlaybackService.sharedPlayer();
        if (p == null) {
            if (tries < 20) {
                ui.postDelayed(() -> attach(tries + 1), 300);
            } else if (listener != null) {
                listener.onFeedUnavailable("Player not ready yet — start a song first.");
            }
            return;
        }
        try {
            int session = p.getAudioSessionId();
            releaseVisualizer();
            visualizer = new Visualizer(session);
            int[] range = Visualizer.getCaptureSizeRange();
            int size = Math.min(range[1], 1024);
            visualizer.setCaptureSize(size);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer v, byte[] w, int rate) {
                    if (w == null || w.length == 0) return;
                    int n = Math.min(w.length, pcmScratch.length);
                    for (int i = 0; i < n; i++) {
                        pcmScratch[i] = ((w[i] & 0xFF) - 128) / 128f;
                    }
                    long h = pmHandle;
                    if (h != 0 && ProjectMBridge.available()) {
                        try { ProjectMBridge.nPcmAddFloat(h, pcmScratch, 1); }
                        catch (Throwable ignored) {}
                    }
                    // Downsample into the fallback waveform ring.
                    int step = Math.max(1, n / waveform.length);
                    for (int i = 0; i < waveform.length; i++) {
                        int idx = Math.min(n - 1, i * step);
                        waveform[i] = pcmScratch[idx];
                    }
                }

                @Override
                public void onFftDataCapture(Visualizer v, byte[] fft, int rate) {
                    if (fft == null || fft.length < 4) return;
                    // fft layout: [dc.re, dc.im, k1.re, k1.im, ...] signed bytes.
                    int bins = (fft.length / 2) - 1;
                    float bass = 0f;
                    for (int b = 0; b < bands.length; b++) {
                        int bin = 1 + (int) ((b / (float) bands.length) * bins);
                        int re = fft[bin * 2], im = fft[bin * 2 + 1];
                        double mag = Math.sqrt(re * re + im * im) / 128.0;
                        bands[b] = (float) Math.min(1.0, mag);
                        if (b < 4) bass = Math.max(bass, bands[b]);
                    }
                    bassEnergy = bassEnergy * 0.6f + bass * 0.4f;
                }
            }, Visualizer.getMaxCaptureRate(), true, true);
            visualizer.setEnabled(true);
            if (listener != null) listener.onFeedReady();
        } catch (Exception e) {
            if (listener != null) {
                listener.onFeedUnavailable("Visualizer unavailable on this device.");
            }
        }
    }

    private void releaseVisualizer() {
        if (visualizer != null) {
            try { visualizer.setEnabled(false); } catch (Exception ignored) {}
            try { visualizer.release(); } catch (Exception ignored) {}
            visualizer = null;
        }
    }

    public void stop() {
        running = false;
        releaseVisualizer();
    }
}
