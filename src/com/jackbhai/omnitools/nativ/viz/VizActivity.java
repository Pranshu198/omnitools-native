package com.jackbhai.omnitools.nativ.viz;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Fullscreen music visualizations (Phase 3, Track C).
 *
 * Engine 1 — projectM / MilkDrop: thousands of .milk presets (bundled
 * starter pack + user folders via {@link PresetCatalog}), rendered by
 * libprojectm through {@link ProjectMBridge}, fed by the player's own
 * audio session via {@link AudioFeed}.
 * Engine 2 — built-in OpenGL modes (Spectrum / Wave / Particles /
 * Kaleidoscope) from {@link FallbackVizRenderer}; automatic fallback if
 * the native library is absent, and a manual lightweight option.
 *
 * Controls (tap the surface to show/hide): preset picker, next/prev,
 * lock, sensitivity slider, preset-duration ("speed") slider, crossfade
 * slider, and "Load more presets" (SAF folder import — plus anything
 * dropped into Downloads/OmniPresets is picked up on Rescan).
 *
 * Integration: manifest entry + launch hook are owned by the
 * coordinator — see projectm/INTEGRATION.md in the repo.
 */
public class VizActivity extends Activity {

    private static final int REQ_RECORD = 41;
    private static final int REQ_TREE = 42;

    private GLSurfaceView glView;
    private VizRenderer renderer;
    private AudioFeed feed;
    private TextView presetLabel;
    private TextView noteLabel;
    private LinearLayout controls;
    private Button engineBtn;
    private Button lockBtn;
    private boolean controlsVisible = true;

    private List<PresetCatalog.Entry> catalog;
    private int current = -1;
    private long lastLoadMs = 0;
    private boolean engineReady = false;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable autoAdvance = new Runnable() {
        @Override public void run() {
            if (renderer != null && renderer.useProjectM && !renderer.presetLocked
                    && catalog != null && catalog.size() > 1
                    && System.currentTimeMillis() - lastLoadMs
                            > renderer.presetDuration * 1000) {
                stepPreset(1, false);
            }
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersive();

        feed = new AudioFeed();
        renderer = new VizRenderer(feed);
        loadPrefs();

        FrameLayout root = new FrameLayout(this);
        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(3);
        glView.setRenderer(renderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        glView.setOnClickListener(v -> toggleControls());
        root.addView(glView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        presetLabel = label("", 15, Color.WHITE);
        presetLabel.setGravity(Gravity.CENTER);
        presetLabel.setShadowLayer(6, 0, 2, Color.BLACK);
        FrameLayout.LayoutParams lpTop = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lpTop.gravity = Gravity.TOP;
        lpTop.setMargins(24, 36, 24, 0);
        root.addView(presetLabel, lpTop);

        noteLabel = label("", 12.5f, 0xFFB0BEC5);
        noteLabel.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lpNote = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lpNote.gravity = Gravity.TOP;
        lpNote.setMargins(24, 76, 24, 0);
        root.addView(noteLabel, lpNote);

        controls = buildControls();
        FrameLayout.LayoutParams lpCtl = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lpCtl.gravity = Gravity.BOTTOM;
        root.addView(controls, lpCtl);

        setContentView(root);

        renderer.setCallbacks(projectmOk -> runOnUiThread(() -> {
            engineReady = true;
            if (!projectmOk && renderer.useProjectM) {
                renderer.useProjectM = false;
                noteLabel.setText("MilkDrop engine unavailable on this device — built-in modes.");
                updateEngineBtn();
            }
            if (renderer.useProjectM) loadInitialPreset();
        }));

        rescanCatalog();
        startFeedIfPermitted();
        ui.postDelayed(autoAdvance, 1000);
    }

    /* ------------------------------------------------------------ UI build */

    private TextView label(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private Button btn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(12.5f);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x55222A33);
        bg.setCornerRadius(24);
        bg.setStroke(1, 0x66FFFFFF);
        b.setBackground(bg);
        return b;
    }

    private LinearLayout buildControls() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(20, 14, 20, 22);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xB3101418);
        bg.setCornerRadii(new float[]{36, 36, 36, 36, 0, 0, 0, 0});
        col.setBackground(bg);

        LinearLayout row1 = new LinearLayout(this);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        Button prev = btn("◀ Prev");
        prev.setOnClickListener(v -> stepPreset(-1, true));
        Button pick = btn("Presets");
        pick.setOnClickListener(v -> showPresetPicker());
        Button next = btn("Next ▶");
        next.setOnClickListener(v -> stepPreset(1, true));
        lockBtn = btn("Lock: off");
        lockBtn.setOnClickListener(v -> {
            renderer.presetLocked = !renderer.presetLocked;
            lockBtn.setText(renderer.presetLocked ? "Lock: on" : "Lock: off");
            queueApplySettings();
            savePrefs();
        });
        LinearLayout.LayoutParams flex = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row1.addView(prev, flex);
        row1.addView(pick, flex);
        row1.addView(next, flex);
        row1.addView(lockBtn, flex);
        col.addView(row1);

        col.addView(sliderRow("Sensitivity", 20, 300,
                (int) (renderer.beatSensitivity * 100), v -> {
                    renderer.beatSensitivity = v / 100f;
                    queueApplySettings();
                    savePrefs();
                }));
        col.addView(sliderRow("Preset time (sec)", 5, 60,
                (int) renderer.presetDuration, v -> {
                    renderer.presetDuration = v;
                    queueApplySettings();
                    savePrefs();
                }));
        col.addView(sliderRow("Crossfade (sec ×10)", 0, 50,
                (int) (renderer.transitionDuration * 10), v -> {
                    renderer.transitionDuration = v / 10.0;
                    queueApplySettings();
                    savePrefs();
                }));

        LinearLayout row2 = new LinearLayout(this);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        engineBtn = btn("");
        engineBtn.setOnClickListener(v -> cycleEngine());
        updateEngineBtn();
        Button more = btn("Load more presets");
        more.setOnClickListener(v -> pickPresetFolder());
        Button rescan = btn("Rescan");
        rescan.setOnClickListener(v -> {
            rescanCatalog();
            Toast.makeText(this, catalog == null ? "No presets found"
                    : catalog.size() + " presets", Toast.LENGTH_SHORT).show();
        });
        row2.addView(engineBtn, flex);
        row2.addView(more, flex);
        row2.addView(rescan, flex);
        col.addView(row2);
        return col;
    }

    private interface SliderSink { void onValue(int v); }

    private LinearLayout sliderRow(String name, int min, int max, int start, SliderSink sink) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = label(name, 12, 0xFFCFD8DC);
        row.addView(t, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(start - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) sink.onValue(p + min);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        row.addView(sb, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /* ------------------------------------------------------------ behaviour */

    private void enterImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void toggleControls() {
        controlsVisible = !controlsVisible;
        controls.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
        presetLabel.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
        if (controlsVisible) enterImmersive();
    }

    private void updateEngineBtn() {
        if (engineBtn == null) return;
        engineBtn.setText(renderer.useProjectM
                ? "Engine: MilkDrop"
                : "Built-in: " + FallbackVizRenderer.MODE_NAMES[renderer.fallback().mode]);
    }

    private void cycleEngine() {
        if (renderer.useProjectM) {
            renderer.useProjectM = false;
        } else if (renderer.fallback().mode < FallbackVizRenderer.MODE_COUNT - 1) {
            renderer.fallback().mode++;
        } else {
            renderer.fallback().mode = 0;
            if (ProjectMBridge.available()) {
                renderer.useProjectM = true;
                if (engineReady && renderer.handle() == 0) {
                    // Engine was never created this session (started in built-in
                    // after a failure) — recreate the activity to rebuild the surface.
                    recreate();
                    return;
                }
                if (current >= 0) loadInitialPreset();
            }
        }
        updateEngineBtn();
        savePrefs();
    }

    private void rescanCatalog() {
        new Thread(() -> {
            List<PresetCatalog.Entry> list = PresetCatalog.scan(this);
            runOnUiThread(() -> {
                catalog = list;
                if (catalog.isEmpty()) {
                    noteLabel.setText("No .milk presets found — use “Load more presets” "
                            + "or drop files into Downloads/OmniPresets.");
                } else if (current < 0) {
                    current = 0;
                }
                if (renderer.useProjectM && engineReady) loadInitialPreset();
            });
        }, "viz-preset-scan").start();
    }

    private void loadInitialPreset() {
        if (catalog == null || catalog.isEmpty() || current < 0) return;
        loadPresetAt(current, true);
    }

    private void loadPresetAt(int idx, boolean hardCut) {
        if (catalog == null || catalog.isEmpty()) return;
        current = ((idx % catalog.size()) + catalog.size()) % catalog.size();
        PresetCatalog.Entry e = catalog.get(current);
        presetLabel.setText(e.name);
        lastLoadMs = System.currentTimeMillis();
        glView.queueEvent(() -> renderer.loadPreset(e.file.getAbsolutePath(), hardCut));
        SharedPreferences.Editor ed = getSharedPreferences(
                PresetCatalog.PREFS, MODE_PRIVATE).edit();
        ed.putInt("last_preset", current).apply();
    }

    private void stepPreset(int dir, boolean hardCut) {
        if (!renderer.useProjectM) {
            renderer.fallback().mode = (renderer.fallback().mode + dir
                    + FallbackVizRenderer.MODE_COUNT) % FallbackVizRenderer.MODE_COUNT;
            presetLabel.setText(FallbackVizRenderer.MODE_NAMES[renderer.fallback().mode]);
            updateEngineBtn();
            return;
        }
        if (catalog == null || catalog.isEmpty()) return;
        loadPresetAt(current + dir, hardCut);
    }

    private void showPresetPicker() {
        if (!renderer.useProjectM) {
            Toast.makeText(this, "Built-in mode — use Next/Prev to switch modes.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (catalog == null || catalog.isEmpty()) {
            Toast.makeText(this, "No presets yet — load a preset folder first.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[catalog.size()];
        for (int i = 0; i < names.length; i++) names[i] = catalog.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle("Presets (" + names.length + ")")
                .setItems(names, (d, which) -> loadPresetAt(which, false))
                .setNegativeButton("Close", null)
                .show();
    }

    private void pickPresetFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_TREE);
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ_TREE && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            PresetCatalog.saveTreeUri(this, uri);
            Toast.makeText(this, "Importing presets…", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                int n = PresetCatalog.importTree(this, uri);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Imported " + n + " presets", Toast.LENGTH_LONG).show();
                    rescanCatalog();
                });
            }, "viz-preset-import").start();
        }
    }

    /* ------------------------------------------------------------ audio feed */

    private void startFeedIfPermitted() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            noteLabel.setText("Microphone permission needed — the visualizer "
                    + "listens only to this player's own sound.");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD);
            return;
        }
        feed.start(new AudioFeed.Listener() {
            @Override public void onFeedReady() { noteLabel.setText(""); }
            @Override public void onFeedUnavailable(String why) { noteLabel.setText(why); }
        });
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == REQ_RECORD && grants.length > 0
                && grants[0] == PackageManager.PERMISSION_GRANTED) {
            startFeedIfPermitted();
        }
    }

    /* ------------------------------------------------------------ prefs */

    private void loadPrefs() {
        SharedPreferences sp = getSharedPreferences(PresetCatalog.PREFS, MODE_PRIVATE);
        renderer.beatSensitivity = sp.getFloat("sensitivity", 1.0f);
        renderer.presetDuration = sp.getFloat("duration", 20f);
        renderer.transitionDuration = sp.getFloat("transition", 1.5f);
        renderer.presetLocked = sp.getBoolean("locked", false);
        renderer.useProjectM = sp.getBoolean("use_pm", true) && ProjectMBridge.available();
        renderer.fallback().mode = sp.getInt("fb_mode", 0);
        renderer.fallback().sensitivity = renderer.beatSensitivity;
        current = sp.getInt("last_preset", -1);
    }

    private void savePrefs() {
        getSharedPreferences(PresetCatalog.PREFS, MODE_PRIVATE).edit()
                .putFloat("sensitivity", renderer.beatSensitivity)
                .putFloat("duration", (float) renderer.presetDuration)
                .putFloat("transition", (float) renderer.transitionDuration)
                .putBoolean("locked", renderer.presetLocked)
                .putBoolean("use_pm", renderer.useProjectM)
                .putInt("fb_mode", renderer.fallback().mode)
                .apply();
    }

    private void queueApplySettings() {
        glView.queueEvent(renderer::applySettings);
    }

    /* ------------------------------------------------------------ lifecycle */

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersive();
        if (glView != null) glView.onResume();
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(autoAdvance);
        if (feed != null) feed.stop();
        if (glView != null) {
            glView.queueEvent(renderer::destroyEngine);
            glView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(autoAdvance);
        super.onDestroy();
    }
}
