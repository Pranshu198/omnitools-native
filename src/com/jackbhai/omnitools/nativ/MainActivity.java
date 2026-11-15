package com.jackbhai.omnitools.nativ;

import android.app.Activity;
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
 * Startup security, in order:
 *  1. Signature self-check — the running app's signing certificate must
 *     match the OmniTools release signer (checked in Sec, natively when
 *     libomnisec is present). A positive mismatch refuses the main UI
 *     with a plain notice. An unreadable certificate fails OPEN: a
 *     PackageManager quirk must never lock out the genuine build.
 *  2. Dex integrity + anti-hook checks (Guard) run async and gate only
 *     the sensitive paths (search / resolve / playback), never the UI.
 */
public class MainActivity extends Activity {

    /** The root BlurTarget, exposed so section-level glass (the Songs
     *  player bar) can blur against the same backdrop. */
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Sfx.init(this);

        // 1) Signature self-check — refuse the main UI on a real mismatch.
        if (!Sec.sigOk(signerDigest())) {
            showRefusal();
            return;
        }
        // 2) Dex integrity (async; gates sensitive paths only).
        Guard.verifyDexAsync(this);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    android.Manifest.permission.POST_NOTIFICATIONS}, 7);
        }

        FrameLayout root = new FrameLayout(this);

        blurTarget = new BlurTarget(this);
        blurTarget.addView(new AmbientView(this), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        int navH = (int) Glass.dp(this, 88);
        content = new FrameLayout(this);
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        contentLp.bottomMargin = navH;
        blurTarget.addView(content, contentLp);
        root.addView(blurTarget, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        BlurView navBlur = new BlurView(this);
        nav = new GlassNavView(this);
        navBlur.addView(nav, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams stripLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, navH);
        stripLp.gravity = Gravity.BOTTOM;
        root.addView(navBlur, stripLp);
        navBlur.setupWith(blurTarget)
                .setFrameClearDrawable(new ColorDrawable(Color.rgb(7, 11, 20)))
                .setBlurRadius(20f)
                .setOverlayColor(Color.argb(30, 10, 15, 26));

        setContentView(root);

        nav.setOnTab(idx -> {
            Sfx.tab();
            showSection(idx);
        });
        showSection(GlassNavView.TAB_SONGS);
        nav.select(GlassNavView.TAB_SONGS, false);
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
        } catch (Exception e) {
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
        switch (tab) {
            case GlassNavView.TAB_TV:         v = new TvSection(this).view(); break;
            case GlassNavView.TAB_DOWNLOADER: v = new DownloaderSection(this).view(); break;
            case GlassNavView.TAB_GAMES:      v = new GamesSection(this).view(); break;
            case GlassNavView.TAB_MENU:       v = new MenuSection(this).view(); break;
            case GlassNavView.TAB_SONGS:
            default:                          v = new SongsSection(this).view(); break;
        }
        sections[tab] = v;
        content.addView(v, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return v;
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
