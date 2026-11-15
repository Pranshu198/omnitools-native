package com.jackbhai.omnitools.nativ;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

    public SongsSection(Activity act) {
        this.act = act;
        prefs = act.getSharedPreferences("omnitools_native", Context.MODE_PRIVATE);
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
            showingFavs = !showingFavs;
            refreshList();
            status.setText(showingFavs
                    ? favourites.size() + " saved songs"
                    : (rows.isEmpty() ? "Search any song, artist or film" : status.getText()));
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
        showingFavs = false;
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
    private List<Song> visible() { return showingFavs ? favourites : rows; }

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
        }

        @Override public int getItemCount() { return visible().size(); }
    }
}
