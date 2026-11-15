package com.jackbhai.omnitools.nativ.tools;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.jackbhai.omnitools.nativ.Glass;

/**
 * Noise Meter (new tool id "noise"). AudioRecord RMS -> dB via the
 * pure, unit-tested {@link ToolMath#dbFromRms}. The reading is an
 * estimate from an uncalibrated phone mic and the UI says exactly that.
 * Mic behaviour is on-device only.
 */
public final class NoiseActivity extends BaseToolActivity {

    private static final int REQ_MIC = 42;
    private static final int SAMPLE_RATE = 44100;

    private TextView dbLabel, peakLabel, toggleBtn;
    private ProgressBar bar;
    private volatile boolean running;
    private Thread worker;
    private double peakDb;

    @Override protected String toolTitle() { return "Noise Meter"; }

    @Override protected void buildUi() {
        dbLabel = Glass.text(this, "-- dB", 44, Glass.TEXT, true);
        dbLabel.setGravity(Gravity.CENTER);
        body().addView(dbLabel);

        peakLabel = Glass.text(this, "Peak: -- dB", 14, Glass.TEXT_DIM, false);
        peakLabel.setGravity(Gravity.CENTER);
        body().addView(peakLabel);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(120);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) Glass.dp(this, 10));
        blp.topMargin = (int) Glass.dp(this, 12);
        body().addView(bar, blp);

        toggleBtn = pill("Start measuring", Glass.NEON_CYAN);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = (int) Glass.dp(this, 14);
        toggleBtn.setLayoutParams(tlp);
        toggleBtn.setOnClickListener(v -> { if (running) stopMeter(); else startMeter(); });
        body().addView(toggleBtn);

        body().addView(note("This is an estimate, not calibrated. Phone microphones "
                + "are not measurement instruments — treat the number as a rough guide "
                + "only (quiet room ~40 dB, conversation ~60 dB, busy traffic ~85 dB)."));
    }

    private void startMeter() {
        if (!ensurePerm(Manifest.permission.RECORD_AUDIO, REQ_MIC)) return;
        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf <= 0) { toast("Audio recording is not available on this device"); return; }
        final AudioRecord rec;
        try {
            rec = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBuf, 8192));
        } catch (Exception e) {
            toast("Could not open the microphone");
            return;
        }
        if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
            rec.release();
            toast("Could not open the microphone");
            return;
        }
        peakDb = 0;
        running = true;
        toggleBtn.setText("Stop");
        rec.startRecording();
        worker = new Thread(() -> {
            short[] buf = new short[4096];
            double smooth = 0;
            boolean first = true;
            while (running) {
                int n = rec.read(buf, 0, buf.length);
                if (n <= 0) continue;
                double db = ToolMath.dbFromRms(ToolMath.rmsOf(buf, n));
                smooth = first ? db : smooth + 0.35 * (db - smooth);
                first = false;
                if (db > peakDb) peakDb = db;
                final double show = smooth, peak = peakDb;
                runOnUiThread(() -> {
                    dbLabel.setText(String.format(java.util.Locale.US, "%.0f dB", show));
                    peakLabel.setText(String.format(java.util.Locale.US, "Peak: %.0f dB", peak));
                    bar.setProgress((int) show);
                });
            }
            try { rec.stop(); } catch (Exception ignored) { }
            rec.release();
        }, "noise-meter");
        worker.start();
    }

    private void stopMeter() {
        running = false;
        toggleBtn.setText("Start measuring");
        if (worker != null) { try { worker.join(500); } catch (Exception ignored) { } worker = null; }
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_MIC) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startMeter();
            else toast("Microphone permission denied — the meter needs it.");
        }
    }

    @Override protected void onPause() { stopMeter(); super.onPause(); }
    @Override protected void onDestroy() { stopMeter(); super.onDestroy(); }
}
