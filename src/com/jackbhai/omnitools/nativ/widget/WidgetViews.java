package com.jackbhai.omnitools.nativ.widget;

import android.app.DownloadManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.KeyEvent;
import android.view.View;
import android.widget.RemoteViews;

import com.jackbhai.omnitools.nativ.MainActivity;
import com.jackbhai.omnitools.nativ.R;
import com.jackbhai.omnitools.nativ.WebFrameActivity;

import java.io.File;

/**
 * RemoteViews builders for every widget kind, plus the shared
 * PendingIntent factories. Providers and the config activity both build
 * through here, so a widget looks identical however it was refreshed.
 *
 * Now-playing contract (written by PlaybackService / Track C):
 *   SharedPreferences "omni_nowplaying": title, artist, art_file,
 *   playing, has_track; broadcast NOW_PLAYING_CHANGED on every change.
 * Playback control goes through media-button broadcasts to
 * androidx.media3.session.MediaButtonReceiver — the sanctioned path that
 * wakes the session even when the service is stopped. The widget never
 * starts PlaybackService directly.
 */
public final class WidgetViews {

    public static final String NOW_PLAYING_ACTION =
            "com.jackbhai.omnitools.nativ.NOW_PLAYING_CHANGED";
    public static final String NOW_PLAYING_PREFS = "omni_nowplaying";
    /** Extra understood by MainActivity (added at integration). */
    public static final String EXTRA_OPEN_SECTION = "open_section";

    private static final int PI_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    private WidgetViews() {}

    // ------------------------------------------------------------------
    // PendingIntent factories
    // ------------------------------------------------------------------

    /** Open MainActivity, optionally pre-selecting a bottom-nav section. */
    public static PendingIntent openApp(Context c, int widgetId, int reqOffset, String section) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (section != null) i.putExtra(EXTRA_OPEN_SECTION, section);
        return PendingIntent.getActivity(c, widgetId * 100 + reqOffset, i, PI_FLAGS);
    }

    /** Open a bundled web tool via WebFrameActivity (in-app component). */
    public static PendingIntent openWebTool(Context c, int widgetId, int reqOffset,
                                            String route, String title) {
        Intent i = new Intent();
        i.setComponent(new ComponentName(c.getPackageName(),
                "com.jackbhai.omnitools.nativ.WebFrameActivity"));
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra(WebFrameActivity.EXTRA_ROUTE, route);
        i.putExtra(WebFrameActivity.EXTRA_TITLE, title);
        return PendingIntent.getActivity(c, widgetId * 100 + reqOffset, i, PI_FLAGS);
    }

    /** Media-button broadcast to MediaButtonReceiver (play/pause/next/prev). */
    public static PendingIntent mediaButton(Context c, int widgetId, int reqOffset, int keycode) {
        Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON);
        i.setComponent(new ComponentName(c, androidx.media3.session.MediaButtonReceiver.class));
        i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, keycode));
        return PendingIntent.getBroadcast(c, widgetId * 100 + reqOffset, i, PI_FLAGS);
    }

    private static void applyGlass(Context c, RemoteViews rv, int widgetId, int defW, int defH) {
        int[] size = WidgetPrefs.sizeDp(c, widgetId, defW, defH);
        rv.setImageViewBitmap(R.id.widget_bg,
                WidgetPrefs.glassBitmap(c, widgetId, size[0], size[1]));
    }

    // ------------------------------------------------------------------
    // Music player (shared by Mini and Big)
    // ------------------------------------------------------------------

    private static RemoteViews buildPlayer(Context c, int widgetId, boolean big) {
        RemoteViews rv = new RemoteViews(c.getPackageName(),
                big ? R.layout.widget_player_big : R.layout.widget_player_mini);
        applyGlass(c, rv, widgetId, 250, big ? 110 : 40);

        SharedPreferences np = c.getSharedPreferences(NOW_PLAYING_PREFS, Context.MODE_PRIVATE);
        boolean has = np.getBoolean("has_track", false);
        boolean playing = np.getBoolean("playing", false);
        boolean showArt = WidgetPrefs.showArtFor(c, widgetId);

        rv.setOnClickPendingIntent(R.id.widget_root, openApp(c, widgetId, 0, "songs"));

        if (!has) {
            rv.setTextViewText(R.id.widget_title, "Nothing playing");
            rv.setTextViewText(R.id.widget_artist, "Tap to open Songs");
            rv.setViewVisibility(R.id.widget_btn_prev, View.GONE);
            rv.setViewVisibility(R.id.widget_btn_play, View.GONE);
            rv.setViewVisibility(R.id.widget_btn_next, View.GONE);
            if (showArt) {
                rv.setViewVisibility(R.id.widget_art, View.VISIBLE);
                rv.setImageViewBitmap(R.id.widget_art,
                        WidgetPrefs.placeholderArt(c, widgetId, big ? 72 : 40));
            } else {
                rv.setViewVisibility(R.id.widget_art, View.GONE);
            }
            return rv;
        }

        String title = np.getString("title", null);
        String artist = np.getString("artist", null);
        rv.setTextViewText(R.id.widget_title,
                title == null || title.isEmpty() ? "Unknown title" : title);
        rv.setTextViewText(R.id.widget_artist,
                artist == null || artist.isEmpty() ? "Unknown artist" : artist);

        if (showArt) {
            rv.setViewVisibility(R.id.widget_art, View.VISIBLE);
            Bitmap art = decodeArt(np.getString("art_file", null), big ? 288 : 160);
            if (art != null) rv.setImageViewBitmap(R.id.widget_art, art);
            else rv.setImageViewBitmap(R.id.widget_art,
                    WidgetPrefs.placeholderArt(c, widgetId, big ? 72 : 40));
        } else {
            rv.setViewVisibility(R.id.widget_art, View.GONE);
        }

        rv.setViewVisibility(R.id.widget_btn_prev, View.VISIBLE);
        rv.setViewVisibility(R.id.widget_btn_play, View.VISIBLE);
        rv.setViewVisibility(R.id.widget_btn_next, View.VISIBLE);
        rv.setImageViewResource(R.id.widget_btn_play,
                playing ? R.drawable.ic_pause : R.drawable.ic_play);
        rv.setOnClickPendingIntent(R.id.widget_btn_prev,
                mediaButton(c, widgetId, 1, KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        rv.setOnClickPendingIntent(R.id.widget_btn_play,
                mediaButton(c, widgetId, 2, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        rv.setOnClickPendingIntent(R.id.widget_btn_next,
                mediaButton(c, widgetId, 3, KeyEvent.KEYCODE_MEDIA_NEXT));
        return rv;
    }

    public static RemoteViews buildPlayerMini(Context c, int widgetId) {
        return buildPlayer(c, widgetId, false);
    }

    public static RemoteViews buildPlayerBig(Context c, int widgetId) {
        return buildPlayer(c, widgetId, true);
    }

    /** Decode the cached artwork file, centre-cropped square, downscaled
     *  so the RemoteViews transaction stays well under the Binder limit. */
    private static Bitmap decodeArt(String path, int targetPx) {
        if (path == null || path.isEmpty()) return null;
        try {
            File f = new File(path);
            if (!f.exists() || f.length() == 0) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            int minDim = Math.min(bounds.outWidth, bounds.outHeight);
            while (minDim / (sample * 2) >= targetPx) sample *= 2;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeFile(path, o);
            if (b == null) return null;
            int side = Math.min(b.getWidth(), b.getHeight());
            if (b.getWidth() != side || b.getHeight() != side) {
                Bitmap cropped = Bitmap.createBitmap(b,
                        (b.getWidth() - side) / 2, (b.getHeight() - side) / 2, side, side);
                if (cropped != b) b.recycle();
                b = cropped;
            }
            if (side > targetPx * 2) {
                Bitmap scaled = Bitmap.createScaledBitmap(b, targetPx * 2, targetPx * 2, true);
                if (scaled != b) b.recycle();
                b = scaled;
            }
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Verse of the Day
    // ------------------------------------------------------------------

    public static RemoteViews buildVerse(Context c, int widgetId) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_verse);
        applyGlass(c, rv, widgetId, 180, 40);
        String[] v = VerseData.today();
        rv.setTextViewText(R.id.widget_verse_text, v[0]);
        rv.setTextViewText(R.id.widget_verse_source, v[1]);
        rv.setTextColor(R.id.widget_verse_label, WidgetPrefs.accentFor(c, widgetId));
        rv.setOnClickPendingIntent(R.id.widget_root, openApp(c, widgetId, 0, null));
        return rv;
    }

    // ------------------------------------------------------------------
    // Quick Tools
    // ------------------------------------------------------------------

    /** One choosable Quick Tools entry. kind: 0 = native section in
     *  MainActivity (route = section key), 1 = bundled web tool opened
     *  via WebFrameActivity (route = Registry tool id). */
    public static final class ToolDef {
        public final String key, label, initial, route;
        public final int kind;
        ToolDef(String key, String label, String initial, int kind, String route) {
            this.key = key; this.label = label; this.initial = initial;
            this.kind = kind; this.route = route;
        }
    }

    public static final ToolDef[] TOOL_CATALOG = {
            new ToolDef("songs", "Songs", "S", 0, "songs"),
            new ToolDef("tv", "TV", "TV", 0, "tv"),
            new ToolDef("downloader", "Downloader", "DL", 0, "downloader"),
            new ToolDef("games", "Games", "G", 0, "games"),
            new ToolDef("menu", "All Tools", "M", 0, "menu"),
            new ToolDef("gita", "Gita", "Gt", 1, "gita"),
            new ToolDef("radio", "Radio", "R", 1, "radio"),
            new ToolDef("weather", "Weather", "W", 1, "weather"),
    };

    public static ToolDef toolByKey(String key) {
        for (ToolDef t : TOOL_CATALOG) if (t.key.equals(key)) return t;
        return null;
    }

    private static final int[][] TILE_IDS = {
            {R.id.widget_tile0, R.id.widget_tile0_initial, R.id.widget_tile0_label},
            {R.id.widget_tile1, R.id.widget_tile1_initial, R.id.widget_tile1_label},
            {R.id.widget_tile2, R.id.widget_tile2_initial, R.id.widget_tile2_label},
            {R.id.widget_tile3, R.id.widget_tile3_initial, R.id.widget_tile3_label},
    };

    public static RemoteViews buildQuickTools(Context c, int widgetId) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_quicktools);
        applyGlass(c, rv, widgetId, 250, 40);
        String[] keys = WidgetPrefs.toolsFor(c, widgetId);
        int accent = WidgetPrefs.accentFor(c, widgetId);
        for (int i = 0; i < TILE_IDS.length; i++) {
            int root = TILE_IDS[i][0], initial = TILE_IDS[i][1], label = TILE_IDS[i][2];
            ToolDef def = i < keys.length ? toolByKey(keys[i]) : null;
            if (def == null) {
                rv.setViewVisibility(root, View.GONE);
                continue;
            }
            rv.setViewVisibility(root, View.VISIBLE);
            rv.setTextViewText(initial, def.initial);
            rv.setTextColor(initial, accent);
            rv.setTextViewText(label, def.label);
            PendingIntent pi = def.kind == 0
                    ? openApp(c, widgetId, 10 + i, def.route)
                    : openWebTool(c, widgetId, 10 + i, def.route, def.label);
            rv.setOnClickPendingIntent(root, pi);
        }
        return rv;
    }

    // ------------------------------------------------------------------
    // Songs Search
    // ------------------------------------------------------------------

    public static RemoteViews buildSearch(Context c, int widgetId) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_search);
        applyGlass(c, rv, widgetId, 250, 40);
        PendingIntent pi = openApp(c, widgetId, 0, "songs");
        rv.setOnClickPendingIntent(R.id.widget_root, pi);
        rv.setOnClickPendingIntent(R.id.widget_search_bar, pi);
        rv.setTextColor(R.id.widget_search_hint, WidgetPrefs.accentFor(c, widgetId));
        return rv;
    }

    // ------------------------------------------------------------------
    // Downloads
    // ------------------------------------------------------------------

    public static RemoteViews buildDownloads(Context c, int widgetId) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_downloads);
        applyGlass(c, rv, widgetId, 250, 40);

        String title = null, statusText = "Downloads from OmniTools appear here";
        int percent = 0;
        boolean found = false;
        Cursor cur = null;
        try {
            DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                cur = dm.query(new DownloadManager.Query());
                if (cur != null) {
                    int iTitle = cur.getColumnIndex(DownloadManager.COLUMN_TITLE);
                    int iStatus = cur.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int iDone = cur.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                    int iTotal = cur.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                    int iMod = cur.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP);
                    long bestMod = -1;
                    // DownloadManager only returns this app's own downloads.
                    int guard = 0;
                    while (cur.moveToNext() && guard++ < 500) {
                        long mod = iMod >= 0 ? cur.getLong(iMod) : 0;
                        if (mod < bestMod) continue;
                        bestMod = mod;
                        found = true;
                        title = iTitle >= 0 ? cur.getString(iTitle) : null;
                        int st = iStatus >= 0 ? cur.getInt(iStatus) : -1;
                        long done = iDone >= 0 ? cur.getLong(iDone) : 0;
                        long total = iTotal >= 0 ? cur.getLong(iTotal) : -1;
                        percent = total > 0 ? (int) Math.min(100, done * 100 / total)
                                : (st == DownloadManager.STATUS_SUCCESSFUL ? 100 : 0);
                        switch (st) {
                            case DownloadManager.STATUS_RUNNING:
                                statusText = total > 0 ? "Downloading" : "Downloading"; break;
                            case DownloadManager.STATUS_PENDING:
                                statusText = "Pending"; break;
                            case DownloadManager.STATUS_PAUSED:
                                statusText = "Paused"; break;
                            case DownloadManager.STATUS_SUCCESSFUL:
                                statusText = "Completed"; break;
                            case DownloadManager.STATUS_FAILED:
                                statusText = "Failed"; break;
                            default:
                                statusText = "Unknown status"; break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            found = false;
        } finally {
            if (cur != null) try { cur.close(); } catch (Exception ignored) {}
        }

        if (!found) {
            rv.setTextViewText(R.id.widget_dl_title, "No downloads yet");
            rv.setTextViewText(R.id.widget_dl_status, statusText);
            rv.setTextViewText(R.id.widget_dl_percent, "");
            rv.setProgressBar(R.id.widget_dl_progress, 100, 0, false);
        } else {
            rv.setTextViewText(R.id.widget_dl_title,
                    title == null || title.isEmpty() ? "Download" : title);
            rv.setTextViewText(R.id.widget_dl_status, statusText);
            rv.setTextViewText(R.id.widget_dl_percent, percent + "%");
            rv.setTextColor(R.id.widget_dl_percent, WidgetPrefs.accentFor(c, widgetId));
            rv.setProgressBar(R.id.widget_dl_progress, 100, percent, false);
        }

        // Tap = refresh this widget (the platform caps the periodic
        // update at 30 min, so a manual tap is the honest fast path).
        Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        i.setComponent(new ComponentName(c, DownloadsWidgetProvider.class));
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId});
        rv.setOnClickPendingIntent(R.id.widget_root,
                PendingIntent.getBroadcast(c, widgetId * 100 + 40, i, PI_FLAGS));
        return rv;
    }

    /** Rebuild the right RemoteViews for a provider class name (config). */
    public static RemoteViews buildForProvider(Context c, int widgetId, String providerClass) {
        if (providerClass == null) return null;
        if (providerClass.endsWith("PlayerMiniWidgetProvider")) return buildPlayerMini(c, widgetId);
        if (providerClass.endsWith("PlayerBigWidgetProvider")) return buildPlayerBig(c, widgetId);
        if (providerClass.endsWith("VerseWidgetProvider")) return buildVerse(c, widgetId);
        if (providerClass.endsWith("QuickToolsWidgetProvider")) return buildQuickTools(c, widgetId);
        if (providerClass.endsWith("SongsSearchWidgetProvider")) return buildSearch(c, widgetId);
        if (providerClass.endsWith("DownloadsWidgetProvider")) return buildDownloads(c, widgetId);
        return null;
    }
}
