package com.jackbhai.omnitools.nativ.audio;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.io.File;

/**
 * Read-only tag sheet for LOCAL files, via jaudiotagger 3.0.1
 * (net.jthink:jaudiotagger, LGPL — the jar in libs/ is used unmodified).
 *
 * EDIT is intentionally NOT shipped: the library's files are owned by
 * MediaStore / other apps under scoped storage, and a safe write needs
 * the temp-file + verify + replace dance against a file the app provably
 * owns. That cannot be proven for arbitrary device media from here, so
 * tags are view-only rather than risking the user's only copy.
 * Blocking — call from a worker thread.
 */
public final class TagView {

    public static final class TagInfo {
        public String title = "", artist = "", album = "", year = "",
                genre = "", track = "", composer = "", format = "";
        public long durationSec;
        public String bitrate = "";
        public boolean ok;

        public String describe() {
            StringBuilder sb = new StringBuilder();
            line(sb, "Title", title);
            line(sb, "Artist", artist);
            line(sb, "Album", album);
            line(sb, "Year", year);
            line(sb, "Genre", genre);
            line(sb, "Track", track);
            line(sb, "Composer", composer);
            line(sb, "Format", format);
            if (durationSec > 0) line(sb, "Duration", (durationSec / 60) + ":"
                    + String.format("%02d", durationSec % 60));
            line(sb, "Bitrate", bitrate);
            return sb.toString().trim();
        }

        private static void line(StringBuilder sb, String k, String v) {
            if (v != null && !v.trim().isEmpty()) {
                sb.append(k).append(": ").append(v.trim()).append('\n');
            }
        }
    }

    public static TagInfo read(File f) {
        TagInfo info = new TagInfo();
        if (f == null || !f.exists()) return info;
        try {
            AudioFile af = AudioFileIO.read(f);
            AudioHeader header = af.getAudioHeader();
            if (header != null) {
                info.format = header.getFormat();
                info.durationSec = header.getTrackLength();
                info.bitrate = header.getBitRate() + " kbps";
            }
            Tag tag = af.getTag();
            if (tag != null) {
                info.title = tag.getFirst(FieldKey.TITLE);
                info.artist = tag.getFirst(FieldKey.ARTIST);
                info.album = tag.getFirst(FieldKey.ALBUM);
                info.year = tag.getFirst(FieldKey.YEAR);
                info.genre = tag.getFirst(FieldKey.GENRE);
                info.track = tag.getFirst(FieldKey.TRACK);
                info.composer = tag.getFirst(FieldKey.COMPOSER);
            }
            info.ok = true;
        } catch (Exception e) {
            info.ok = false;
        }
        return info;
    }

    private TagView() {}
}
