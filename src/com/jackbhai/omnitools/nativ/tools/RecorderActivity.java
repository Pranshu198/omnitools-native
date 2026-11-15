package com.jackbhai.omnitools.nativ.tools;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.jackbhai.omnitools.nativ.Glass;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Voice Recorder (registry id "recorder"). Records AAC/.m4a into the
 * app-private recordings dir with a live level meter, then lists the
 * recordings with play / rename / delete / share.
 * Device behaviour (mic, playback) is on-device only — compile and
 * logic verified off-device.
 */
public final class RecorderActivity extends BaseToolActivity {

    private static final int REQ_MIC = 41;

    private TextView status, timer;
    private ProgressBar meter;
    private LinearLayout listBox;
    private TextView recordBtn;

    private MediaRecorder recorder;
    private File currentFile;
    private long startedAt;
    private boolean recording;

    private MediaPlayer player;
    private File playingFile;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!recording) return;
            timer.setText(ToolMath.formatDurationMs(System.currentTimeMillis() - startedAt));
            try {
                int amp = recorder != null ? recorder.getMaxAmplitude() : 0;
                meter.setProgress((int) (ToolMath.level01FromAmplitude(amp) * 1000));
            } catch (Exception ignored) { }
            ui.postDelayed(this, 100);
        }
    };

    @Override protected String toolTitle() { return "Voice Recorder"; }

    @Override protected void buildUi() {
        status = Glass.text(this, "Ready", 15, Glass.TEXT, false);
        body().addView(status);

        timer = Glass.text(this, "0:00", 40, Glass.TEXT, true);
        timer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = (int) Glass.dp(this, 10);
        body().addView(timer, tlp);

        meter = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        meter.setMax(1000);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) Glass.dp(this, 10));
        mlp.topMargin = (int) Glass.dp(this, 8);
        body().addView(meter, mlp);

        recordBtn = pill("Start recording", Glass.NEON_CYAN);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = (int) Glass.dp(this, 14);
        recordBtn.setLayoutParams(blp);
        recordBtn.setOnClickListener(v -> { if (recording) stopRecording(); else startRecording(); });
        body().addView(recordBtn);

        body().addView(heading("Recordings"));
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        body().addView(listBox);
        refreshList();
    }

    private File recordingsDir() {
        File dir = new File(getFilesDir(), "recordings");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void startRecording() {
        if (!ensurePerm(Manifest.permission.RECORD_AUDIO, REQ_MIC)) {
            status.setText("Microphone permission requested...");
            return;
        }
        stopPlayback();
        try {
            currentFile = new File(recordingsDir(),
                    "rec-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",
                            java.util.Locale.US).format(new Date()) + ".m4a");
            recorder = Build.VERSION.SDK_INT >= 31
                    ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(currentFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            startedAt = System.currentTimeMillis();
            status.setText("Recording...");
            recordBtn.setText("Stop");
            ui.post(tick);
        } catch (Exception e) {
            status.setText("Could not start recording: " + e.getMessage());
            releaseRecorder();
        }
    }

    private void stopRecording() {
        if (!recording) return;
        recording = false;
        ui.removeCallbacks(tick);
        meter.setProgress(0);
        try { recorder.stop(); } catch (Exception ignored) { }
        releaseRecorder();
        recordBtn.setText("Start recording");
        status.setText("Saved: " + (currentFile != null ? currentFile.getName() : ""));
        timer.setText("0:00");
        refreshList();
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try { recorder.reset(); } catch (Exception ignored) { }
            try { recorder.release(); } catch (Exception ignored) { }
            recorder = null;
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_MIC) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startRecording();
            else status.setText("Microphone permission denied — recording needs it.");
        }
    }

    // ------------------------------------------------------------ list + playback

    private void refreshList() {
        listBox.removeAllViews();
        File[] files = recordingsDir().listFiles((d, n) -> n.endsWith(".m4a"));
        List<File> sorted = new ArrayList<>();
        if (files != null) sorted.addAll(Arrays.asList(files));
        sorted.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        if (sorted.isEmpty()) {
            listBox.addView(note("No recordings yet. Tap Start recording."));
            return;
        }
        for (File f : sorted) listBox.addView(rowFor(f));
    }

    private View rowFor(final File f) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(Glass.panel(this, 14));
        Glass.pad(row, this, 12, 10, 12, 10);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = (int) Glass.dp(this, 6);
        row.setLayoutParams(rlp);

        TextView name = Glass.text(this, f.getName(), 15, Glass.TEXT, true);
        row.addView(name);
        String meta = ToolMath.formatBytes(f.length()) + "  ·  "
                + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new Date(f.lastModified()))
                + "  ·  " + durationOf(f);
        row.addView(Glass.text(this, meta, 12.5f, Glass.TEXT_DIM, false));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView play = smallAction(playingFile != null && playingFile.equals(f) ? "Stop" : "Play");
        play.setOnClickListener(v -> {
            if (playingFile != null && playingFile.equals(f)) { stopPlayback(); refreshList(); }
            else playFile(f);
        });
        TextView rename = smallAction("Rename");
        rename.setOnClickListener(v -> renameDialog(f));
        TextView share = smallAction("Share");
        share.setOnClickListener(v -> {
            if (!ShareUtil.shareFile(this, f, "audio/mp4", "Share recording"))
                toast("Could not share this recording");
        });
        TextView del = smallAction("Delete");
        del.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Delete recording?")
                .setMessage(f.getName())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    if (playingFile != null && playingFile.equals(f)) stopPlayback();
                    if (f.delete()) toast("Deleted");
                    refreshList();
                }).show());
        actions.addView(play); actions.addView(rename); actions.addView(share); actions.addView(del);
        row.addView(actions);
        return row;
    }

    private TextView smallAction(String label) {
        TextView t = Glass.text(this, label, 13.5f, Glass.NEON_CYAN, true);
        Glass.pad(t, this, 4, 8, 16, 2);
        return t;
    }

    private String durationOf(File f) {
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(f.getAbsolutePath());
            mp.prepare();
            return ToolMath.formatDurationMs(mp.getDuration());
        } catch (Exception e) {
            return "--:--";
        } finally {
            try { mp.release(); } catch (Exception ignored) { }
        }
    }

    private void playFile(File f) {
        stopPlayback();
        try {
            player = new MediaPlayer();
            player.setDataSource(f.getAbsolutePath());
            player.setOnCompletionListener(mp -> { stopPlayback(); refreshList(); });
            player.prepare();
            player.start();
            playingFile = f;
            status.setText("Playing: " + f.getName());
            refreshList();
        } catch (Exception e) {
            toast("Could not play: " + e.getMessage());
        }
    }

    private void stopPlayback() {
        if (player != null) {
            try { if (player.isPlaying()) player.stop(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
        if (playingFile != null) status.setText("Ready");
        playingFile = null;
    }

    private void renameDialog(final File f) {
        final EditText input = new EditText(this);
        String base = f.getName().replaceFirst("\\.m4a$", "");
        input.setText(base);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("Rename recording")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Rename", (d, w) -> {
                    String n = input.getText().toString().trim()
                            .replaceAll("[\\\\/:*?\"<>|]", "_");
                    if (n.isEmpty()) { toast("Name cannot be empty"); return; }
                    File target = new File(f.getParentFile(), n + ".m4a");
                    if (target.exists()) { toast("A recording with that name exists"); return; }
                    if (f.renameTo(target)) toast("Renamed"); else toast("Rename failed");
                    refreshList();
                }).show();
    }

    @Override protected void onPause() {
        super.onPause();
        if (recording) stopRecording();
    }

    @Override protected void onDestroy() {
        ui.removeCallbacks(tick);
        releaseRecorder();
        stopPlayback();
        super.onDestroy();
    }
}
