package com.jackbhai.omnitools.nativ.audio;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import com.jackbhai.omnitools.nativ.Song;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * On-device music library: one MediaStore.Audio scan (off the UI thread —
 * callers must invoke {@link #scan} from a worker), cached in memory with
 * an explicit rescan. Groupings (Albums / Artists / Folders) are derived
 * from the same scan, never re-queried.
 */
public final class Library {

    public static final class LocalTrack {
        public long id;
        public String title = "";
        public String artist = "";
        public String album = "";
        public String path = "";
        public String folder = "";
        public long durationMs;
        public long albumId;
        public Uri contentUri;
        public Uri artUri;

        /** Play through the same queue path as streams: content:// uri. */
        public Song toSong() {
            Song s = new Song();
            s.id = "local:" + id;
            s.title = title;
            s.artist = artist;
            s.album = album;
            s.durSec = durationMs / 1000;
            s.stream = contentUri != null ? contentUri.toString() : "";
            s.art = artUri != null ? artUri.toString() : "";
            return s;
        }
    }

    private static volatile List<LocalTrack> cache;
    private static volatile long cacheAtMs;

    public static List<LocalTrack> cached() { return cache; }
    public static long cachedAtMs() { return cacheAtMs; }
    public static void invalidate() { cache = null; cacheAtMs = 0; }

    public static String permission() {
        return Build.VERSION.SDK_INT >= 33
                ? android.Manifest.permission.READ_MEDIA_AUDIO
                : android.Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    public static List<LocalTrack> scan(Context ctx) {
        List<LocalTrack> out = new ArrayList<>();
        ContentResolver cr = ctx.getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID,
        };
        String sel = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        try (Cursor cur = cr.query(uri, proj, sel, null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (cur == null) { cache = out; cacheAtMs = System.currentTimeMillis(); return out; }
            int iId = cur.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int iTitle = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int iArtist = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int iAlbum = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int iDur = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int iData = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            int iAlbumId = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            Uri artBase = Uri.parse("content://media/external/audio/albumart");
            while (cur.moveToNext()) {
                LocalTrack t = new LocalTrack();
                t.id = cur.getLong(iId);
                t.title = or(cur.getString(iTitle), "Unknown title");
                t.artist = or(cur.getString(iArtist), "Unknown artist");
                t.album = or(cur.getString(iAlbum), "");
                t.durationMs = cur.getLong(iDur);
                t.path = or(cur.getString(iData), "");
                t.albumId = cur.getLong(iAlbumId);
                t.contentUri = ContentUris.withAppendedId(uri, t.id);
                t.artUri = ContentUris.withAppendedId(artBase, t.albumId);
                File parent = t.path.isEmpty() ? null : new File(t.path).getParentFile();
                t.folder = parent != null ? parent.getName() : "";
                out.add(t);
            }
        } catch (Exception e) {
            // Permission revoked mid-scan or provider hiccup: return what we have.
        }
        cache = out;
        cacheAtMs = System.currentTimeMillis();
        return out;
    }

    private static String or(String s, String fallback) {
        return s == null || s.trim().isEmpty() ? fallback : s;
    }

    public static Map<String, List<LocalTrack>> byAlbum(List<LocalTrack> all) {
        Map<String, List<LocalTrack>> m = new LinkedHashMap<>();
        for (LocalTrack t : all) put(m, t.album.isEmpty() ? "Unknown album" : t.album, t);
        return m;
    }

    public static Map<String, List<LocalTrack>> byArtist(List<LocalTrack> all) {
        Map<String, List<LocalTrack>> m = new LinkedHashMap<>();
        for (LocalTrack t : all) put(m, t.artist, t);
        return m;
    }

    public static Map<String, List<LocalTrack>> byFolder(List<LocalTrack> all) {
        Map<String, List<LocalTrack>> m = new LinkedHashMap<>();
        for (LocalTrack t : all) put(m, t.folder.isEmpty() ? "Unknown folder" : t.folder, t);
        return m;
    }

    private static void put(Map<String, List<LocalTrack>> m, String k, LocalTrack t) {
        List<LocalTrack> l = m.get(k);
        if (l == null) { l = new ArrayList<>(); m.put(k, l); }
        l.add(t);
    }

    private Library() {}
}
