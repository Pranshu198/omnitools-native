package com.jackbhai.omnitools.nativ;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Songs — the native music section.
 *
 * Search runs the PWA's two independent routes CONCURRENTLY (community
 * mirrors raced in waves; the catalogue's own API direct). The first solid
 * batch paints immediately; when the second route lands its rows are merged
 * in, deduped — the same progressive shape the PWA's 2026-11-14 latency fix
 * uses, because a search that waits for every source feels broken.
 *
 * Playback goes through a MediaController to PlaybackService: background
 * play with notification + lockscreen controls. Tapping a row queues the
 * whole visible list from that row on.
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
    private SeekBar seek;
    private boolean userSeeking = false;

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
        bar.setBackground(Glass.panelStrong(c, 18));
        Glass.pad(bar, c, 12, 10, 12, 10);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.topMargin = (int) Glass.dp(c, 8);
        bar.setLayoutParams(barLp);

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

        Button prev = smallBtn(c, "|<<");
        prev.setOnClickListener(v -> { if (controller != null) controller.seekToPrevious(); });
        playBtn = smallBtn(c, "Play");
        playBtn.setOnClickListener(v -> {
            if (controller == null) return;
            if (controller.isPlaying()) controller.pause(); else controller.play();
        });
        Button next = smallBtn(c, ">>|");
        next.setOnClickListener(v -> { if (controller != null) controller.seekToNext(); });
        top.addView(prev);
        top.addView(playBtn);
        top.addView(next);
        bar.addView(top);

        LinearLayout seekRow = new LinearLayout(c);
        seekRow.setOrientation(LinearLayout.HORIZONTAL);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        nowPos = Glass.text(c, "0:00", 11, Glass.TEXT_DIM, false);
        nowDur = Glass.text(c, "0:00", 11, Glass.TEXT_DIM, false);
        seek = new SeekBar(c);
        seek.setMax(1000);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser && controller != null && controller.getDuration() > 0) {
                    controller.seekTo(controller.getDuration() * p / 1000);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) { userSeeking = false; }
        });
        seekRow.addView(nowPos);
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        seekLp.leftMargin = (int) Glass.dp(c, 8);
        seekLp.rightMargin = (int) Glass.dp(c, 8);
        seekRow.addView(seek, seekLp);
        seekRow.addView(nowDur);
        LinearLayout.LayoutParams seekRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        seekRowLp.topMargin = (int) Glass.dp(c, 4);
        bar.addView(seekRow, seekRowLp);
        return bar;
    }

    private Button smallBtn(Context c, String label) {
        Button b = new Button(c);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(Glass.TEXT);
        b.setBackground(Glass.neonPill(c, Glass.NEON_CYAN));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = (int) Glass.dp(c, 6);
        b.setLayoutParams(lp);
        return b;
    }

    /* ------------------------------------------------------------ search */

    private void runSearch() {
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
                    }
                    @Override public void onMediaItemTransition(
                            @Nullable MediaItem item, int reason) {
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
            if (controller != null && !userSeeking) {
                long pos = controller.getCurrentPosition();
                long dur = controller.getDuration();
                if (dur > 0) {
                    seek.setProgress((int) (1000 * pos / dur));
                    nowPos.setText(fmt(pos));
                    nowDur.setText(fmt(dur));
                }
            }
            ui.postDelayed(this, 500);
        }
    };

    private void updateNowPlaying() {
        if (controller == null) return;
        MediaItem item = controller.getCurrentMediaItem();
        if (item == null) return;
        MediaMetadata md = item.mediaMetadata;
        nowTitle.setText(md.title != null ? md.title.toString() : "Playing");
        nowArtist.setText(md.artist != null ? md.artist.toString() : "");
        if (md.artworkUri != null) Img.load(md.artworkUri.toString(), nowArt);
        playBtn.setText(controller.isPlaying() ? "Pause" : "Play");
    }

    private void playFrom(List<Song> list, int index) {
        if (controller == null || list.isEmpty()) return;
        List<MediaItem> items = new ArrayList<>();
        for (Song s : list) {
            MediaItem mi = new MediaItem.Builder()
                    .setUri(s.stream)
                    .setMediaId(s.id.isEmpty() ? s.title : s.id)
                    .setMediaMetadata(new MediaMetadata.Builder()
                            .setTitle(s.title)
                            .setArtist(s.artist)
                            .setAlbumTitle(s.album)
                            .setArtworkUri(s.art.isEmpty() ? null : Uri.parse(s.art))
                            .build())
                    .build();
            items.add(mi);
        }
        controller.setMediaItems(items, index, 0);
        controller.prepare();
        controller.play();
    }

    /* ------------------------------------------------------------ favourites */

    private void loadFavourites() {
        favourites.clear();
        try {
            JSONArray a = new JSONArray(prefs.getString("favourites", "[]"));
            for (int i = 0; i < a.length(); i++) {
                Song s = Song.fromJson(a.optJSONObject(i));
                if (!s.title.isEmpty()) favourites.add(s);
            }
        } catch (Exception ignored) {}
    }

    private void saveFavourites() {
        JSONArray a = new JSONArray();
        for (Song s : favourites) a.put(s.toJson());
        prefs.edit().putString("favourites", a.toString()).apply();
    }

    private boolean isFav(Song s) {
        for (Song f : favourites) {
            if (!s.id.isEmpty() && s.id.equals(f.id)) return true;
            if (s.title.equalsIgnoreCase(f.title) && s.artist.equalsIgnoreCase(f.artist)) return true;
        }
        return false;
    }

    private void toggleFav(Song s) {
        if (isFav(s)) {
            favourites.removeIf(f ->
                    (!s.id.isEmpty() && s.id.equals(f.id))
                    || (s.title.equalsIgnoreCase(f.title) && s.artist.equalsIgnoreCase(f.artist)));
        } else {
            favourites.add(0, s);
        }
        saveFavourites();
        adapter.notifyDataSetChanged();
        if (showingFavs) refreshList();
    }

    private void refreshList() {
        adapter.notifyDataSetChanged();
    }

    private List<Song> visible() {
        return showingFavs ? favourites : rows;
    }

    /* ------------------------------------------------------------ adapter */

    private final class Holder extends RecyclerView.ViewHolder {
        final ImageView art;
        final TextView title, sub;
        final Button fav;
        Holder(View v, ImageView a, TextView t, TextView s, Button f) {
            super(v); art = a; title = t; sub = s; fav = f;
        }
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @Override public Holder onCreateViewHolder(ViewGroup parent, int vt) {
            Context c = parent.getContext();
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Glass.panel(c, 14));
            Glass.pad(row, c, 10, 8, 10, 8);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) Glass.dp(c, 4);
            lp.bottomMargin = (int) Glass.dp(c, 4);
            row.setLayoutParams(lp);

            ImageView art = new ImageView(c);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            row.addView(art, new LinearLayout.LayoutParams(
                    (int) Glass.dp(c, 52), (int) Glass.dp(c, 52)));

            LinearLayout meta = new LinearLayout(c);
            meta.setOrientation(LinearLayout.VERTICAL);
            TextView t = Glass.text(c, "", 15, Glass.TEXT, true);
            t.setSingleLine(true);
            TextView s = Glass.text(c, "", 12.5f, Glass.TEXT_DIM, false);
            s.setSingleLine(true);
            meta.addView(t);
            meta.addView(s);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            mlp.leftMargin = (int) Glass.dp(c, 10);
            row.addView(meta, mlp);

            Button fav = new Button(c);
            fav.setTextSize(11.5f);
            fav.setTextColor(Glass.TEXT);
            row.addView(fav);
            return new Holder(row, art, t, s, fav);
        }

        @Override public void onBindViewHolder(Holder h, int pos) {
            final Song s = visible().get(pos);
            h.title.setText(s.title);
            String sub = s.artist;
            if (s.durSec > 0) sub += (sub.isEmpty() ? "" : "  ·  ") + fmt(s.durSec * 1000);
            h.sub.setText(sub);
            Img.load(s.art, h.art);
            boolean fav = isFav(s);
            h.fav.setText(fav ? "Saved" : "Save");
            h.fav.setBackground(Glass.neonPill(act, fav ? Glass.NEON_VIOLET : Glass.NEON_CYAN));
            h.fav.setOnClickListener(v -> toggleFav(s));
            h.itemView.setOnClickListener(v -> playFrom(visible(), h.getAdapterPosition()));
        }

        @Override public int getItemCount() { return visible().size(); }
    }

    static String fmt(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }
}
