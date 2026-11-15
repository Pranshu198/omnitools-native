package com.jackbhai.omnitools.nativ;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;

/**
 * Security bridge. The sensitive comparisons — "is this the genuine
 * signing certificate", "is the dex untouched" — and the endpoint-string
 * decoding live in a small native library (libomnisec.so, NDK/JNI) when
 * it is present, so the expected values are not sitting in the dex as
 * readable strings. Every entry point falls back to a Java
 * implementation when the library cannot be loaded (for example on an
 * ABI we do not ship), with ONE deliberate exception: the dex-integrity
 * expectation exists only in native code, because sealing it in Java
 * would put the expected hash inside the very dex being hashed
 * (self-referential). Without the library, the dex check is skipped
 * (fail-open), never faked.
 *
 * R8 note: this class and its native methods are name-kept in
 * proguard-native.pro — JNI binds by name, obfuscating them would break
 * the bridge.
 */
public final class Sec {

    private Sec() {}

    private static final boolean LOADED;
    static {
        boolean ok = false;
        try {
            System.loadLibrary("omnisec");
            ok = true;
        } catch (Throwable ignored) {}
        LOADED = ok;
    }

    public static boolean nativeAvailable() { return LOADED; }

    /* ------------------------------------------------ native surface */
    private static native String nEp(String encoded);
    private static native boolean nSig(String sha256HexLower);
    private static native boolean nDex(String sha256HexLower);
    private static native String nToken(long timestampSeconds, String sigHex);

    /* ------------------------------------------------ endpoint decode */
    /** Decode an obfuscated endpoint string (native preferred). */
    public static String decode(String b64) {
        if (LOADED) {
            try {
                String s = nEp(b64);
                if (s != null && !s.isEmpty()) return s;
            } catch (Throwable ignored) {}
        }
        return Net.ep(b64);
    }

    /* ------------------------------------------------ signature check */
    /**
     * The release signer's certificate SHA-256, Base64-encoded so it is
     * not a plain string in the dex (Java fallback copy; the native copy
     * is the primary). Decodes to the hex digest of the OmniTools
     * release certificate — the same signer as the WebView app.
     */
    private static final String EXPECTED_SIG_B64 =
            "ZjgyOWMxMGEwNGY2MDAwNDEwMWI2NWQxZWY2MzVlMWRhY2I3MmQ4YWE5OTQwODY5ZjJlZDJiYWY0NzhhMTc2NA==";

    /** True when the running app's signer matches the release signer. */
    public static boolean sigOk(String actualSha256HexLower) {
        if (actualSha256HexLower == null || actualSha256HexLower.isEmpty()) {
            return true; // unreadable signing info: fail-open, never a false lock
        }
        if (LOADED) {
            try { return nSig(actualSha256HexLower); }
            catch (Throwable ignored) {}
        }
        return Net.ep(EXPECTED_SIG_B64).equalsIgnoreCase(actualSha256HexLower);
    }

    /* ------------------------------------------------ dex integrity */
    /**
     * True when classes*.dex + resources.arsc match the values sealed in
     * the native library at build time. Native-only by design (see class
     * doc); without the library this returns true (check skipped).
     */
    public static boolean dexOk(String combinedSha256HexLower) {
        if (!LOADED) return true;
        if (combinedSha256HexLower == null || combinedSha256HexLower.isEmpty()) {
            return true; // could not compute: fail-open
        }
        try { return nDex(combinedSha256HexLower); }
        catch (Throwable ignored) { return true; }
    }

    /* ------------------------------------------------ server token */
    /**
     * A minted server token: send {@code timestamp} as the X-Omni-Ts
     * header and {@code token} as X-Omni-Token to token-gated worker
     * routes (e.g. /native/ping). The worker recomputes the same
     * HMAC-SHA256 over "{timestamp}.{signerCertSha256HexLower}" with
     * its mirror copy of the sealed key and rejects anything stale
     * (over 5 minutes of skew) or mismatched.
     */
    public static final class Token {
        public final long timestamp;
        public final String token;

        Token(long timestamp, String token) {
            this.timestamp = timestamp;
            this.token = token;
        }
    }

    /**
     * Mint a server token for the current moment, or null when one
     * cannot be minted (native library absent, signer unreadable).
     * Fail-soft by design: callers treat null as "no token available"
     * and simply skip the gated route, never as an app error. The
     * signer digest is obtained exactly the way MainActivity obtains
     * it for {@link #sigOk}.
     */
    public static Token token(Context c) {
        if (!LOADED || c == null) return null;
        String sig = signerDigest(c);
        if (sig == null || sig.isEmpty()) return null;
        long ts = System.currentTimeMillis() / 1000L;
        try {
            String t = nToken(ts, sig);
            if (t == null || t.isEmpty()) return null;
            return new Token(ts, t);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** SHA-256 (lowercase hex) of this app's signing certificate, or
     *  null when it cannot be read. Same code path as
     *  MainActivity.signerDigest(). */
    private static String signerDigest(Context c) {
        try {
            PackageManager pm = c.getPackageManager();
            String pkg = c.getPackageName();
            Signature sig;
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo pi = pm.getPackageInfo(pkg,
                        PackageManager.GET_SIGNING_CERTIFICATES);
                Signature[] sigs = pi.signingInfo.getApkContentsSigners();
                if (sigs == null || sigs.length == 0) return null;
                sig = sigs[0];
            } else {
                PackageInfo pi = pm.getPackageInfo(pkg,
                        PackageManager.GET_SIGNATURES);
                if (pi.signatures == null || pi.signatures.length == 0) return null;
                sig = pi.signatures[0];
            }
            byte[] d = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray());
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
