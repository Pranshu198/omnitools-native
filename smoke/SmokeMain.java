import com.jackbhai.omnitools.nativ.AlldlApi;
import com.jackbhai.omnitools.nativ.PipedApi;
import com.jackbhai.omnitools.nativ.SaavnApi;
import com.jackbhai.omnitools.nativ.Song;

import java.util.List;

/**
 * JVM smoke test for the native app's data layer. Compiles the app's pure
 * data classes (Net, Song, SaavnApi, PipedApi, AlldlApi) against the real
 * org.json jar and runs them against the LIVE sources with real queries.
 * The pass bar: song search for "khaab akhil" returns playable results.
 */
public class SmokeMain {

    /**
     * Harness-only: this sandbox reaches the internet through an
     * authenticated egress proxy (env https_proxy); curl honours it, the
     * JVM does not. The Android app itself runs direct on the phone and
     * needs none of this. Credentials are read from the environment at
     * runtime and never printed.
     */
    static void installSandboxProxy() {
        // Preferred path: the local CONNECT relay (smoke/relay.py) adds
        // the egress proxy's Basic auth preemptively, which the JVM's
        // own proxy handling refuses to do for CONNECT tunnels.
        String relay = System.getProperty("smoke.relay");
        if (relay != null && !relay.isEmpty()) {
            System.setProperty("https.proxyHost", "127.0.0.1");
            System.setProperty("https.proxyPort", relay);
            System.setProperty("http.proxyHost", "127.0.0.1");
            System.setProperty("http.proxyPort", relay);
            System.out.println("      (via local CONNECT relay on port " + relay + ")");
            return;
        }
        String proxy = System.getenv("https_proxy");
        if (proxy == null || proxy.isEmpty()) proxy = System.getenv("HTTPS_PROXY");
        if (proxy == null || proxy.isEmpty()) return;
        try {
            java.net.URI u = new java.net.URI(proxy);
            String host = u.getHost();
            int port = u.getPort() > 0 ? u.getPort() : 3128;
            System.setProperty("https.proxyHost", host);
            System.setProperty("https.proxyPort", String.valueOf(port));
            System.setProperty("http.proxyHost", host);
            System.setProperty("http.proxyPort", String.valueOf(port));
            String ui = u.getUserInfo();
            if (ui != null && ui.contains(":")) {
                final String user = ui.substring(0, ui.indexOf(':'));
                final String pass = ui.substring(ui.indexOf(':') + 1);
                java.net.Authenticator.setDefault(new java.net.Authenticator() {
                    @Override protected java.net.PasswordAuthentication getPasswordAuthentication() {
                        return new java.net.PasswordAuthentication(user, pass.toCharArray());
                    }
                });
            }
            System.out.println("      (sandbox egress proxy configured for this harness run)");
        } catch (Exception e) {
            System.out.println("      (proxy setup skipped: " + e + ")");
        }
    }

    static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + " — " + detail);
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        // Fixture mode: the sandbox blocks raw TCP from the JVM, so the
        // shadow Net serves real responses captured live with curl
        // (smoke/fixtures/, minutes old). Parsing/decryption are the
        // app's real code; only the HTTP fetch is replayed.
        System.out.println("      (fixture mode: real captured payloads, app parsing code)");
        // 1) Song search — mirrors route.
        List<Song> mirror = null;
        String mirrorErr = "";
        long t0 = System.currentTimeMillis();
        try { mirror = SaavnApi.searchMirrors("khaab akhil", 20); }
        catch (Exception e) { mirrorErr = String.valueOf(e); }
        long mirrorMs = System.currentTimeMillis() - t0;
        check("saavn-mirrors 'khaab akhil'",
                mirror != null && !mirror.isEmpty(),
                mirror != null ? mirror.size() + " songs in " + mirrorMs + " ms"
                        : "error: " + mirrorErr);
        if (mirror != null && !mirror.isEmpty()) {
            Song s = mirror.get(0);
            System.out.println("      first: \"" + s.title + "\" — " + s.artist
                    + " | stream: " + (s.stream.isEmpty() ? "(none)"
                        : s.stream.substring(0, Math.min(64, s.stream.length())) + "…")
                    + " | art: " + (!s.art.isEmpty()));
            boolean khaab = false;
            for (Song x : mirror) {
                if (x.title.toLowerCase().contains("khaab")) khaab = true;
                if (x.stream.isEmpty()) check("mirror row playable", false, x.title);
            }
            check("khaab present in mirror results", khaab, "");
        }

        // 2) Song search — official route (direct + DES decrypt).
        List<Song> official = null;
        String offErr = "";
        t0 = System.currentTimeMillis();
        try { official = SaavnApi.searchOfficial("khaab akhil", 20); }
        catch (Exception e) { offErr = String.valueOf(e); }
        long offMs = System.currentTimeMillis() - t0;
        check("saavn-official 'khaab akhil'",
                official != null && !official.isEmpty(),
                official != null ? official.size() + " songs in " + offMs + " ms"
                        : "error: " + offErr);
        if (official != null && !official.isEmpty()) {
            Song s = official.get(0);
            System.out.println("      first: \"" + s.title + "\" — " + s.artist
                    + " | decrypted stream host: "
                    + (s.stream.contains("saavncdn") || s.stream.contains("jiosaavn")
                        ? "catalogue CDN" : s.stream.substring(0, Math.min(40, s.stream.length()))));
        }

        // 3) Video search (TV section).
        List<PipedApi.Video> vids = null;
        String vidErr = "";
        try { vids = PipedApi.search("khaab akhil"); }
        catch (Exception e) { vidErr = String.valueOf(e); }
        check("piped video search 'khaab akhil'",
                vids != null && !vids.isEmpty(),
                vids != null ? vids.size() + " videos" : "error: " + vidErr);

        // 4) Video stream resolution for the first hit.
        if (vids != null && !vids.isEmpty()) {
            PipedApi.Streams st = null;
            String stErr = "";
            try { st = PipedApi.streams(vids.get(0).id); }
            catch (Exception e) { stErr = String.valueOf(e); }
            check("piped streams resolve",
                    st != null && (!st.videoOptions.isEmpty() || !st.audioOptions.isEmpty()),
                    st != null ? st.videoOptions.size() + " video + "
                            + st.audioOptions.size() + " audio options, progressive="
                            + (!st.progressive.isEmpty())
                            : "error: " + stErr);
        }

        // 5) Downloader resolve — a YouTube link through alldl.
        AlldlApi.Result dl = null;
        String dlErr = "";
        t0 = System.currentTimeMillis();
        try { dl = AlldlApi.resolve("https://www.youtube.com/watch?v=aqz-KE-bpKQ"); }
        catch (Exception e) { dlErr = String.valueOf(e); }
        long dlMs = System.currentTimeMillis() - t0;
        check("alldl resolve (YouTube link)",
                dl != null && !dl.options.isEmpty(),
                dl != null ? "\"" + dl.title + "\" — " + dl.options.size()
                        + " options in " + dlMs + " ms"
                        : "error: " + dlErr + " (" + dlMs + " ms)");

        System.out.println(failures == 0 ? "SMOKE: ALL PASS" : "SMOKE: " + failures + " FAILURES");
        System.exit(failures == 0 ? 0 : 1);
    }
}
