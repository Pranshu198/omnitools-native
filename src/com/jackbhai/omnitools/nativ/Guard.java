package com.jackbhai.omnitools.nativ;

import android.content.Context;
import android.os.Debug;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Runtime tamper signals: debugger attachment, tracers, and the common
 * hooking frameworks (Frida / Xposed / Substrate).
 *
 * Policy, deliberately conservative so the genuine build can never trip
 * it: checks run once and are cached; only STRONG signals trip (a
 * debugger actually attached, a non-zero TracerPid, hook-framework
 * libraries actually mapped into this process, a Frida server binary
 * actually on disk). The one network probe — Frida's default port — is
 * never sufficient on its own; it only counts together with a mapped
 * Frida library or the server binary, because an unrelated app could
 * legitimately hold a port.
 *
 * On a trip, the SENSITIVE PATHS stop (search + playback resolve in the
 * Songs/TV/Downloader sections check Guard.sensitiveBlocked). The app
 * does not crash, loop, or brick itself — it simply refuses to do the
 * work a repacker would want it for.
 */
public final class Guard {

    private Guard() {}

    private static volatile Boolean tripped = null;
    private static volatile Boolean dexBad = null;

    /** Strong-signal tamper check. Cached after the first run. */
    public static boolean tampered(Context c) {
        Boolean t = tripped;
        if (t != null) return t;
        boolean bad = false;
        try {
            if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) bad = true;
            if (!bad && tracerPid() > 0) bad = true;
            boolean mapsHook = false;
            if (!bad) {
                mapsHook = mapsContainHook();
                if (mapsHook) bad = true;
            }
            if (!bad) {
                boolean serverOnDisk = new File("/data/local/tmp/frida-server").exists()
                        || new File("/data/local/tmp/re.frida.server").exists();
                if (serverOnDisk && (mapsHook || fridaPortOpen())) bad = true;
            }
        } catch (Throwable ignored) {
            bad = false; // any check erroring must never lock the genuine app
        }
        tripped = bad;
        return bad;
    }

    private static int tracerPid() {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("TracerPid:")) {
                    return Integer.parseInt(line.substring(10).trim());
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private static boolean mapsContainHook() {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            int n = 0;
            while ((line = r.readLine()) != null && n++ < 4000) {
                String low = line.toLowerCase(java.util.Locale.US);
                if (low.contains("frida") || low.contains("xposed")
                        || low.contains("substrate") || low.contains("lsposed")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean fridaPortOpen() {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress("127.0.0.1", 27042), 60);
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            try { s.close(); } catch (Throwable ignored) {}
        }
    }

    /* ------------------------------------------------ dex integrity */

    /**
     * SHA-256 over the APK's classes*.dex entries (in name order) followed
     * by resources.arsc — exactly the values sealed into the native
     * library by the two-pass build. Computed on a background thread;
     * until it finishes (or if it cannot run) the answer is "not bad".
     */
    public static void verifyDexAsync(final Context c) {
        if (dexBad != null) return;
        new Thread(() -> {
            boolean bad = false;
            try {
                String hex = apkCodeHash(c);
                if (hex != null) bad = !Sec.dexOk(hex);
            } catch (Throwable ignored) {
                bad = false;
            }
            dexBad = bad;
        }, "dex-verify").start();
    }

    static String apkCodeHash(Context c) throws Exception {
        String source = c.getApplicationInfo().sourceDir;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (ZipFile z = new ZipFile(source)) {
            List<String> dexNames = new ArrayList<>();
            java.util.Enumeration<? extends ZipEntry> e = z.entries();
            while (e.hasMoreElements()) {
                String name = e.nextElement().getName();
                if (name.matches("classes\\d*\\.dex")) dexNames.add(name);
            }
            Collections.sort(dexNames);
            byte[] buf = new byte[65536];
            for (String name : dexNames) {
                ZipEntry entry = z.getEntry(name);
                if (entry == null) return null;
                try (InputStream in = z.getInputStream(entry)) {
                    int n;
                    while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
                }
            }
            ZipEntry arsc = z.getEntry("resources.arsc");
            if (arsc == null) return null;
            try (InputStream in = z.getInputStream(arsc)) {
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
        }
        byte[] d = md.digest();
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** True when a sensitive path (search / resolve / playback) must stop. */
    public static boolean sensitiveBlocked(Context c) {
        if (tampered(c)) return true;
        Boolean d = dexBad;
        return d != null && d;
    }
}
