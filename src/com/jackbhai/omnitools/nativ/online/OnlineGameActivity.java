package com.jackbhai.omnitools.nativ.online;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jackbhai.omnitools.nativ.Glass;
import com.jackbhai.omnitools.nativ.Sfx;

/**
 * Online game player — our glass frame chrome around the GamePix player.
 * The embed page is loaded as the WebView's top-level page (the documented
 * WebView pattern; no intermediate iframe of ours). The game canvas, and
 * the ads inside it, are GamePix's and are not restyled — our chrome ends
 * at the canvas edge.
 */
public final class OnlineGameActivity extends Activity {

    public static final String EXTRA_NAMESPACE = "og_namespace";
    public static final String EXTRA_TITLE = "og_title";
    public static final String EXTRA_ORIENTATION = "og_orientation";

    private final Handler ui = new Handler(Looper.getMainLooper());

    private WebView web;
    private LinearLayout topBar;
    private FrameLayout loadingOverlay;
    private FrameLayout errorOverlay;
    private View customView;
    private FrameLayout customHolder;
    private boolean fullscreen = false;
    private boolean failed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String ns = getIntent().getStringExtra(EXTRA_NAMESPACE);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String orientation = getIntent().getStringExtra(EXTRA_ORIENTATION);
        if (title == null || title.isEmpty()) title = "Online Game";
        if (ns == null || ns.isEmpty()) { finish(); return; }

        if ("landscape".equals(orientation)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else if ("portrait".equals(orientation)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        OnlineGames.recordRecent(this, ns);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Glass.BG_DEEP);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- glass top bar: title, Fullscreen toggle, Close ----
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackground(Glass.panel(this, 0));
        Glass.pad(topBar, this, 12, 8, 12, 8);
        TextView heading = Glass.text(this, title, 16, Glass.TEXT, true);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        topBar.addView(heading, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView fsBtn = Glass.text(this, "Fullscreen", 13.5f, Glass.NEON_CYAN, true);
        fsBtn.setBackground(Glass.neonPill(this, Glass.NEON_CYAN));
        Glass.pad(fsBtn, this, 12, 6, 12, 6);
        fsBtn.setOnClickListener(v -> { Sfx.tap(); setFullscreen(!fullscreen); });
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.leftMargin = (int) Glass.dp(this, 8);
        topBar.addView(fsBtn, flp);

        TextView closeBtn = Glass.text(this, "Close", 13.5f, Glass.TEXT, true);
        closeBtn.setBackground(Glass.neonPill(this, Glass.NEON_VIOLET));
        Glass.pad(closeBtn, this, 12, 6, 12, 6);
        closeBtn.setOnClickListener(v -> { Sfx.tap(); finish(); });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = (int) Glass.dp(this, 8);
        topBar.addView(closeBtn, clp);
        col.addView(topBar);

        TextView label = Glass.text(this, "Online game \u2014 needs internet",
                11.5f, Glass.TEXT_DIM, false);
        Glass.pad(label, this, 12, 5, 12, 5);
        col.addView(label);

        // ---- the game itself ----
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (failed) return;
                ui.postDelayed(() -> {
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                }, 600);
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest req,
                                        WebResourceError err) {
                if (req != null && req.isForMainFrame()) showError();
            }
            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest req,
                                            WebResourceResponse resp) {
                if (req != null && req.isForMainFrame()
                        && resp != null && resp.getStatusCode() >= 400) showError();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customHolder == null) return;
                customView = view;
                customHolder.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                customHolder.setVisibility(View.VISIBLE);
            }
            @Override
            public void onHideCustomView() {
                if (customHolder == null || customView == null) return;
                customHolder.removeView(customView);
                customView = null;
                customHolder.setVisibility(View.GONE);
            }
        });
        col.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Holder for HTML5 fullscreen video/canvas requests from the game.
        customHolder = new FrameLayout(this);
        customHolder.setBackgroundColor(Color.BLACK);
        customHolder.setVisibility(View.GONE);
        root.addView(customHolder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- branded glass loading overlay ----
        loadingOverlay = new FrameLayout(this);
        loadingOverlay.setBackgroundColor(Color.argb(190, 7, 11, 20));
        LinearLayout loadPanel = new LinearLayout(this);
        loadPanel.setOrientation(LinearLayout.VERTICAL);
        loadPanel.setGravity(Gravity.CENTER);
        loadPanel.setBackground(Glass.panelStrong(this, 18));
        Glass.pad(loadPanel, this, 26, 22, 26, 22);
        TextView loadTitle = Glass.text(this, title, 16, Glass.TEXT, true);
        loadTitle.setGravity(Gravity.CENTER);
        loadPanel.addView(loadTitle);
        TextView loadMsg = Glass.text(this, "Loading game\u2026", 13.5f, Glass.TEXT_DIM, false);
        loadMsg.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lmlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lmlp.topMargin = (int) Glass.dp(this, 6);
        loadPanel.addView(loadMsg, lmlp);
        FrameLayout.LayoutParams loadLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        loadLp.gravity = Gravity.CENTER;
        loadLp.leftMargin = (int) Glass.dp(this, 32);
        loadLp.rightMargin = (int) Glass.dp(this, 32);
        loadingOverlay.addView(loadPanel, loadLp);
        root.addView(loadingOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- glass error card ----
        errorOverlay = new FrameLayout(this);
        errorOverlay.setBackgroundColor(Color.argb(190, 7, 11, 20));
        errorOverlay.setVisibility(View.GONE);
        LinearLayout errPanel = new LinearLayout(this);
        errPanel.setOrientation(LinearLayout.VERTICAL);
        errPanel.setGravity(Gravity.CENTER);
        errPanel.setBackground(Glass.panelStrong(this, 18));
        Glass.pad(errPanel, this, 26, 22, 26, 22);
        TextView errMsg = Glass.text(this,
                "Couldn't load this game \u2014 check connection", 15, Glass.TEXT, true);
        errMsg.setGravity(Gravity.CENTER);
        errPanel.addView(errMsg);
        TextView errClose = Glass.text(this, "Close", 14, Glass.TEXT, true);
        errClose.setBackground(Glass.neonPill(this, Glass.NEON_VIOLET));
        Glass.pad(errClose, this, 18, 8, 18, 8);
        errClose.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = (int) Glass.dp(this, 14);
        errPanel.addView(errClose, elp);
        FrameLayout.LayoutParams errLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errLp.gravity = Gravity.CENTER;
        errLp.leftMargin = (int) Glass.dp(this, 32);
        errLp.rightMargin = (int) Glass.dp(this, 32);
        errorOverlay.addView(errPanel, errLp);
        root.addView(errorOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        web.loadUrl(OnlineGames.embedUrl(ns));
    }

    private void showError() {
        failed = true;
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
        if (errorOverlay != null) errorOverlay.setVisibility(View.VISIBLE);
    }

    private void setFullscreen(boolean on) {
        fullscreen = on;
        if (topBar != null) topBar.setVisibility(on ? View.GONE : View.VISIBLE);
        View decor = getWindow().getDecorView();
        if (on) {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    public void onBackPressed() {
        if (fullscreen) { setFullscreen(false); return; }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
