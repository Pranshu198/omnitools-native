package com.jackbhai.omnitools.nativ;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.security.MessageDigest;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

/**
 * The native shell: a dark ambient background with neon glows inside a
 * BlurTarget, one section visible at a time, and the glass bottom nav
 * (a BlurView hosting the neon-wave GlassNavView) blurring whatever is
 * behind it — live, via Dimezis BlurView (RenderEffect on API 31+,
 * OpenGL on 29-30, RenderScript below).
 *
 * Startup, hardened (WS0, v3.3 — v3.2 died instantly on the user's
 * Realme 13 Pro before any UI appeared):
 *  - NOTHING on the launch path may throw, exit, or block. Every stage
 *    (sounds, signature gate, dex thread, blur shell, section creation,
 *    update check) is wrapped; every check fails OPEN.
 *  - The signature refusal screen is shown ONLY when the check cleanly
 *    returns false — any throwable on that path reads as "genuine".
 *  - A crash-loop breaker (prefs "omni_launch_guard") counts launches
 *    that start but never reach a stable first frame, and progressively
 *    disables the risky extras — blur first, then the security threads,
 *    then sounds — until the app opens plain. A stable launch resets it.
 */
public class MainActivity extends Activity {

    /** Request code for the backup-restore document picker (MenuSection). */
    public static final int REQ_RESTORE_BACKUP = 9031;

    /** The root BlurTarget, exposed so section-level glass (the Songs
     *  player bar) can blur against the same backdrop. Null whenever the
     *  plain (no-blur) shell is in use — consumers must null-check, and
     *  SongsSection already does. */
    public static BlurTarget blurTarget;

    /** Full-screen ambient backdrop: deep gradient + two soft neon glows. */
    static final class AmbientView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        AmbientView(Activity a) { super(a); }
        @Override protected void onDraw(android.graphics.Canvas cv) {
            int w = getWidth(), h = getHeight();
            p.setShader(new android.graphics.LinearGradient(0, 0, 0, h,
                    new int[]{Color.rgb(7, 11, 20), Color.rgb(11, 18, 34),
                            Color.rgb(8, 12, 24)},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, p);
            p.setShader(new RadialGradient(w * 0.12f, h * 0.06f, w * 0.75f,
                    Color.argb(64, 0, 229, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, p);
            p.setShader(new RadialGradient(w * 0.95f, h * 1.02f, w * 0.85f,
                    Color.argb(70, 179, 136, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, p);
            p.setShader(null);
        }
    }

    private final View[] sections = new View[5];
    private FrameLayout content;
    private GlassNavView nav;
    private int current = -1;

    /* ------------------------------------------------ WS0 launch guard */

    private static final String GUARD_PREFS = "omni_launch_guard";
    private boolean useBlur = true;
    private boolean minimalMode = false;
    private boolean bootMarkedStable = false;

    /** Reads the strike count and arms the boot marker. A previous
     *  launch that set the marker but never marked stable counts as one
     *  strike. Fully guarded: the guard itself must never throw. */
    private int armBootGuard() {
        try {
            android.content.SharedPreferences g =
                    getSharedPreferences(GUARD_PREFS, MODE_PRIVATE);
            int strikes = g.getBoolean("boot_started", false)
                    ? g.getInt("strikes", 0) + 1 : g.getInt("strikes", 0);
            g.edit().putBoolean("boot_started", true)
                    .putInt("strikes", strikes).apply();
            return strikes;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** Marks this launch stable (called ~1 s after the first section is
     *  shown, so a crash during the first draw still counts as a failed
     *  launch) and resets the strike counter. */
    private void markBootStable() {
        if (bootMarkedStable) return;
        bootMarkedStable = true;
        try {
            getSharedPreferences(GUARD_PREFS, MODE_PRIVATE).edit()
                    .putBoolean("boot_started", false)
                    .putInt("strikes", 0).apply();
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 0) Crash-loop breaker first: degrade by strikes from earlier
        //    failed launches. strikes>=1: no blur. >=2: also skip the
        //    security calls entirely. >=3: minimal mode (no sounds).
        int strikes = armBootGuard();
        useBlur = strikes < 1;
        minimalMode = strikes >= 3;
        boolean skipSecurity = strikes >= 2;

        // 1) Sounds — a sound problem must never kill the launch.
        if (!minimalMode) {
            try { Sfx.init(this); } catch (Throwable ignored) {}
        }

        // 2) Signature self-check — FAIL OPEN on any throwable; the
        //    refusal screen appears ONLY on a clean `false`.
        boolean genuine = true;
        if (!skipSecurity) {
            try { genuine = Sec.sigOk(signerDigest()); }
            catch (Throwable ignored) { genuine = true; }
        }
        if (!genuine) {
            try { showRefusal(); } catch (Throwable ignored) {}
            markBootStable();
            return;
        }
        // 3) Dex integrity (async; gates sensitive paths only, and even
        //    starting the thread is guarded).
        if (!skipSecurity) {
            try { Guard.verifyDexAsync(this); } catch (Throwable ignored) {}
        }

        try {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.POST_NOTIFICATIONS}, 7);
            }
        } catch (Throwable ignored) {}

        FrameLayout root = new FrameLayout(this);
        int navH = (int) Glass.dp(this, 88);
        content = new FrameLayout(this);
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        contentLp.bottomMargin = navH;

        nav = new GlassNavView(this);
        FrameLayout.LayoutParams stripLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, navH);
        stripLp.gravity = Gravity.BOTTOM;

        boolean blurred = false;
        if (useBlur) {
            try {
                blurTarget = new BlurTarget(this);
                blurTarget.addView(new AmbientView(this), new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                blurTarget.addView(content, contentLp);
                root.addView(blurTarget, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

                BlurView navBlur = new BlurView(this);
                navBlur.addView(nav, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                root.addView(navBlur, stripLp);
                navBlur.setupWith(blurTarget)
                        .setFrameClearDrawable(new ColorDrawable(Color.rgb(7, 11, 20)))
                        .setBlurRadius(20f)
                        .setOverlayColor(Color.argb(30, 10, 15, 26));
                blurred = true;
            } catch (Throwable ignored) {
                blurred = false;
                blurTarget = null;
            }
        }
        if (!blurred) {
            // Plain shell: same layout contract, no BlurView anywhere.
            root.setBackgroundColor(Color.rgb(7, 11, 20));
            root.addView(new AmbientView(this), new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            root.addView(content, contentLp);
            FrameLayout strip = new FrameLayout(this);
            strip.setBackgroundColor(Color.rgb(10, 15, 26));
            strip.addView(nav, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            root.addView(strip, stripLp);
        }

        setContentView(root);

        nav.setOnTab(idx -> {
            try { Sfx.tab(); } catch (Throwable ignored) {}
            showSection(idx);
        });
        int initial = tabForSection(getIntent() == null ? null
                : getIntent().getStringExtra("open_section"));
        showSection(initial);
        nav.select(initial, false);

        // Automatic update check (throttled to once per day, silent on
        // failure; itself best-effort, and the call is guarded too).
        try {
            com.jackbhai.omnitools.nativ.update.UpdateChecker.maybeCheck(this);
        } catch (Throwable ignored) {}

        // The launch counts as stable once the UI has survived ~1 s —
        // late enough that a first-draw crash still registers a strike.
        content.postDelayed(this::markBootStable, 1000);
    }

    /** Forwards the backup-restore picker result to BackupManager.
     *  Note: after a restore the running sections hold in-memory copies
     *  of the old values, so the user should reopen sections / restart
     *  the app to see the restored values. */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_RESTORE_BACKUP && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            com.jackbhai.omnitools.nativ.backup.BackupManager.importAsync(this, data.getData());
        }
    }

    /** Widget deep links: a home-screen widget tap arrives with an
     *  "open_section" extra (songs/tv/downloader/games/menu). With the
     *  widgets' CLEAR_TOP flags a running instance may be reused, so the
     *  routing lives in onNewIntent as well as onCreate. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (nav == null) return; // refused copy: no UI to route
        showSection(tabForSection(intent == null ? null
                : intent.getStringExtra("open_section")));
    }

    private static int tabForSection(String s) {
        if (s == null) return GlassNavView.TAB_SONGS;
        switch (s) {
            case "tv":         return GlassNavView.TAB_TV;
            case "downloader": return GlassNavView.TAB_DOWNLOADER;
            case "games":      return GlassNavView.TAB_GAMES;
            case "menu":       return GlassNavView.TAB_MENU;
            case "songs":
            default:           return GlassNavView.TAB_SONGS;
        }
    }

    /** SHA-256 (lowercase hex) of this app's signing certificate, or null
     *  when it cannot be read (Sec treats null as fail-open). */
    private String signerDigest() {
        try {
            PackageManager pm = getPackageManager();
            Signature sig;
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo pi = pm.getPackageInfo(getPackageName(),
                        PackageManager.GET_SIGNING_CERTIFICATES);
                Signature[] sigs = pi.signingInfo.getApkContentsSigners();
                if (sigs == null || sigs.length == 0) return null;
                sig = sigs[0];
            } else {
                PackageInfo pi = pm.getPackageInfo(getPackageName(),
                        PackageManager.GET_SIGNATURES);
                if (pi.signatures == null || pi.signatures.length == 0) return null;
                sig = pi.signatures[0];
            }
            byte[] d = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray());
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable e) {
            return null;
        }
    }

    /** The refusal screen for a repacked / re-signed copy. Plain, final,
     *  and shown instead of — never on top of — the real UI. */
    private void showRefusal() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setBackgroundColor(Color.rgb(7, 11, 20));
        Glass.pad(col, this, 28, 28, 28, 28);
        TextView t = Glass.text(this, "This copy of OmniTools is not genuine",
                19, Glass.TEXT, true);
        t.setGravity(Gravity.CENTER);
        col.addView(t);
        TextView s = Glass.text(this,
                "Its signature does not match the official release, so it may "
                + "have been modified or re-packed by someone else. "
                + "Please install the official build instead.",
                14, Glass.TEXT_DIM, false);
        s.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = (int) Glass.dp(this, 12);
        col.addView(s, slp);
        Button close = new Button(this);
        close.setText("Close");
        close.setTextColor(Glass.TEXT);
        close.setBackground(Glass.neonPill(this, Glass.NEON_CYAN));
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = (int) Glass.dp(this, 20);
        col.addView(close, clp);
        setContentView(col);
    }

    private View sectionAt(int tab) {
        if (sections[tab] != null) return sections[tab];
        View v;
        // WS0: one broken section must never kill the app — substitute a
        // plain placeholder and let the other tabs work.
        try {
            switch (tab) {
                case GlassNavView.TAB_TV:         v = new TvSection(this).view(); break;
                case GlassNavView.TAB_DOWNLOADER: v = new DownloaderSection(this).view(); break;
                case GlassNavView.TAB_GAMES:      v = new GamesSection(this).view(); break;
                case GlassNavView.TAB_MENU:       v = new MenuSection(this).view(); break;
                case GlassNavView.TAB_SONGS:
                default:                          v = new SongsSection(this).view(); break;
            }
        } catch (Throwable t) {
            v = sectionPlaceholder();
        }
        sections[tab] = v;
        content.addView(v, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return v;
    }

    /** Fallback view for a section whose construction failed. */
    private View sectionPlaceholder() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        Glass.pad(col, this, 28, 28, 28, 28);
        TextView t = Glass.text(this,
                "This section could not start. Please try another tab, "
                + "or reopen the app.", 14.5f, Glass.TEXT_DIM, false);
        t.setGravity(Gravity.CENTER);
        col.addView(t);
        return col;
    }

    private void showSection(int tab) {
        if (tab == current) return;
        current = tab;
        for (int i = 0; i < sections.length; i++) {
            if (sections[i] != null) sections[i].setVisibility(View.GONE);
        }
        sectionAt(tab).setVisibility(View.VISIBLE);
        if (nav != null) nav.select(tab, true);
    }
}
