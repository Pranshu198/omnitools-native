package com.jackbhai.omnitools.nativ;

import android.app.Activity;
import android.content.Intent;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TV — native video search + playback. Search runs through the same public
 * front-end pool the PWA's video features use; tapping a result resolves
 * its streams and opens the full-screen native WatchActivity (ExoPlayer).
 */
public final class TvSection {

    private final Activity act;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newFixedThreadPool(2);
    private final List<PipedApi.Video> rows = new ArrayList<>();
    private final Adapter adapter = new Adapter();

    private View root;
    private EditText queryBox;
    private TextView status;
    private int gen = 0;

    public TvSection(Activity act) {
        this.act = act;
        build();
    }

    public View view() { return root; }

    private void build() {
        android.content.Context c = act;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        Glass.pad(col, c, 14, 12, 14, 8);

        col.addView(Glass.text(c, "TV", 24, Glass.TEXT, true));

        LinearLayout searchRow = new LinearLayout(c);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        srLp.topMargin = (int) Glass.dp(c, 12);
        queryBox = new EditText(c);
        queryBox.setHint("Search videos…");
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

        status = Glass.text(c, "Search any video, show or channel", 12.5f, Glass.TEXT_DIM, false);
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
        root = col;
    }

    private void runSearch() {
        if (Guard.sensitiveBlocked(act)) {
            status.setText("Unavailable in this copy of the app.");
            return;
        }
        Sfx.tap();
        final String q = queryBox.getText().toString().trim();
        if (q.isEmpty()) return;
        final int g = ++gen;
        status.setText("Searching…");
        net.execute(() -> {
            try {
                List<PipedApi.Video> found = PipedApi.search(q);
                ui.post(() -> {
                    if (g != gen) return;
                    rows.clear();
                    rows.addAll(found);
                    adapter.notifyDataSetChanged();
                    status.setText(found.size() + " videos");
                });
            } catch (Exception e) {
                ui.post(() -> {
                    if (g != gen) return;
                    status.setText("Video search failed — the sources are not answering right now. Try again.");
                });
            }
        });
    }

    private void openVideo(PipedApi.Video v) {
        if (Guard.sensitiveBlocked(act)) {
            status.setText("Unavailable in this copy of the app.");
            return;
        }
        Sfx.tap();
        Toast.makeText(act, "Loading stream…", Toast.LENGTH_SHORT).show();
        net.execute(() -> {
            try {
                PipedApi.Streams s = PipedApi.streams(v.id);
                final String videoUrl, audioUrl;
                if (!s.progressive.isEmpty()) {
                    videoUrl = s.progressive;
                    audioUrl = "";
                } else {
                    videoUrl = s.bestVideo;
                    audioUrl = s.bestAudio;
                }
                if (videoUrl == null || videoUrl.isEmpty()) throw new Exception("no video stream");
                ui.post(() -> {
                    Intent it = new Intent(act, WatchActivity.class);
                    it.putExtra(WatchActivity.EXTRA_TITLE, v.title);
                    it.putExtra(WatchActivity.EXTRA_VIDEO, videoUrl);
                    it.putExtra(WatchActivity.EXTRA_AUDIO, audioUrl);
                    act.startActivity(it);
                });
            } catch (Exception e) {
                ui.post(() -> Toast.makeText(act,
                        "This video's stream could not be resolved. Try another.",
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private final class Holder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView title, sub;
        Holder(View v, ImageView t, TextView ti, TextView s) {
            super(v); thumb = t; title = ti; sub = s;
        }
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @Override public Holder onCreateViewHolder(ViewGroup parent, int vt) {
            android.content.Context c = parent.getContext();
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

            ImageView thumb = new ImageView(c);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            row.addView(thumb, new LinearLayout.LayoutParams(
                    (int) Glass.dp(c, 96), (int) Glass.dp(c, 56)));

            LinearLayout meta = new LinearLayout(c);
            meta.setOrientation(LinearLayout.VERTICAL);
            TextView t = Glass.text(c, "", 14.5f, Glass.TEXT, true);
            t.setMaxLines(2);
            TextView s = Glass.text(c, "", 12, Glass.TEXT_DIM, false);
            s.setSingleLine(true);
            meta.addView(t);
            meta.addView(s);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            mlp.leftMargin = (int) Glass.dp(c, 10);
            row.addView(meta, mlp);
            return new Holder(row, thumb, t, s);
        }

        @Override public void onBindViewHolder(Holder h, int pos) {
            PipedApi.Video v = rows.get(pos);
            h.title.setText(v.title);
            String sub = v.uploader;
            if (v.durSec > 0) sub += (sub.isEmpty() ? "" : "  ·  ") + SongsSection.fmt(v.durSec * 1000);
            if (v.views > 0) sub += "  ·  " + compactViews(v.views) + " views";
            h.sub.setText(sub);
            Img.load(v.thumb, h.thumb);
            h.itemView.setOnClickListener(x -> openVideo(v));
        }

        @Override public int getItemCount() { return rows.size(); }
    }

    private static String compactViews(long v) {
        if (v >= 1_000_000) return String.format(Locale.US, "%.1fM", v / 1e6);
        if (v >= 1_000) return String.format(Locale.US, "%.1fK", v / 1e3);
        return String.valueOf(v);
    }
}
