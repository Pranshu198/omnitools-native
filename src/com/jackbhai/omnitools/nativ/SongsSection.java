package com.jackbhai.omnitools.nativ;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.gauravk.audiovisualizer.visualizer.BarVisualizer;
import com.gauravk.audiovisualizer.visualizer.CircleLineVisualizer;
import com.gauravk.audiovisualizer.visualizer.WaveVisualizer;
import com.masoudss.lib.WaveformSeekBar;

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.jackbhai.omnitools.nativ.audio.AccentTheme;
import com.jackbhai.omnitools.nativ.audio.EqController;
import com.jackbhai.omnitools.nativ.audio.EqPresets;
import com.jackbhai.omnitools.nativ.audio.Library;
import com.jackbhai.omnitools.nativ.audio.Lrc;
import com.jackbhai.omnitools.nativ.audio.LrclibClient;
import com.jackbhai.omnitools.nativ.audio.TagView;

import eightbitlab.com.blurview.BlurView;
import linc.com.amplituda.Amplituda;

/**
 * Songs — the native music section.
 *
 * Search runs the PWA's two independent routes CONCURRENTLY (community
 * mirrors raced in waves; the catalogue's own API via the app relay,
 * direct as fallback). The first solid batch paints immediately; when
 * the second route lands its rows are merged in, deduped — the same
 * progressive shape the PWA's 2026-11-14 latency fix uses.
 *
 * Playback goes through a MediaController to PlaybackService (background
 * play, notification + lockscreen). The player bar is real glass (a
 * BlurView over the shell's BlurTarget) and carries the bundle's player
 * kit: a waveform seekbar (massoudss) fed by Amplituda amplitudes of the
 * actual track — computed from a cached copy of the stream, so a song's
 * waveform appears once it has been fetched once and is instant after;
 * until then the seekbar shows a calm placeholder shape and still seeks.
 * A visualizer panel (gauravk95, vendored) renders Bar / Wave / Circle
 * from the player's own audio session. A small Lottie EQ dances while
 * music plays.
 *
 * Sensitive paths (search, playback) stop when Guard reports tampering.
 */
public final class SongsSection {

    private final Activity act;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newFixedThreadPool(3);
    private final List<Song> rows = new ArrayList<>();
    private final List<Song> favourites = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private final SharedPreferences prefs;

    private View root;
    private EditText queryBox;
    private TextView status;
    private TextView favChip;
    private boolean showingFavs = false;
    private int searchGen = 0;

    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private ImageView nowArt;
    private TextView nowTitle, nowArtist, nowPos, nowDur;
    private Button playBtn;
    private WaveformSeekBar wave;
    private LottieDrawable eqDrawable;
    private ImageView eqView;

    private List<Song> currentQueue = new ArrayList<>();
    private Song currentSong = null;
    private String waveForId = "";

    // Visualizer panel
    private FrameLayout vizPanel;
    private BarVisualizer vizBar;
    private WaveVisualizer vizWave;
    private CircleLineVisualizer vizCircle;
    private View[] vizViews;
    private int vizStyle = 0;
    private boolean vizOn = false;
    private TextView vizNote;

    // ---- Phase 2: library / EQ / lyrics / accent (Track C) ----
    private static final int SRC_STREAM = 0, SRC_LIBRARY = 1, SRC_FAVS = 2;
    private int sourceMode = SRC_STREAM;
    private TextView[] sourceChips;
    private TextView rescanChip;
    private LinearLayout groupBar;
    private LinearLayout groupNamesRow;
    private int groupMode = 0;              // 0 Songs, 1 Albums, 2 Artists, 3 Folders
    private String groupFilter = null;
    private List<Library.LocalTrack> library = null;
    private List<Song> libraryVisibleCache = null;
    private boolean scanning = false;

    private EqController eq;
    private AccentTheme accentTheme;
    private SharedPreferences audioPrefs;
    private LinearLayout audioPanel;
    private LinearLayout bandBox;
    private TextView engineLabel;
    private TextView presetLabel;
    private LinearLayout presetRow;
    private int eqUiSession = -1;
    private int eqUiBands = -1;

    private float crossfadeSec = 0f;
    private boolean skipSilence = false;
    private boolean skipSilenceApplied = false;
    private boolean fadingIn = false;
    private float lastVolume = 1f;
    private long sleepDeadlineMs = 0;
    private boolean sleepAtTrackEnd = false;
    private TextView sleepLabel;

    private TextView masterChip, proChip, dynChip, xfLabel, skipChip;

    private LinearLayout lyricsPanel;
    private TextView lyricsState;
    private TextView lyricsBody;
    private ScrollView lyricsScroll;
    private Lrc.Doc lyricsDoc = null;
    private int lyricsGen = 0;
    private int lastLyricIdx = -2;

    public SongsSection(Activity act) {
        this.act = act;
        prefs = act.getSharedPreferences("omnitools_native", Context.MODE_PRIVATE);
        audioPrefs = act.getSharedPreferences(EqController.PREFS, Context.MODE_PRIVATE);
        eq = new EqController(act);
        accentTheme = new AccentTheme(act);
        crossfadeSec = audioPrefs.getFloat("crossfade_sec", 0f);
        skipSilence = audioPrefs.getBoolean("skip_silence", false);
        loadFavourites();
        build();
        connectController();
    }

    public View view() { return root; }

    /* ------------------------------------------------------------ layout */

    private void build() {
        Context c = act;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        Glass.pad(col, c, 14, 12, 14, 8);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Glass.text(c, "Songs", 24, Glass.TEXT, true);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        favChip = Glass.text(c, "Favourites", 13, Glass.TEXT, false);
        favChip.setBackground(Glass.neonPill(c, Glass.NEON_VIOLET));
        Glass.pad(favChip, c, 12, 6, 12, 6);
        favChip.setOnClickListener(v -> {
            Sfx.tap();
            setSource(sourceMode == SRC_FAVS ? SRC_STREAM : SRC_FAVS);
        });
        head.addView(favChip);
        col.addView(head);

        LinearLayout searchRow = new LinearLayout(c);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        srLp.topMargin = (int) Glass.dp(c, 12);
        queryBox = new EditText(c);
        queryBox.setHint("Search songs, artists, films…");
        queryBox.setSingleLine(true);
        queryBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        queryBox.setTextColor(Glass.TEXT);
        queryBox.setHintTextColor(Glass.TEXT_DIM);
        queryBox.setBackground(Glass.panel(c, 14));
        Glass.pad(queryBox, c, 14, 10, 14, 10);
        queryBox.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH) { runSearch(); return true; }
            return false;
        });
        searchRow.addView(queryBox, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button go = new Button(c);
        go.setText("Search");
        go.setTextColor(Glass.TEXT);
        go.setBackground(Glass.neonPill(c, Glass.NEON_CYAN));
        go.setOnClickListener(v -> runSearch());
        LinearLayout.LayoutParams goLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        goLp.leftMargin = (int) Glass.dp(c, 8);
        searchRow.addView(go, goLp);
        col.addView(searchRow, srLp);

        status = Glass.text(c, "Search any song, artist or film", 12.5f, Glass.TEXT_DIM, false);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.topMargin = (int) Glass.dp(c, 8);
        col.addView(status, stLp);

        // Source switch: Streaming / Library / Favourites.
        LinearLayout srcRow = new LinearLayout(c);
        srcRow.setOrientation(LinearLayout.HORIZONTAL);
        srcRow.setGravity(Gravity.CENTER_VERTICAL);
        String[] srcNames = {"Streaming", "Library", "Favourites"};
        sourceChips = new TextView[srcNames.length];
        for (int i = 0; i < srcNames.length; i++) {
            final int mode = i;
            TextView chip = Glass.text(c, srcNames[i], 12.5f, Glass.TEXT, false);
            Glass.pad(chip, c, 12, 5, 12, 5);
            chip.setOnClickListener(v -> { Sfx.tap(); setSource(mode); });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) clp.leftMargin = (int) Glass.dp(c, 8);
            srcRow.addView(chip, clp);
            sourceChips[i] = chip;
        }
        rescanChip = Glass.text(c, "Rescan", 12.5f, Glass.TEXT, false);
        rescanChip.setBackground(Glass.neonPill(c, Glass.NEON_VIOLET));
        Glass.pad(rescanChip, c, 12, 5, 12, 5);
        rescanChip.setVisibility(View.GONE);
        rescanChip.setOnClickListener(v -> { Sfx.tap(); Library.invalidate(); scanLibrary(); });
        LinearLayout.LayoutParams rlp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp2.leftMargin = (int) Glass.dp(c, 8);
        srcRow.addView(rescanChip, rlp2);
        LinearLayout.LayoutParams srcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        srcLp.topMargin = (int) Glass.dp(c, 8);
        col.addView(srcRow, srcLp);
        styleSourceChips();

        // Library grouping bar (Albums / Artists / Folders), library mode only.
        groupBar = new LinearLayout(c);
        groupBar.setOrientation(LinearLayout.VERTICAL);
        groupBar.setVisibility(View.GONE);
        LinearLayout modeRow = new LinearLayout(c);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] groupNames = {"Songs", "Albums", "Artists", "Folders"};
        for (int i = 0; i < groupNames.length; i++) {
            final int gm = i;
            TextView chip = Glass.text(c, groupNames[i], 12f, Glass.TEXT, false);
            chip.setBackground(Glass.neonPill(c, Glass.NEON_CYAN));
            Glass.pad(chip, c, 10, 4, 10, 4);
            chip.setOnClickListener(v -> {
                Sfx.tap();
                groupMode = gm;
                groupFilter = null;
                libraryVisibleCache = null;
                rebuildGroupNames();
                refreshList();
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) clp.leftMargin = (int) Glass.dp(c, 6);
            modeRow.addView(chip, clp);
        }
        groupBar.addView(modeRow);
        groupNamesRow = new LinearLayout(c);
        groupNamesRow.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView gScroll = new HorizontalScrollView(c);
        gScroll.setHorizontalScrollBarEnabled(false);
        gScroll.addView(groupNamesRow);
        LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gLp.topMargin = (int) Glass.dp(c, 6);
        groupBar.addView(gScroll, gLp);
        LinearLayout.LayoutParams gbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gbLp.topMargin = (int) Glass.dp(c, 6);
        col.addView(groupBar, gbLp);

        RecyclerView list = new RecyclerView(c);
        list.setLayoutManager(new LinearLayoutManager(c));
        list.setAdapter(adapter);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listLp.topMargin = (int) Glass.dp(c, 6);
        col.addView(list, listLp);

        col.addView(buildPlayerBar(c));
        root = col;
    }

    private View buildPlayerBar(Context c) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.VERTICAL);
        Glass.pad(bar, c, 12, 10, 12, 10);

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        nowArt = new ImageView(c);
        nowArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        top.addView(nowArt, new LinearLayout.LayoutParams(
                (int) Glass.dp(c, 46), (int) Glass.dp(c, 46)));
        LinearLayout meta = new LinearLayout(c);
        meta.setOrientation(LinearLayout.VERTICAL);
        nowTitle = Glass.text(c, "Nothing playing", 14.5f, Glass.TEXT, true);
        nowTitle.setSingleLine(true);
        nowArtist = Glass.text(c, "", 12.5f, Glass.TEXT_DIM, false);
        nowArtist.setSingleLine(true);
        meta.addView(nowTitle);
        meta.addView(nowArtist);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        metaLp.leftMargin = (int) Glass.dp(c, 10);
        top.addView(meta, metaLp);

        eqView = new ImageView(c);
        LinearLayout.LayoutParams eqLp = new LinearLayout.LayoutParams(
                (int) Glass.dp(c, 30), (int) Glass.dp(c, 30));
        eqLp.rightMargin = (int) Glass.dp(c, 4);
        top.addView(eqView, eqLp);
        loadEqAnimation();

        Button prev = smallBtn(c, "Prev");
        prev.setOnClickListener(v -> { if (controller != null) controller.seekToPrevious(); });
        playBtn = smallBtn(c, "Play");
        playBtn.setOnClickListener(v -> {
            if (controller == null) return;
            if (controller.isPlaying()) { Sfx.pause(); controller.pause(); }
            else { Sfx.play_(); controller.play(); }
        });
        Button next = smallBtn(c, "Next");
        next.setOnClickListener(v -> { if (controller != null) controller.seekToNext(); });
        top.addView(prev);
        top.addView(playBtn);
        top.addView(next);
        bar.addView(top);

        // Waveform seekbar row.
        LinearLayout seekRow = new LinearLayout(c);
        seekRow.setOrientation(LinearLayout.HORIZONTAL);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        nowPos = Glass.text(c, "0:00", 11, Glass.TEXT_DIM, false);
        nowDur = Glass.text(c, "0:00", 11, Glass.TEXT_DIM, false);
        wave = new WaveformSeekBar(c, null, 0);
        wave.setMaxProgress(100f);
        wave.setProgress(0f);
        wave.setWaveProgressColor(Glass.NEON_CYAN);
        wave.setWaveBackgroundColor(Color.argb(90, 200, 215, 235));
        wave.setSample(placeholderSample());
        wave.setOnProgressChanged((bar2, progress, fromUser) -> {
            if (fromUser && controller != null && controller.getDuration() > 0) {
                controller.seekTo((long) (controller.getDuration() * progress / 100.0));
            }
        });
        seekRow.addView(nowPos);
        LinearLayout.LayoutParams waveLp = new LinearLayout.LayoutParams(0,
                (int) Glass.dp(c, 36), 1f);
        waveLp.leftMargin = (int) Glass.dp(c, 8);
        waveLp.rightMargin = (int) Glass.dp(c, 8);
        seekRow.addView(wave, waveLp);
        seekRow.addView(nowDur);
        LinearLayout.LayoutParams seekRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        seekRowLp.topMargin = (int) Glass.dp(c, 4);
        bar.addView(seekRow, seekRowLp);

        // Visualizer toggle row + panel.
        LinearLayout vizRow = new LinearLayout(c);
        vizRow.setOrientation(LinearLayout.HORIZONTAL);
        vizRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView vizChip = Glass.text(c, "Visualizer", 12.5f, Glass.TEXT, false);
        vizChip.setBackground(Glass.neonPill(c, Glass.NEON_VIOLET));
        Glass.pad(vizChip, c, 12, 5, 12, 5);
        vizChip.setOnClickListener(v -> toggleVisualizer());
        vizRow.addView(vizChip);
        TextView styleChip = Glass.text(c, "Style: Bar", 12.5f, Glass.TEXT, false);
        styleChip.setBackground(Glass.neonPill(c, Glass.NEON_CYAN));
        Glass.pad(styleChip, c, 12, 5, 12, 5);
        styleChip.setOnClickListener(v -> {
            vizStyle = (vizStyle + 1) % 3;
            styleChip.setText("Style: " + new String[]{"Bar", "Wave", "Circle"}[vizStyle]);
            Sfx.tap();
            applyVizStyle();
        });
        LinearLayout.LayoutParams styleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        styleLp.leftMargin = (int) Glass.dp(c, 8);
        vizRow.addView(styleChip, styleLp);
        // Batch 3 Track C: MilkDrop (projectM) fullscreen visualizations.
        TextView milkChip = Glass.text(c, "MilkDrop", 12.5f, Glass.TEXT, false);
        milkChip.setBackground(Glass.neonPill(c, Glass.NEON_VIOLET));
        Glass.pad(milkChip, c, 12, 5, 12, 5);
        milkChip.setOnClickListener(v -> {
            Sfx.tap();
            act.startActivity(new android.content.Intent(
                    act, com.jackbhai.omnitools.nativ.viz.VizActivity.class));
        });
        LinearLayout.LayoutParams milkLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        milkLp.leftMargin = (int) Glass.dp(c, 8);
        vizRow.addView(milkChip, milkLp);
        vizNote = Glass.text(c, "", 11.5f, Glass.TEXT_DIM, false);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        noteLp.leftMargin = (int) Glass.dp(c, 10);
        vizRow.addView(vizNote, noteLp);
        LinearLayout.LayoutParams vizRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vizRowLp.topMargin = (int) Glass.dp(c, 6);
        bar.addView(vizRow, vizRowLp);

        vizPanel = new FrameLayout(c);
        vizPanel.setVisibility(View.GONE);
        vizBar = new BarVisualizer(c);
        vizWave = new WaveVisualizer(c);
        vizCircle = new CircleLineVisualizer(c);
        vizViews = new View[]{vizBar, vizWave, vizCircle};
        for (View vv : vizViews) {
            vv.setVisibility(View.GONE);
            vizPanel.addView(vv, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) Glass.dp(c, 84));
        panelLp.topMargin = (int) Glass.dp(c, 6);
        bar.addView(vizPanel, panelLp);

        // Audio (EQ / effects / playback / accent) + Lyrics toggles.
        LinearLayout audioRow = new LinearLayout(c);
        audioRow.setOrientation(LinearLayout.HORIZONTAL);
        audioRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView audioChip = Glass.text(c, "Audio", 12.5f, Glass.TEXT, false);
        audioChip.setBackground(Glass.neonPill(c, Glass.NEON_CYAN));
        Glass.pad(audioChip, c, 12, 5, 12, 5);
        audioChip.setOnClickListener(v -> {
            Sfx.tap();
            audioPanel.setVisibility(audioPanel.getVisibility() == View.VISIBLE
                    ? View.GONE : View.VISIBLE);
            if (audioPanel.getVisibility() == View.VISIBLE) refreshEqUi();
        });
        audioRow.addView(audioChip);
        TextView lyricsChip = Glass.text(c, "Lyrics", 12.5f, Glass.TEXT, false);
        lyricsChip.setBackground(Glass.neonPill(c, Glass.NEON_VIOLET));
        Glass.pad(lyricsChip, c, 12, 5, 12, 5);
        lyricsChip.setOnClickListener(v -> {
            Sfx.tap();
            lyricsPanel.setVisibility(lyricsPanel.getVisibility() == View.VISIBLE
                    ? View.GONE : View.VISIBLE);
        });
        LinearLayout.LayoutParams lyrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lyrLp.leftMargin = (int) Glass.dp(c, 8);
        audioRow.addView(lyricsChip, lyrLp);
        sleepLabel = Glass.text(c, "", 11.5f, Glass.TEXT_DIM, false);
        LinearLayout.LayoutParams slLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        slLp.leftMargin = (int) Glass.dp(c, 10);
        audioRow.addView(sleepLabel, slLp);
        LinearLayout.LayoutParams audioRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        audioRowLp.topMargin = (int) Glass.dp(c, 6);
        bar.addView(audioRow, audioRowLp);

        audioPanel = buildAudioPanel(c);
        bar.addView(audioPanel);
        lyricsPanel = buildLyricsPanel(c);
        bar.addView(lyricsPanel);

        // The bar is real glass when the shell's BlurTarget is available.
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.topMargin = (int) Glass.dp(c, 8);
        if (MainActivity.blurTarget != null) {
            bar.setBackground(Glass.panel(c, 18));
            BlurView bv = new BlurView(c);
            bv.addView(bar);
            bv.setupWith(MainActivity.blurTarget)
                    .setBlurRadius(16f)
                    .setOverlayColor(Color.argb(24, 12, 18, 30));
            bv.setLayoutParams(barLp);
            return bv;
        }
        bar.setBackground(Glass.panelStrong(c, 18));
        bar.setLayoutParams(barLp);
        return bar;
    }

    private Button smallBtn(Context c, String label) {
        Button b = new Button(c);
        b.setText(label);
        b.setTextSize(12.5f);
        b.setTextColor(Glass.TEXT);
        b.setBackground(Glass.neonPill(c, Glass.NEON_CYAN));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = (int) Glass.dp(c, 6);
        b.setLayoutParams(lp);
        return b;
    }

    private void loadEqAnimation() {
        eqDrawable = new LottieDrawable();
        LottieCompositionFactory.fromRawRes(act, R.raw.lottie_eq)
                .addListener(comp -> {
                    eqDrawable.setComposition(comp);
                    eqDrawable.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                    eqView.setImageDrawable(eqDrawable);
                })
                .addFailureListener(e -> { /* animation is garnish; absence is fine */ });
    }

    /* ------------------------------------------------------------ visualizer */

    private void toggleVisualizer() {
        vizOn = !vizOn;
        vizPanel.setVisibility(vizOn ? View.VISIBLE : View.GONE);
        if (vizOn) {
            Sfx.open();
            if (act.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                vizNote.setText("Microphone permission needed — the visualizer "
                        + "listens only to this player's own sound.");
                act.requestPermissions(new String[]{
                        android.Manifest.permission.RECORD_AUDIO}, 9);
                return;
            }
            attachVisualizer(0);
        } else {
            Sfx.close();
            vizNote.setText("");
        }
    }

    private void attachVisualizer(int tries) {
        androidx.media3.exoplayer.ExoPlayer p = PlaybackService.sharedPlayer();
        if (p == null) {
            if (tries < 16) ui.postDelayed(() -> attachVisualizer(tries + 1), 300);
            return;
        }
        try {
            int session = p.getAudioSessionId();
            for (com.gauravk.audiovisualizer.base.BaseVisualizer v :
                    new com.gauravk.audiovisualizer.base.BaseVisualizer[]{vizBar, vizWave, vizCircle}) {
                v.setColor(Glass.NEON_CYAN);
                v.setAudioSessionId(session);
            }
            vizNote.setText("");
            applyVizStyle();
        } catch (Exception e) {
            vizNote.setText("Visualizer unavailable on this device.");
        }
    }

    private void applyVizStyle() {
        if (vizViews == null) return;
        for (int i = 0; i < vizViews.length; i++) {
            vizViews[i].setVisibility(vizOn && i == vizStyle ? View.VISIBLE : View.GONE);
        }
    }

    /* ------------------------------------------------------------ search */

    private void runSearch() {
        if (Guard.sensitiveBlocked(act)) {
            status.setText("Unavailable in this copy of the app.");
            return;
        }
        Sfx.tap();
        final String q = queryBox.getText().toString().trim();
        if (q.isEmpty()) return;
        if (sourceMode != SRC_STREAM) setSource(SRC_STREAM);
        final int gen = ++searchGen;
        status.setText("Searching…");
        final List<Song> merged = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        final boolean[] painted = {false};

        Runnable paint = () -> ui.post(() -> {
            if (gen != searchGen) return;
            synchronized (merged) {
                rows.clear();
                rows.addAll(merged);
            }
            adapter.notifyDataSetChanged();
        });

        java.util.function.Consumer<List<Song>> accept = batch -> {
            if (batch == null || batch.isEmpty()) return;
            boolean added = false;
            synchronized (merged) {
                for (Song s : batch) {
                    String key = !s.id.isEmpty() ? "id:" + s.id
                            : "ta:" + (s.title + "|" + s.artist).toLowerCase(Locale.US);
                    if (seen.add(key)) { merged.add(s); added = true; }
                }
            }
            if (!added) return;
            final boolean first = !painted[0];
            painted[0] = true;
            paint.run();
            ui.post(() -> {
                if (gen != searchGen) return;
                status.setText(first
                        ? rows.size() + " songs · finding more…"
                        : rows.size() + " songs");
            });
        };

        net.execute(() -> {
            try { accept.accept(SaavnApi.searchMirrors(q, 20)); }
            catch (Exception e) {
                ui.post(() -> { if (gen == searchGen && !painted[0]) status.setText("Searching other sources…"); });
            }
            finishSearch(gen, painted);
        });
        net.execute(() -> {
            try { accept.accept(SaavnApi.searchOfficial(q, 20)); }
            catch (Exception ignored) {}
            finishSearch(gen, painted);
        });
    }

    private final java.util.concurrent.atomic.AtomicInteger legsDone =
            new java.util.concurrent.atomic.AtomicInteger();
    private volatile int legsGen = -1;

    private void finishSearch(int gen, boolean[] painted) {
        synchronized (legsDone) {
            if (legsGen != gen) { legsGen = gen; legsDone.set(0); }
            if (legsDone.incrementAndGet() < 2) return;
        }
        ui.post(() -> {
            if (gen != searchGen) return;
            if (!painted[0]) status.setText("No playable songs found. Check the connection and try again.");
            else status.setText(rows.size() + " songs");
        });
    }

    /* ------------------------------------------------------------ favourites */

    private void loadFavourites() {
        favourites.clear();
        try {
            JSONArray arr = new JSONArray(prefs.getString("favourites", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                Song s = Song.fromJson(arr.getJSONObject(i));
                if (s != null) favourites.add(s);
            }
        } catch (Exception ignored) {}
    }

    /** Same track = same catalogue id when both have one, else same
     *  title + artist (mirrors can disagree on ids for one recording). */
    static boolean sameTrack(Song a, Song b) {
        if (a == null || b == null) return false;
        if (!a.id.isEmpty() && !b.id.isEmpty()) return a.id.equals(b.id);
        return a.title.equalsIgnoreCase(b.title)
                && a.artist.equalsIgnoreCase(b.artist);
    }

    private boolean isFav(Song s) {
        for (Song f : favourites) if (sameTrack(f, s)) return true;
        return false;
    }

    private void toggleFav(Song s) {
        boolean removed = favourites.removeIf(f -> sameTrack(f, s));
        if (!removed) {
            favourites.add(s);
            Sfx.favourite();
        } else {
            Sfx.unfavourite();
        }
        try {
            JSONArray arr = new JSONArray();
            for (Song f : favourites) arr.put(f.toJson());
            prefs.edit().putString("favourites", arr.toString()).apply();
        } catch (Exception ignored) {}
        adapter.notifyDataSetChanged();
    }

    private void refreshList() { adapter.notifyDataSetChanged(); }
    private List<Song> visible() {
        if (sourceMode == SRC_FAVS) return favourites;
        if (sourceMode == SRC_LIBRARY) return librarySongs();
        return rows;
    }

    /* ------------------------------------------------------------ playback */

    private void connectController() {
        SessionToken token = new SessionToken(act,
                new ComponentName(act, PlaybackService.class));
        controllerFuture = new MediaController.Builder(act, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                controller.addListener(new Player.Listener() {
                    @Override public void onIsPlayingChanged(boolean playing) {
                        playBtn.setText(playing ? "Pause" : "Play");
                        if (eqDrawable != null) {
                            if (playing) eqDrawable.playAnimation();
                            else eqDrawable.pauseAnimation();
                        }
                    }
                    @Override public void onMediaItemTransition(@Nullable MediaItem item, int reason) {
                        if (sleepAtTrackEnd
                                && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                            sleepAtTrackEnd = false;
                            if (sleepLabel != null) sleepLabel.setText("");
                            controller.pause();
                        }
                        fadingIn = crossfadeSec > 0;
                        updateNowPlaying();
                    }
                });
                updateNowPlaying();
                ui.post(tick);
            } catch (Exception ignored) {}
        }, MoreExecutors.directExecutor());
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (controller != null) {
                long pos = Math.max(0, controller.getCurrentPosition());
                long dur = Math.max(0, controller.getDuration());
                nowPos.setText(fmt(pos));
                nowDur.setText(fmt(dur));
                if (wave != null) {
                    wave.setProgress(dur > 0 ? 100f * pos / dur : 0f);
                }
                audioTick(pos, dur);
            }
            ui.postDelayed(this, 500);
        }
    };

    static String fmt(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private void updateNowPlaying() {
        if (controller == null) return;
        MediaItem item = controller.getCurrentMediaItem();
        if (item == null) return;
        MediaMetadata md = item.mediaMetadata;
        nowTitle.setText(md.title != null ? md.title : "Playing");
        nowArtist.setText(md.artist != null ? md.artist : "");
        if (md.artworkUri != null) Img.load(md.artworkUri.toString(), nowArt);
        playBtn.setText(controller.isPlaying() ? "Pause" : "Play");
        int idx = controller.getCurrentMediaItemIndex();
        if (idx >= 0 && idx < currentQueue.size()) {
            Song s = currentQueue.get(idx);
            if (currentSong == null || !sameTrack(currentSong, s)) {
                currentSong = s;
                ensureWaveform(s);
                onTrackChangedForAudio(s);
            }
        }
    }

    private void playFrom(List<Song> list, int index) {
        if (Guard.sensitiveBlocked(act)) {
            status.setText("Unavailable in this copy of the app.");
            return;
        }
        if (controller == null) {
            status.setText("Player is still starting — try again in a moment.");
            return;
        }
        List<MediaItem> items = new ArrayList<>();
        for (Song s : list) {
            MediaMetadata md = new MediaMetadata.Builder()
                    .setTitle(s.title)
                    .setArtist(s.artist)
                    .setArtworkUri(s.art.isEmpty() ? null : Uri.parse(s.art))
                    .build();
            items.add(new MediaItem.Builder()
                    .setUri(s.stream)
                    .setMediaId(s.id)
                    .setMediaMetadata(md)
                    .build());
        }
        currentQueue = new ArrayList<>(list);
        controller.setMediaItems(items, index, 0);
        controller.prepare();
        controller.play();
        Sfx.play_();
    }

    /* ------------------------------------------------------------ waveform */

    private static int[] placeholderSample() {
        int[] s = new int[72];
        for (int i = 0; i < s.length; i++) {
            s[i] = 26 + (int) (18 * Math.sin(i * 0.35)) + (i % 5 == 0 ? 10 : 0);
        }
        return s;
    }

    private File waveDir() {
        File d = new File(act.getCacheDir(), "waveforms");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static String waveKey(Song s) {
        String base = !s.id.isEmpty() ? s.id : (s.title + s.artist);
        return base.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    /** Real waveform for a song: cached amplitudes win; otherwise the
     *  stream is fetched once into the cache and Amplituda (native
     *  FFmpeg) measures it. Until then the placeholder stays — the
     *  seekbar remains fully usable throughout. */
    private void ensureWaveform(Song s) {
        final String key = waveKey(s);
        if (key.equals(waveForId)) return;
        waveForId = key;
        final File ampsFile = new File(waveDir(), key + ".json");
        if (ampsFile.exists()) {
            net.execute(() -> {
                int[] amps = readAmps(ampsFile);
                if (amps != null) ui.post(() -> {
                    if (key.equals(waveForId) && wave != null) wave.setSample(amps);
                });
            });
            return;
        }
        if (wave != null) wave.setSample(placeholderSample());
        // Local library tracks: no stream download for a waveform — the
        // placeholder shape stays and the seekbar remains fully usable.
        if (s.stream == null || !s.stream.startsWith("http")) return;
        final File audioFile = new File(waveDir(), key + ".audio");
        net.execute(() -> {
            try {
                if (!audioFile.exists()) downloadToFile(s.stream, audioFile);
                if (!audioFile.exists() || audioFile.length() < 4096) return;
                new Amplituda(act).processAudio(audioFile.getAbsolutePath()).get(
                        result -> {
                            int[] amps = downsample(result.amplitudesAsList(), 72);
                            writeAmps(ampsFile, amps);
                            ui.post(() -> {
                                if (key.equals(waveForId) && wave != null) wave.setSample(amps);
                            });
                        },
                        err -> { /* waveform is best-effort garnish */ });
            } catch (Exception ignored) {}
        });
    }

    private static void downloadToFile(String url, File out) throws Exception {
        HttpURLConnection cn = (HttpURLConnection) new URL(url).openConnection();
        cn.setConnectTimeout(12000);
        cn.setReadTimeout(30000);
        cn.setRequestProperty("User-Agent", Net.UA);
        cn.setInstanceFollowRedirects(true);
        try (InputStream in = cn.getInputStream();
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[32 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 24L * 1024 * 1024) break;
                fos.write(buf, 0, n);
            }
        } finally {
            cn.disconnect();
        }
    }

    private static int[] downsample(List<Integer> src, int bars) {
        int[] out = new int[bars];
        if (src == null || src.isEmpty()) return placeholderSample();
        int chunk = Math.max(1, src.size() / bars);
        int max = 1;
        for (int b = 0; b < bars; b++) {
            int m = 0;
            for (int i = b * chunk; i < Math.min(src.size(), (b + 1) * chunk); i++) {
                m = Math.max(m, Math.abs(src.get(i)));
            }
            out[b] = m;
            max = Math.max(max, m);
        }
        for (int b = 0; b < bars; b++) out[b] = Math.max(4, out[b] * 100 / max);
        return out;
    }

    private static int[] readAmps(File f) {
        try {
            JSONArray arr = new JSONArray(Net.readFile(f));
            int[] out = new int[arr.length()];
            for (int i = 0; i < out.length; i++) out[i] = arr.getInt(i);
            return out;
        } catch (Exception e) { return null; }
    }

    private static void writeAmps(File f, int[] amps) {
        try {
            JSONArray arr = new JSONArray();
            for (int a : amps) arr.put(a);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(arr.toString().getBytes("UTF-8"));
            }
        } catch (Exception ignored) {}
    }

    /* ------------------------------------------------------------ list adapter */

    private final class VH extends RecyclerView.ViewHolder {
        final ImageView art;
        final TextView title, sub;
        final Button fav;
        VH(View v) {
            super(v);
            art = v.findViewById(1);
            title = v.findViewById(2);
            sub = v.findViewById(3);
            fav = v.findViewById(4);
        }
    }

    private final class Adapter extends RecyclerView.Adapter<VH> {
        @Override public VH onCreateViewHolder(ViewGroup parent, int vt) {
            Context c = parent.getContext();
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Glass.panel(c, 14));
            Glass.pad(row, c, 10, 8, 10, 8);
            RecyclerView.LayoutParams rlp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = (int) Glass.dp(c, 8);
            row.setLayoutParams(rlp);

            ImageView art = new ImageView(c);
            art.setId(1);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            row.addView(art, new LinearLayout.LayoutParams(
                    (int) Glass.dp(c, 48), (int) Glass.dp(c, 48)));
            LinearLayout meta = new LinearLayout(c);
            meta.setOrientation(LinearLayout.VERTICAL);
            TextView t = Glass.text(c, "", 14.5f, Glass.TEXT, true);
            t.setId(2);
            t.setSingleLine(true);
            TextView s = Glass.text(c, "", 12.5f, Glass.TEXT_DIM, false);
            s.setId(3);
            s.setSingleLine(true);
            meta.addView(t);
            meta.addView(s);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            mlp.leftMargin = (int) Glass.dp(c, 10);
            row.addView(meta, mlp);
            Button fav = new Button(c);
            fav.setId(4);
            fav.setTextSize(12);
            fav.setTextColor(Glass.TEXT);
            row.addView(fav, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new VH(row);
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            List<Song> src = visible();
            final Song s = src.get(pos);
            final int idx = pos;
            h.title.setText(s.title);
            h.sub.setText(s.artist + (s.album.isEmpty() ? "" : " · " + s.album));
            Img.load(s.art, h.art);
            boolean fav = isFav(s);
            h.fav.setText(fav ? "Saved" : "Save");
            h.fav.setBackground(Glass.neonPill(act, fav ? Glass.NEON_VIOLET : Glass.NEON_CYAN));
            h.fav.setOnClickListener(v -> toggleFav(s));
            h.itemView.setOnClickListener(v -> playFrom(new ArrayList<>(visible()), idx));
            if (sourceMode == SRC_LIBRARY) {
                h.itemView.setOnLongClickListener(v -> { showTagSheet(s); return true; });
            } else {
                h.itemView.setOnLongClickListener(null);
            }
        }

        @Override public int getItemCount() { return visible().size(); }
    }

    /* ============================================================
     * Phase 2 (Track C): local library, EQ + effects, playback
     * settings, lyrics, tags, dynamic accent.
     * ============================================================ */

    private TextView mkChip(Context c, String label, int neon) {
        TextView chip = Glass.text(c, label, 12f, Glass.TEXT, false);
        chip.setBackground(Glass.neonPill(c, neon));
        Glass.pad(chip, c, 10, 4, 10, 4);
        return chip;
    }

    private LinearLayout.LayoutParams chipLp(Context c, boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!first) lp.leftMargin = (int) Glass.dp(c, 6);
        return lp;
    }

    /* ------------------------------------------------------------ sources + library */

    private void setSource(int mode) {
        sourceMode = mode;
        showingFavs = mode == SRC_FAVS;
        styleSourceChips();
        boolean lib = mode == SRC_LIBRARY;
        groupBar.setVisibility(lib ? View.VISIBLE : View.GONE);
        rescanChip.setVisibility(lib ? View.VISIBLE : View.GONE);
        if (mode == SRC_FAVS) {
            status.setText(favourites.size() + " saved songs");
        } else if (lib) {
            ensureLibrary();
        } else {
            status.setText(rows.isEmpty()
                    ? "Search any song, artist or film" : rows.size() + " songs");
        }
        refreshList();
    }

    private void styleSourceChips() {
        if (sourceChips == null) return;
        for (int i = 0; i < sourceChips.length; i++) {
            sourceChips[i].setBackground(Glass.neonPill(act,
                    i == sourceMode ? Glass.ACCENT : Glass.NEON_VIOLET));
        }
        if (favChip != null) {
            favChip.setBackground(Glass.neonPill(act,
                    sourceMode == SRC_FAVS ? Glass.ACCENT : Glass.NEON_VIOLET));
        }
    }

    private boolean libraryGranted() {
        return act.checkSelfPermission(Library.permission())
                == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureLibrary() {
        if (libraryGranted()) {
            if (library == null && Library.cached() != null) {
                library = Library.cached();
                libraryVisibleCache = null;
                rebuildGroupNames();
                refreshList();
                status.setText(library.size() + " local songs");
            } else if (library == null) {
                scanLibrary();
            } else {
                status.setText(library.size() + " local songs");
            }
            return;
        }
        status.setText("Storage permission is needed to list the songs on this device.");
        act.requestPermissions(new String[]{Library.permission()}, 77);
        watchLibraryPermission(0);
    }

    private void watchLibraryPermission(int tries) {
        if (sourceMode != SRC_LIBRARY) return;
        if (libraryGranted()) { scanLibrary(); return; }
        if (tries < 10) ui.postDelayed(() -> watchLibraryPermission(tries + 1), 900);
    }

    private void scanLibrary() {
        if (scanning) return;
        if (!libraryGranted()) { ensureLibrary(); return; }
        scanning = true;
        status.setText("Scanning device library…");
        net.execute(() -> {
            final List<Library.LocalTrack> all = Library.scan(act);
            ui.post(() -> {
                scanning = false;
                library = all;
                libraryVisibleCache = null;
                rebuildGroupNames();
                refreshList();
                if (sourceMode == SRC_LIBRARY) {
                    status.setText(all.size() + " local songs");
                }
            });
        });
    }

    private List<Song> librarySongs() {
        if (libraryVisibleCache != null) return libraryVisibleCache;
        List<Song> out = new ArrayList<>();
        if (library != null) {
            List<Library.LocalTrack> src = library;
            if (groupMode > 0) {
                java.util.Map<String, List<Library.LocalTrack>> groups =
                        groupMode == 1 ? Library.byAlbum(library)
                                : groupMode == 2 ? Library.byArtist(library)
                                : Library.byFolder(library);
                if (groupFilter != null) {
                    List<Library.LocalTrack> g = groups.get(groupFilter);
                    src = g != null ? g : new ArrayList<>();
                }
            }
            for (Library.LocalTrack t : src) out.add(t.toSong());
        }
        libraryVisibleCache = out;
        return out;
    }

    private void rebuildGroupNames() {
        if (groupNamesRow == null) return;
        groupNamesRow.removeAllViews();
        if (library == null || groupMode == 0) return;
        final java.util.Map<String, List<Library.LocalTrack>> groups =
                groupMode == 1 ? Library.byAlbum(library)
                        : groupMode == 2 ? Library.byArtist(library)
                        : Library.byFolder(library);
        TextView all = mkChip(act, "All", Glass.NEON_VIOLET);
        all.setOnClickListener(v -> {
            groupFilter = null;
            libraryVisibleCache = null;
            refreshList();
        });
        groupNamesRow.addView(all, chipLp(act, true));
        for (final String name : groups.keySet()) {
            TextView chip = mkChip(act, name + " (" + groups.get(name).size() + ")",
                    Glass.NEON_CYAN);
            chip.setOnClickListener(v -> {
                groupFilter = name;
                libraryVisibleCache = null;
                refreshList();
            });
            groupNamesRow.addView(chip, chipLp(act, false));
        }
    }

    private void showTagSheet(final Song s) {
        Library.LocalTrack found = null;
        if (library != null && s.id.startsWith("local:")) {
            long id = -1;
            try { id = Long.parseLong(s.id.substring(6)); } catch (Exception ignored) {}
            for (Library.LocalTrack t : library) if (t.id == id) { found = t; break; }
        }
        if (found == null) {
            status.setText("Tags are available for local library files.");
            return;
        }
        final Library.LocalTrack track = found;
        status.setText("Reading tags…");
        net.execute(() -> {
            final TagView.TagInfo info = TagView.read(new File(track.path));
            ui.post(() -> new AlertDialog.Builder(act)
                    .setTitle("Tags — " + track.title)
                    .setMessage((info.ok ? info.describe() : "No readable tags in this file.")
                            + "\n\nTag editing is view-only: these files belong to the "
                            + "device media library, and OmniTools will not risk "
                            + "rewriting your only copy.")
                    .setPositiveButton("Close", null)
                    .show());
        });
    }

    /* ------------------------------------------------------------ audio panel */

    private LinearLayout buildAudioPanel(Context c) {
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setVisibility(View.GONE);
        Glass.pad(panel, c, 2, 8, 2, 2);

        LinearLayout masterRow = new LinearLayout(c);
        masterRow.setOrientation(LinearLayout.HORIZONTAL);
        masterRow.setGravity(Gravity.CENTER_VERTICAL);
        masterChip = mkChip(c, "EQ: On", Glass.NEON_CYAN);
        masterChip.setOnClickListener(v -> { eq.setMasterOn(!eq.isMasterOn()); refreshEqUi(); });
        masterRow.addView(masterChip);
        proChip = mkChip(c, "Pro EQ: Off", Glass.NEON_VIOLET);
        proChip.setOnClickListener(v -> {
            if (!eq.isProAvailable()) {
                engineLabel.setText("Pro EQ needs Android 9 (API 28) or newer — "
                        + "the standard Equalizer is live.");
                return;
            }
            eq.setProRequested(!eq.isProRequested());
            refreshEqUi();
        });
        masterRow.addView(proChip, chipLp(c, false));
        panel.addView(masterRow);

        engineLabel = Glass.text(c, "", 11.5f, Glass.TEXT_DIM, false);
        LinearLayout.LayoutParams elLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elLp.topMargin = (int) Glass.dp(c, 4);
        panel.addView(engineLabel, elLp);

        presetLabel = Glass.text(c, "Preset: Flat", 12.5f, Glass.TEXT, true);
        LinearLayout.LayoutParams plLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plLp.topMargin = (int) Glass.dp(c, 8);
        panel.addView(presetLabel, plLp);

        presetRow = new LinearLayout(c);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView pScroll = new HorizontalScrollView(c);
        pScroll.setHorizontalScrollBarEnabled(false);
        pScroll.addView(presetRow);
        panel.addView(pScroll);

        bandBox = new LinearLayout(c);
        bandBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bbLp.topMargin = (int) Glass.dp(c, 6);
        panel.addView(bandBox, bbLp);

        panel.addView(effectSlider(c, "Bass boost", eq.bassStrength(), v -> {
            eq.setBassStrength(v * 10);
        }));
        panel.addView(effectSlider(c, "Virtualizer", eq.virtStrength(), v -> {
            eq.setVirtStrength(v * 10);
        }));

        TextView revLabel = Glass.text(c, "Reverb", 12.5f, Glass.TEXT, true);
        panel.addView(revLabel, plLp);
        LinearLayout revRow = new LinearLayout(c);
        revRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < EqController.REVERB_NAMES.length; i++) {
            final int idx = i;
            TextView chip = mkChip(c, EqController.REVERB_NAMES[i], Glass.NEON_CYAN);
            chip.setOnClickListener(v -> eq.setReverbIndex(idx));
            revRow.addView(chip, chipLp(c, i == 0));
        }
        HorizontalScrollView rScroll = new HorizontalScrollView(c);
        rScroll.setHorizontalScrollBarEnabled(false);
        rScroll.addView(revRow);
        panel.addView(rScroll);

        xfLabel = Glass.text(c, crossfadeLabel(), 12.5f, Glass.TEXT, true);
        panel.addView(xfLabel, plLp);
        SeekBar xfBar = new SeekBar(c);
        xfBar.setMax(24);
        xfBar.setProgress(Math.round(crossfadeSec * 2));
        xfBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                if (!user) return;
                crossfadeSec = p / 2f;
                audioPrefs.edit().putFloat("crossfade_sec", crossfadeSec).apply();
                xfLabel.setText(crossfadeLabel());
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        panel.addView(xfBar);

        skipChip = mkChip(c, "Skip silence: Off", Glass.NEON_VIOLET);
        skipChip.setOnClickListener(v -> {
            skipSilence = !skipSilence;
            audioPrefs.edit().putBoolean("skip_silence", skipSilence).apply();
            skipSilenceApplied = false;
            applySkipSilence();
            refreshEqUi();
        });
        LinearLayout.LayoutParams skLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        skLp.topMargin = (int) Glass.dp(c, 6);
        panel.addView(skipChip, skLp);

        TextView sleepTitle = Glass.text(c, "Sleep timer", 12.5f, Glass.TEXT, true);
        panel.addView(sleepTitle, plLp);
        LinearLayout sleepRow = new LinearLayout(c);
        sleepRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] sleepNames = {"Off", "10", "20", "30", "45", "60", "End of track"};
        for (int i = 0; i < sleepNames.length; i++) {
            final String name = sleepNames[i];
            TextView chip = mkChip(c, name, Glass.NEON_CYAN);
            chip.setOnClickListener(v -> setSleep(name));
            sleepRow.addView(chip, chipLp(c, i == 0));
        }
        HorizontalScrollView sScroll = new HorizontalScrollView(c);
        sScroll.setHorizontalScrollBarEnabled(false);
        sScroll.addView(sleepRow);
        panel.addView(sScroll);

        TextView accTitle = Glass.text(c, "Accent colour", 12.5f, Glass.TEXT, true);
        panel.addView(accTitle, plLp);
        dynChip = mkChip(c, "Dynamic accent: On", Glass.NEON_CYAN);
        dynChip.setOnClickListener(v -> {
            accentTheme.setDynamicOn(!accentTheme.isDynamicOn());
            refreshEqUi();
        });
        panel.addView(dynChip, skLp);
        LinearLayout accRow = new LinearLayout(c);
        accRow.setOrientation(LinearLayout.HORIZONTAL);
        for (final AccentTheme.Choice choice : AccentTheme.MANUAL) {
            TextView chip = mkChip(c, choice.name, choice.color);
            chip.setOnClickListener(v -> {
                accentTheme.setManualColor(choice.color);
                status.setText("Accent: " + choice.name
                        + " (manual — turn Dynamic on for album-art colours)");
            });
            accRow.addView(chip, chipLp(c, accRow.getChildCount() == 0));
        }
        HorizontalScrollView aScroll = new HorizontalScrollView(c);
        aScroll.setHorizontalScrollBarEnabled(false);
        aScroll.addView(accRow);
        panel.addView(aScroll);

        rebuildPresetRow();
        refreshEqUi();
        return panel;
    }

    private interface IntSink { void put(int v); }

    private View effectSlider(Context c, String label, int initial0to1000, IntSink sink) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Glass.text(c, label, 12f, Glass.TEXT_DIM, false);
        row.addView(name, new LinearLayout.LayoutParams(
                (int) Glass.dp(c, 92), ViewGroup.LayoutParams.WRAP_CONTENT));
        SeekBar bar = new SeekBar(c);
        bar.setMax(100);
        bar.setProgress(Math.max(0, Math.min(100, initial0to1000 / 10)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                if (user) sink.put(p);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        row.addView(bar, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private String crossfadeLabel() {
        return crossfadeSec <= 0
                ? "Crossfade: Off (fade between tracks)"
                : "Crossfade: " + (crossfadeSec % 1 == 0
                        ? String.valueOf((int) crossfadeSec) : String.valueOf(crossfadeSec))
                        + " s (volume fade at track joins — not a two-deck mix)";
    }

    private void setSleep(String name) {
        sleepAtTrackEnd = false;
        sleepDeadlineMs = 0;
        if (name.equals("Off")) {
            sleepLabel.setText("");
            return;
        }
        if (name.equals("End of track")) {
            sleepAtTrackEnd = true;
            sleepLabel.setText("Sleep at end of track");
            return;
        }
        try {
            int mins = Integer.parseInt(name);
            sleepDeadlineMs = System.currentTimeMillis() + mins * 60000L;
            sleepLabel.setText("Sleep in " + fmt(mins * 60000L));
        } catch (Exception ignored) {}
    }

    private void rebuildPresetRow() {
        if (presetRow == null) return;
        presetRow.removeAllViews();
        boolean first = true;
        for (final String name : EqPresets.NAMES) {
            TextView chip = mkChip(act, name, Glass.NEON_CYAN);
            chip.setOnClickListener(v -> {
                eq.applyPreset(name);
                refreshEqUi();
                rebuildBandSliders();
            });
            presetRow.addView(chip, chipLp(act, first));
            first = false;
        }
        for (final String name : eq.userPresets().keySet()) {
            TextView chip = mkChip(act, name, Glass.ACCENT);
            chip.setOnClickListener(v -> {
                eq.applyUserPreset(name);
                refreshEqUi();
                rebuildBandSliders();
            });
            presetRow.addView(chip, chipLp(act, false));
        }
        TextView save = mkChip(act, "Save preset", Glass.NEON_VIOLET);
        save.setOnClickListener(v -> {
            final EditText input = new EditText(act);
            input.setHint("Preset name");
            input.setTextColor(Glass.TEXT);
            new AlertDialog.Builder(act)
                    .setTitle("Save EQ preset")
                    .setView(input)
                    .setPositiveButton("Save", (d, w) -> {
                        String n = input.getText().toString().trim();
                        if (!n.isEmpty()) {
                            eq.saveUserPreset(n);
                            rebuildPresetRow();
                            status.setText("Preset saved: " + n);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        presetRow.addView(save, chipLp(act, false));
    }

    private void rebuildBandSliders() {
        if (bandBox == null) return;
        bandBox.removeAllViews();
        eqUiSession = eq.sessionId();
        eqUiBands = eq.bandCount();
        if (eq.sessionId() <= 0) {
            bandBox.addView(Glass.text(act,
                    "Play a song to attach the equalizer to its audio session.",
                    11.5f, Glass.TEXT_DIM, false));
            return;
        }
        for (int b = 0; b < eq.bandCount(); b++) {
            final int band = b;
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int hz = eq.centerFreqHz(band);
            String freq = hz >= 1000
                    ? String.format(Locale.US, "%.1fk", hz / 1000.0) : hz + "";
            TextView fl = Glass.text(act, freq + " Hz", 11f, Glass.TEXT_DIM, false);
            row.addView(fl, new LinearLayout.LayoutParams(
                    (int) Glass.dp(act, 62), ViewGroup.LayoutParams.WRAP_CONTENT));
            SeekBar bar = new SeekBar(act);
            bar.setMax(300);
            bar.setProgress(Math.round((eq.gainDb(band) + 15f) * 10f));
            final TextView val = Glass.text(act, "", 11f, Glass.TEXT_DIM, false);
            val.setText(String.format(Locale.US, "%+.1f dB", eq.gainDb(band)));
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                    float db = p / 10f - 15f;
                    val.setText(String.format(Locale.US, "%+.1f dB", db));
                    if (user) {
                        eq.setGainDb(band, db);
                        presetLabel.setText("Preset: Custom");
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });
            row.addView(bar, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(val, new LinearLayout.LayoutParams(
                    (int) Glass.dp(act, 58), ViewGroup.LayoutParams.WRAP_CONTENT));
            bandBox.addView(row);
        }
    }

    private void refreshEqUi() {
        if (masterChip != null) masterChip.setText(eq.isMasterOn() ? "EQ: On" : "EQ: Off");
        if (proChip != null) {
            proChip.setText("Pro EQ: " + (eq.isProRequested() ? "On" : "Off"));
        }
        if (engineLabel != null) {
            engineLabel.setText("Engine: " + eq.liveEngine()
                    + (eq.sessionId() <= 0 ? " (attaches when playback starts)" : ""));
        }
        if (presetLabel != null) presetLabel.setText("Preset: " + eq.presetName());
        if (skipChip != null) {
            skipChip.setText("Skip silence: " + (skipSilence ? "On" : "Off"));
        }
        if (dynChip != null) {
            dynChip.setText("Dynamic accent: " + (accentTheme.isDynamicOn() ? "On" : "Off"));
        }
        if (eqUiSession != eq.sessionId() || eqUiBands != eq.bandCount()) {
            rebuildBandSliders();
        }
    }

    /* ------------------------------------------------------------ per-tick audio work */

    private void audioTick(long pos, long dur) {
        androidx.media3.exoplayer.ExoPlayer p = PlaybackService.sharedPlayer();
        if (p != null) {
            int sid = 0;
            try { sid = p.getAudioSessionId(); } catch (Exception ignored) {}
            if (sid > 0 && sid != eq.sessionId()) {
                eq.attach(sid);
                skipSilenceApplied = false;
                if (audioPanel != null && audioPanel.getVisibility() == View.VISIBLE) {
                    refreshEqUi();
                }
            }
            if (!skipSilenceApplied) applySkipSilence();
        }
        // Crossfade: volume fade-out at the track end + fade-in at the
        // start (single player, so this is a fade, not a two-deck mix).
        if (controller != null) {
            if (crossfadeSec > 0 && dur > 0) {
                long fadeMs = (long) (crossfadeSec * 1000);
                float v = 1f;
                long rem = dur - pos;
                if (rem < fadeMs) v = Math.max(0.05f, rem / (float) fadeMs);
                if (fadingIn) {
                    float vi = pos / (float) fadeMs;
                    v = Math.min(v, Math.max(0.05f, vi));
                    if (pos >= fadeMs) fadingIn = false;
                }
                setPlayerVolume(v);
            } else if (lastVolume != 1f) {
                setPlayerVolume(1f);
            }
        }
        if (sleepDeadlineMs > 0 && controller != null) {
            long rem = sleepDeadlineMs - System.currentTimeMillis();
            if (rem <= 0) {
                sleepDeadlineMs = 0;
                sleepLabel.setText("");
                controller.pause();
                status.setText("Sleep timer ended — playback paused");
            } else {
                sleepLabel.setText("Sleep in " + fmt(rem));
            }
        }
        updateLyricHighlight(pos);
    }

    private void setPlayerVolume(float v) {
        if (controller == null || Math.abs(v - lastVolume) < 0.01f) return;
        try {
            controller.setVolume(v);
            lastVolume = v;
        } catch (Exception ignored) {}
    }

    private void applySkipSilence() {
        androidx.media3.exoplayer.ExoPlayer p = PlaybackService.sharedPlayer();
        if (p == null) return;
        try {
            p.setSkipSilenceEnabled(skipSilence);
            skipSilenceApplied = true;
        } catch (Exception ignored) {}
    }

    /* ------------------------------------------------------------ track change: lyrics + accent */

    private void onTrackChangedForAudio(final Song s) {
        loadLyricsFor(s);
        if (s.art != null && !s.art.isEmpty()) {
            final String artRef = s.art;
            net.execute(() -> {
                Bitmap bmp = loadArtBitmap(artRef);
                if (bmp != null) ui.post(() -> accentTheme.onArtwork(bmp));
            });
        }
    }

    private Bitmap loadArtBitmap(String ref) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;
            if (ref.startsWith("content://")) {
                try (InputStream in = act.getContentResolver()
                        .openInputStream(Uri.parse(ref))) {
                    return BitmapFactory.decodeStream(in, null, o);
                }
            }
            if (ref.startsWith("http")) {
                HttpURLConnection cn = (HttpURLConnection) new URL(ref).openConnection();
                cn.setConnectTimeout(8000);
                cn.setReadTimeout(8000);
                cn.setRequestProperty("User-Agent", Net.UA);
                try (InputStream in = cn.getInputStream()) {
                    return BitmapFactory.decodeStream(in, null, o);
                } finally {
                    cn.disconnect();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /* ------------------------------------------------------------ lyrics */

    private LinearLayout buildLyricsPanel(Context c) {
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setVisibility(View.GONE);
        Glass.pad(panel, c, 2, 8, 2, 2);
        lyricsState = Glass.text(c, "Play a song to fetch its lyrics.",
                11.5f, Glass.TEXT_DIM, false);
        panel.addView(lyricsState);
        lyricsBody = Glass.text(c, "", 13.5f, Glass.TEXT, false);
        lyricsBody.setLineSpacing(Glass.dp(c, 4), 1f);
        lyricsScroll = new ScrollView(c);
        lyricsScroll.addView(lyricsBody);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) Glass.dp(c, 168));
        lp.topMargin = (int) Glass.dp(c, 4);
        panel.addView(lyricsScroll, lp);
        return panel;
    }

    private void loadLyricsFor(final Song s) {
        final int gen = ++lyricsGen;
        lyricsDoc = null;
        lastLyricIdx = -2;
        if (lyricsState != null) lyricsState.setText("Loading lyrics…");
        if (lyricsBody != null) lyricsBody.setText("");
        final File dir = new File(act.getCacheDir(), "lyrics");
        final String key = !s.id.isEmpty() ? s.id : (s.title + "|" + s.artist);
        net.execute(() -> {
            LrclibClient.Result r = LrclibClient.readCache(dir, key);
            if (r == null) {
                r = LrclibClient.fetch(s.title, s.artist, s.durSec);
                LrclibClient.writeCache(dir, key, r);
            }
            final LrclibClient.Result result = r;
            ui.post(() -> {
                if (gen != lyricsGen) return;
                if (result == null || !result.found()) {
                    lyricsDoc = null;
                    lyricsState.setText("Lyrics not found for this track.");
                    lyricsBody.setText("");
                    return;
                }
                if (result.synced != null) {
                    lyricsDoc = Lrc.parse(result.synced);
                    lyricsState.setText("Synced lyrics · LRCLIB");
                } else {
                    // Some LRCLIB "plain" records embed timestamps anyway —
                    // label from what the parser actually found.
                    lyricsDoc = Lrc.parse(result.plain);
                    lyricsState.setText(lyricsDoc.synced
                            ? "Synced lyrics · LRCLIB"
                            : "Plain lyrics (not synced) · LRCLIB");
                }
                lastLyricIdx = -2;
                renderLyrics(controller != null
                        ? Math.max(0, controller.getCurrentPosition()) : 0);
            });
        });
    }

    private void updateLyricHighlight(long pos) {
        if (lyricsDoc == null || !lyricsDoc.synced) return;
        if (lyricsPanel == null || lyricsPanel.getVisibility() != View.VISIBLE) return;
        int idx = lyricsDoc.indexAt(pos);
        if (idx != lastLyricIdx) renderLyrics(pos);
    }

    private void renderLyrics(long pos) {
        if (lyricsDoc == null || lyricsBody == null) return;
        if (!lyricsDoc.synced) {
            StringBuilder sb = new StringBuilder();
            for (String line : lyricsDoc.plain) sb.append(line).append('\n');
            lyricsBody.setText(sb.toString());
            return;
        }
        int idx = lyricsDoc.indexAt(pos);
        lastLyricIdx = idx;
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        for (int i = 0; i < lyricsDoc.lines.size(); i++) {
            int start = sb.length();
            sb.append(lyricsDoc.lines.get(i).text);
            if (i == idx) {
                sb.setSpan(new android.text.style.ForegroundColorSpan(Glass.ACCENT),
                        start, sb.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        start, sb.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            sb.append('\n');
        }
        lyricsBody.setText(sb);
        if (idx >= 0) {
            lyricsBody.post(() -> {
                android.text.Layout layout = lyricsBody.getLayout();
                if (layout != null && idx < layout.getLineCount()) {
                    int top = layout.getLineTop(idx);
                    lyricsScroll.smoothScrollTo(0,
                            Math.max(0, top - (int) Glass.dp(act, 56)));
                }
            });
        }
    }
}
