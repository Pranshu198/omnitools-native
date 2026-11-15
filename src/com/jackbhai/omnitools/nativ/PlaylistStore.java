package com.jackbhai.omnitools.nativ;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Playlists — pure-Java + org.json core (JVM-testable), with a thin
 * Android glue layer at the bottom.
 *
 * Storage mirrors favourites exactly: the whole collection lives as ONE
 * JSON-array string in SharedPreferences file "omnitools_native",
 * key "playlists". Each playlist is
 * {"id": string, "name": string, "songs": [Song JSON objects]}.
 */
public final class PlaylistStore {

    public static final String PREFS = "omnitools_native";
    public static final String KEY = "playlists";

    private static long idCounter = 0;

    private PlaylistStore() {}

    /* ------------------------------------------------------------ model */

    public static final class Playlist {
        public final String id;
        public String name;
        public final List<Song> songs;

        public Playlist(String id, String name, List<Song> songs) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.songs = songs == null ? new ArrayList<>() : songs;
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("name", name);
                JSONArray arr = new JSONArray();
                for (Song s : songs) arr.put(s.toJson());
                o.put("songs", arr);
            } catch (Exception ignored) {}
            return o;
        }

        public static Playlist fromJson(JSONObject o) {
            Playlist p = new Playlist(
                    o == null ? "" : o.optString("id", ""),
                    o == null ? "" : o.optString("name", ""), null);
            if (o != null) {
                JSONArray arr = o.optJSONArray("songs");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject so = arr.optJSONObject(i);
                        if (so != null) p.songs.add(Song.fromJson(so));
                    }
                }
            }
            return p;
        }
    }

    /* ------------------------------------------------------------ core */

    /** Corrupt / null input yields an empty list, never a crash. */
    public static List<Playlist> parse(String json) {
        List<Playlist> out = new ArrayList<>();
        if (json == null) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(Playlist.fromJson(o));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static String serialize(List<Playlist> playlists) {
        JSONArray arr = new JSONArray();
        if (playlists != null) {
            for (Playlist p : playlists) arr.put(p.toJson());
        }
        return arr.toString();
    }

    private static String newId() {
        return "pl" + System.currentTimeMillis() + "_" + (++idCounter);
    }

    public static Playlist create(List<Playlist> playlists, String name) {
        Playlist p = new Playlist(newId(),
                name == null ? "" : name.trim(), null);
        playlists.add(p);
        return p;
    }

    /** Creates a playlist in a fresh list (convenience for tests/callers). */
    public static Playlist create(String name) {
        return new Playlist(newId(), name == null ? "" : name.trim(), null);
    }

    public static Playlist find(List<Playlist> playlists, String id) {
        if (playlists == null || id == null) return null;
        for (Playlist p : playlists) if (p.id.equals(id)) return p;
        return null;
    }

    public static boolean rename(List<Playlist> playlists, String id, String name) {
        Playlist p = find(playlists, id);
        if (p == null) return false;
        p.name = name == null ? "" : name.trim();
        return true;
    }

    public static boolean delete(List<Playlist> playlists, String id) {
        Playlist p = find(playlists, id);
        if (p == null) return false;
        playlists.remove(p);
        return true;
    }

    /** Same track = same non-empty id, else title+artist case-insensitive
     *  (mirrors SongsSection.sameTrack). */
    static boolean sameTrack(Song a, Song b) {
        if (a == null || b == null) return false;
        if (!a.id.isEmpty() && !b.id.isEmpty()) return a.id.equals(b.id);
        return a.title.equalsIgnoreCase(b.title)
                && a.artist.equalsIgnoreCase(b.artist);
    }

    /** @return true when the song was added, false when it was a duplicate
     *          or the playlist does not exist. */
    public static boolean addSong(List<Playlist> playlists, String id, Song song) {
        Playlist p = find(playlists, id);
        if (p == null || song == null) return false;
        for (Song s : p.songs) if (sameTrack(s, song)) return false;
        p.songs.add(song);
        return true;
    }

    public static boolean removeSong(List<Playlist> playlists, String id, int index) {
        Playlist p = find(playlists, id);
        if (p == null || index < 0 || index >= p.songs.size()) return false;
        p.songs.remove(index);
        return true;
    }

    public static boolean move(List<Playlist> playlists, String id, int from, int to) {
        Playlist p = find(playlists, id);
        if (p == null) return false;
        if (from < 0 || from >= p.songs.size() || to < 0 || to >= p.songs.size()) return false;
        if (from == to) return true;
        Song s = p.songs.remove(from);
        p.songs.add(to, s);
        return true;
    }

    /* ------------------------------------------------------------ Android glue */

    public static List<Playlist> load(Context ctx) {
        // WS0: playlist data must never throw into a section constructor.
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return parse(prefs.getString(KEY, "[]"));
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }

    public static void save(Context ctx, List<Playlist> playlists) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY, serialize(playlists)).apply();
        } catch (Throwable ignored) {}
    }
}
