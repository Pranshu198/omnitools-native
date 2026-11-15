package com.jackbhai.omnitools.nativ;

import org.json.JSONObject;

/** One playable song row, shaped like the PWA's catalogue rows. */
public final class Song {
    public String id = "";
    public String title = "";
    public String artist = "";
    public String album = "";
    public String year = "";
    public long durSec = 0;
    public String art = "";
    public String stream = "";

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id); o.put("title", title); o.put("artist", artist);
            o.put("album", album); o.put("year", year); o.put("dur", durSec);
            o.put("art", art); o.put("stream", stream);
        } catch (Exception ignored) {}
        return o;
    }

    public static Song fromJson(JSONObject o) {
        Song s = new Song();
        if (o == null) return s;
        s.id = o.optString("id", "");
        s.title = o.optString("title", "");
        s.artist = o.optString("artist", "");
        s.album = o.optString("album", "");
        s.year = o.optString("year", "");
        s.durSec = o.optLong("dur", 0);
        s.art = o.optString("art", "");
        s.stream = o.optString("stream", "");
        return s;
    }
}
