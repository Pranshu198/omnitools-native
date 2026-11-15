import com.jackbhai.omnitools.nativ.PlaylistStore;
import com.jackbhai.omnitools.nativ.Song;

import java.util.ArrayList;
import java.util.List;

/** Pure-JVM tests for PlaylistStore's org.json core. */
public class PlaylistTestMain {

    private static int pass = 0, fail = 0;

    private static void check(String name, boolean cond) {
        if (cond) { pass++; System.out.println("PASS " + name); }
        else { fail++; System.out.println("FAIL " + name); }
    }

    private static Song song(String id, String title, String artist) {
        Song s = new Song();
        s.id = id; s.title = title; s.artist = artist;
        s.album = "Alb"; s.stream = "https://x/" + title;
        return s;
    }

    public static void main(String[] args) {
        List<PlaylistStore.Playlist> lists = new ArrayList<>();

        // create
        PlaylistStore.Playlist a = PlaylistStore.create(lists, "Chill");
        PlaylistStore.Playlist b = PlaylistStore.create(lists, "Gym");
        check("create adds to list", lists.size() == 2);
        check("create generates distinct non-empty ids",
                !a.id.isEmpty() && !b.id.isEmpty() && !a.id.equals(b.id));
        check("create keeps name", "Chill".equals(a.name));

        // find
        check("find by id", PlaylistStore.find(lists, a.id) == a);
        check("find missing -> null", PlaylistStore.find(lists, "nope") == null);

        // rename
        check("rename works", PlaylistStore.rename(lists, a.id, "Chill Vibes")
                && "Chill Vibes".equals(a.name));
        check("rename missing -> false", !PlaylistStore.rename(lists, "nope", "X"));

        // add + dedupe by id
        check("add song", PlaylistStore.addSong(lists, a.id, song("s1", "One", "A1")));
        check("dedupe by id", !PlaylistStore.addSong(lists, a.id, song("s1", "One (alt)", "Other"))
                && a.songs.size() == 1);
        // dedupe by title+artist (case-insensitive) when ids are empty
        check("add second song", PlaylistStore.addSong(lists, a.id, song("", "Two", "A2")));
        check("dedupe by title+artist case-insensitive",
                !PlaylistStore.addSong(lists, a.id, song("", "two", "a2"))
                        && a.songs.size() == 2);
        check("different title+artist with empty id added",
                PlaylistStore.addSong(lists, a.id, song("", "Three", "A3"))
                        && a.songs.size() == 3);
        check("add to missing playlist -> false",
                !PlaylistStore.addSong(lists, "nope", song("z", "Z", "Z")));

        // remove
        check("remove song", PlaylistStore.removeSong(lists, a.id, 0)
                && a.songs.size() == 2 && "Two".equals(a.songs.get(0).title));
        check("remove out of range -> false", !PlaylistStore.removeSong(lists, a.id, 9));

        // move / reorder
        PlaylistStore.addSong(lists, a.id, song("s9", "Four", "A4")); // [Two, Three, Four]
        check("move reorders", PlaylistStore.move(lists, a.id, 2, 0)
                && "Four".equals(a.songs.get(0).title)
                && "Two".equals(a.songs.get(1).title)
                && "Three".equals(a.songs.get(2).title));
        check("move out of range -> false", !PlaylistStore.move(lists, a.id, 0, 7));

        // serialize -> parse round-trip equality
        String json = PlaylistStore.serialize(lists);
        List<PlaylistStore.Playlist> back = PlaylistStore.parse(json);
        check("round-trip count", back.size() == lists.size());
        boolean equal = back.size() == lists.size();
        if (equal) {
            for (int i = 0; i < lists.size(); i++) {
                PlaylistStore.Playlist x = lists.get(i), y = back.get(i);
                if (!x.id.equals(y.id) || !x.name.equals(y.name)
                        || x.songs.size() != y.songs.size()) { equal = false; break; }
                for (int j = 0; j < x.songs.size(); j++) {
                    Song p = x.songs.get(j), q = y.songs.get(j);
                    if (!p.id.equals(q.id) || !p.title.equals(q.title)
                            || !p.artist.equals(q.artist) || !p.stream.equals(q.stream)) {
                        equal = false; break;
                    }
                }
            }
        }
        check("round-trip deep equality", equal);
        check("re-serialize stable", PlaylistStore.serialize(back).equals(json));

        // delete
        check("delete works", PlaylistStore.delete(lists, b.id) && lists.size() == 1);
        check("delete missing -> false", !PlaylistStore.delete(lists, "nope"));

        // corrupt input -> empty list, no crash
        check("corrupt json -> empty", PlaylistStore.parse("not json {{{").isEmpty());
        check("null -> empty", PlaylistStore.parse(null).isEmpty());
        check("empty string -> empty", PlaylistStore.parse("").isEmpty());
        check("non-array json -> empty", PlaylistStore.parse("{\"a\":1}").isEmpty());
        check("array with junk entries tolerated",
                PlaylistStore.parse("[42,{\"id\":\"x\",\"name\":\"Ok\",\"songs\":[]}]").size() == 1);

        System.out.println("RESULT pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
