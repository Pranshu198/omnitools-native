package com.jackbhai.omnitools.nativ;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * The native shell: a dark ambient background with neon glows, one section
 * visible at a time, and the glass bottom nav with its neon wave.
 *
 * Glassmorphism, honestly implemented: the nav strip's backdrop is a real
 * blurred copy of the ambient background on Android 12+ (RenderEffect on
 * the strip's ImageView), and the translucent tint the GlassNavView draws
 * itself is the fallback below API 31 — the same two paths the design
 * brief specifies.
 */
public class MainActivity extends Activity {

    /** Full-screen ambient backdrop: deep gradient + two soft neon glows. */
    static final class AmbientView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        AmbientView(Activity a) { super(a); }
        @Override protected void onDraw(Canvas cv) {
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
    private AmbientView ambient;
    private ImageView blurStrip;
    private int current = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    android.Manifest.permission.POST_NOTIFICATIONS}, 7);
        }

        FrameLayout root = new FrameLayout(this);
        ambient = new AmbientView(this);
        root.addView(ambient, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        int navH = (int) Glass.dp(this, 88);
        content = new FrameLayout(this);
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        contentLp.bottomMargin = navH;
        root.addView(content, contentLp);

        FrameLayout strip = new FrameLayout(this);
        FrameLayout.LayoutParams stripLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, navH);
        stripLp.gravity = android.view.Gravity.BOTTOM;
        blurStrip = new ImageView(this);
        blurStrip.setScaleType(ImageView.ScaleType.FIT_XY);
        strip.addView(blurStrip, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        nav = new GlassNavView(this);
        strip.addView(nav, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(strip, stripLp);

        setContentView(root);

        nav.setOnTab(this::showSection);
        showSection(GlassNavView.TAB_SONGS);
        nav.select(GlassNavView.TAB_SONGS, false);

        root.post(this::buildBlurStrip);
    }

    /** Blur the ambient backdrop behind the nav strip (API 31+). */
    private void buildBlurStrip() {
        try {
            int w = ambient.getWidth();
            int navH = (int) Glass.dp(this, 88);
            int h = ambient.getHeight();
            if (w == 0 || h == 0) return;
            Bitmap bmp = Bitmap.createBitmap(w, navH, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            cv.translate(0, -(h - navH));
            ambient.draw(cv);
            blurStrip.setImageBitmap(bmp);
            if (Build.VERSION.SDK_INT >= 31) {
                blurStrip.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                        22f, 22f, Shader.TileMode.CLAMP));
            }
        } catch (Exception ignored) {
            // Translucent tint in GlassNavView is the fallback; absence of
            // the blur strip is cosmetic, never fatal.
        }
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
