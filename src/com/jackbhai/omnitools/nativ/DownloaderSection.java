package com.jackbhai.omnitools.nativ;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloader — paste a link, get formats, save to the Downloads folder.
 *
 * Resolution mirrors the PWA (src/tools/downloader.jsx): YouTube links race
 * the broad resolver against the front-end pool's per-quality stream lists;
 * every other platform goes to the broad resolver (freeapi.pro alldl —
 * direct first, the app relay as fallback; native has no CORS so the PWA's
 * proxy stages collapse into those two). Downloads run through Android's
 * DownloadManager, so they survive the app being closed and land in the
 * public Downloads folder with a system notification.
 */
public final class DownloaderSection {

    private final Activity act;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newFixedThreadPool(2);
    private final List<AlldlApi.Option> options = new ArrayList<>();
    private final Adapter adapter = new Adapter();

    private View root;
    private EditText urlBox;
    private TextView status;
    private TextView infoTitle;
    private String currentTitle = "download";
    private long activeDownloadId = -1;

    public DownloaderSection(Activity act) {
        this.act = act;
        build();
    }

    public View view() { return root; }

    private void build() {
        Context c = act;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        Glass.pad(col, c, 14, 12, 14, 8);

        col.addView(Glass.text(c, "Downloader", 24, Glass.TEXT, true));

        urlBox = new EditText(c);
        urlBox.setHint("Paste a video or audio link…");
        urlBox.setSingleLine(true);
        urlBox.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlBox.setTextColor(Glass.TEXT);
        urlBox.setHintTextColor(Glass.TEXT_DIM);
        urlBox.setBackground(Glass.panel(c, 14));
        Glass.pad(urlBox, c, 14, 10, 14, 10);
        LinearLayout.LayoutParams urlLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        urlLp.topMargin = (int) Glass.dp(c, 12);
        col.addView(urlBox, urlLp);

        Button fetch = new Button(c);
        fetch.setText("Get download options");
        fetch.setTextColor(Glass.TEXT);
        fetch.setBackground(Glass.neonPill(c, Glass.NEON_CYAN));
        fetch.setOnClickListener(v -> resolve());
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fLp.topMargin = (int) Glass.dp(c, 10);
        col.addView(fetch, fLp);

        status = Glass.text(c, "Works with most video, music and social links.",
                12.5f, Glass.TEXT_DIM, false);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.topMargin = (int) Glass.dp(c, 10);
        col.addView(status, stLp);

        infoTitle = Glass.text(c, "", 15, Glass.TEXT, true);
        LinearLayout.LayoutParams itLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        itLp.topMargin = (int) Glass.dp(c, 8);
        col.addView(infoTitle, itLp);

        RecyclerView list = new RecyclerView(c);
        list.setLayoutManager(new LinearLayoutManager(c));
        list.setAdapter(adapter);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listLp.topMargin = (int) Glass.dp(c, 6);
        col.addView(list, listLp);
        root = col;
    }

    private static final Pattern YT_ID =
            Pattern.compile("(?:v=|youtu\\.be/|shorts/|live/|embed/)([A-Za-z0-9_-]{11})");

    private void resolve() {
        final String raw = urlBox.getText().toString().trim();
        if (!raw.startsWith("http")) {
            status.setText("Paste a full link first (starting with http).");
            return;
        }
        status.setText("Resolving…");
        options.clear();
        adapter.notifyDataSetChanged();
        infoTitle.setText("");

        Matcher m = YT_ID.matcher(raw);
        final String ytId = m.find() ? m.group(1) : null;

        if (ytId != null) {
            // Race both legs like the PWA; first rich answer wins.
            final boolean[] settled = {false};
            net.execute(() -> {
                try {
                    AlldlApi.Result r = AlldlApi.resolve(raw);
                    ui.post(() -> {
                        if (settled[0]) return;
                        settled[0] = true;
                        showResult(r.title, r.options);
                    });
                } catch (Exception ignored) {}
            });
            net.execute(() -> {
                try {
                    PipedApi.Streams s = PipedApi.streams(ytId);
                    List<AlldlApi.Option> opts = new ArrayList<>();
                    for (PipedApi.Option o : s.videoOptions) {
                        AlldlApi.Option a = new AlldlApi.Option();
                        a.label = o.label + (o.videoOnly ? " (no audio)" : "");
                        a.url = o.url;
                        a.size = o.size;
                        opts.add(a);
                    }
                    for (PipedApi.Option o : s.audioOptions) {
                        AlldlApi.Option a = new AlldlApi.Option();
                        a.label = "Audio " + o.label;
                        a.url = o.url;
                        a.size = o.size;
                        a.audio = true;
                        opts.add(a);
                    }
                    if (opts.isEmpty()) return;
                    ui.post(() -> {
                        if (settled[0]) return;
                        settled[0] = true;
                        showResult(s.title, opts);
                    });
                } catch (Exception ignored) {}
            });
            ui.postDelayed(() -> {
                if (!settled[0]) status.setText("Still resolving — some sources are slow. Hang on…");
            }, 12000);
            ui.postDelayed(() -> {
                if (!settled[0]) {
                    settled[0] = true;
                    status.setText("Could not resolve this link. Check it and try again.");
                }
            }, 45000);
        } else {
            net.execute(() -> {
                try {
                    AlldlApi.Result r = AlldlApi.resolve(raw);
                    ui.post(() -> showResult(r.title, r.options));
                } catch (Exception e) {
                    ui.post(() -> status.setText(
                            "Could not resolve this link. Some sources need a login and cannot be downloaded."));
                }
            });
        }
    }

    private void showResult(String title, List<AlldlApi.Option> opts) {
        currentTitle = (title == null || title.isEmpty()) ? "download" : title;
        infoTitle.setText(currentTitle);
        options.clear();
        options.addAll(opts);
        adapter.notifyDataSetChanged();
        status.setText(opts.size() + " format" + (opts.size() == 1 ? "" : "s")
                + " — tap one to save it to Downloads.");
    }

    private void startDownload(AlldlApi.Option o) {
        String ext = o.audio ? ".mp3" : ".mp4";
        String fromUrl = "";
        try {
            String path = Uri.parse(o.url).getPath();
            if (path != null) {
                int dot = path.lastIndexOf('.');
                if (dot > 0 && path.length() - dot <= 5) fromUrl = path.substring(dot);
            }
        } catch (Exception ignored) {}
        if (!fromUrl.isEmpty() && fromUrl.matches("\\.[a-zA-Z0-9]{2,4}")) ext = fromUrl;
        String name = currentTitle.replaceAll("[\\\\/:*?\"<>|]+", " ")
                .replaceAll("\\s+", " ").trim();
        if (name.length() > 70) name = name.substring(0, 70);
        if (name.isEmpty()) name = "download";
        String fileName = name + " - " + o.label.replaceAll("[^A-Za-z0-9 ]+", "").trim() + ext;

        DownloadManager dm = (DownloadManager) act.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) {
            Toast.makeText(act, "Download service unavailable", Toast.LENGTH_LONG).show();
            return;
        }
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(o.url));
        req.setTitle(fileName);
        req.setDescription(currentTitle);
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        req.addRequestHeader("User-Agent", Net.UA);
        try {
            activeDownloadId = dm.enqueue(req);
            status.setText("Downloading: " + fileName);
            pollDownload(dm, fileName);
        } catch (Exception e) {
            status.setText("Download could not start: " + e.getMessage());
        }
    }

    private void pollDownload(DownloadManager dm, String fileName) {
        final long id = activeDownloadId;
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                if (id != activeDownloadId) return;
                try {
                    Cursor cur = dm.query(new DownloadManager.Query().setFilterById(id));
                    if (cur != null && cur.moveToFirst()) {
                        int st = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        long got = cur.getLong(cur.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        long total = cur.getLong(cur.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        if (st == DownloadManager.STATUS_SUCCESSFUL) {
                            status.setText("Saved to Downloads: " + fileName);
                            cur.close();
                            return;
                        } else if (st == DownloadManager.STATUS_FAILED) {
                            status.setText("Download failed. The source may have expired — resolve the link again.");
                            cur.close();
                            return;
                        } else if (total > 0) {
                            status.setText(String.format(Locale.US,
                                    "Downloading… %d%% (%s)", got * 100 / total, fileName));
                        }
                        cur.close();
                    }
                } catch (Exception ignored) {}
                ui.postDelayed(this, 1500);
            }
        }, 1200);
    }

    private final class Holder extends RecyclerView.ViewHolder {
        final TextView label, sub;
        Holder(View v, TextView l, TextView s) { super(v); label = l; sub = s; }
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @Override public Holder onCreateViewHolder(ViewGroup parent, int vt) {
            Context c = parent.getContext();
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Glass.panel(c, 14));
            Glass.pad(row, c, 14, 12, 14, 12);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) Glass.dp(c, 4);
            lp.bottomMargin = (int) Glass.dp(c, 4);
            row.setLayoutParams(lp);
            TextView l = Glass.text(c, "", 15, Glass.TEXT, true);
            TextView s = Glass.text(c, "", 12.5f, Glass.TEXT_DIM, false);
            LinearLayout meta = new LinearLayout(c);
            meta.setOrientation(LinearLayout.VERTICAL);
            meta.addView(l);
            meta.addView(s);
            row.addView(meta, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView dl = Glass.text(c, "Save", 13, Glass.NEON_CYAN, true);
            dl.setBackground(Glass.neonPill(c, Glass.NEON_CYAN));
            Glass.pad(dl, c, 12, 6, 12, 6);
            row.addView(dl);
            return new Holder(row, l, s);
        }

        @Override public void onBindViewHolder(Holder h, int pos) {
            AlldlApi.Option o = options.get(pos);
            h.label.setText((o.audio ? "Audio · " : "Video · ") + o.label);
            h.sub.setText(o.size > 0
                    ? String.format(Locale.US, "%.1f MB", o.size / 1048576.0)
                    : "Size unknown");
            h.itemView.setOnClickListener(v -> startDownload(o));
        }

        @Override public int getItemCount() { return options.size(); }
    }
}
