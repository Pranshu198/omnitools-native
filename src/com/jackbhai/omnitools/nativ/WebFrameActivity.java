package com.jackbhai.omnitools.nativ;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

/**
 * Hosts one bundled PWA page (Games arcade, or any Menu tool) inside the
 * native shell. The whole PWA dist ships in assets (synced at build time
 * from the omnitools repo, exactly like the v2 WebView app) and is served
 * by WebViewAssetLoader under the fixed https origin
 * https://appassets.androidplatform.net — so the bundled tools run fully
 * offline with their own storage, and hash routes (#games, #bus, …) work
 * exactly as they do in the browser.
 */
public class WebFrameActivity extends Activity {

    public static final String EXTRA_ROUTE = "route";
    public static final String EXTRA_TITLE = "title";
    private static final String BASE = "https://appassets.androidplatform.net/";

    private WebView web;
    private WebViewAssetLoader loader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String route = getIntent().getStringExtra(EXTRA_ROUTE);
        if (route == null) route = "";
        setTitle(getIntent().getStringExtra(EXTRA_TITLE));

        loader = new WebViewAssetLoader.Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler("/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view, android.webkit.WebResourceRequest req) {
                return loader.shouldInterceptRequest(req.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest req) {
                Uri u = req.getUrl();
                // Keep app-internal navigation inside; hand real external
                // links to the system browser.
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                if ("http".equals(u.getScheme()) || "https".equals(u.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                    return true;
                }
                return false;
            }
        });
        setContentView(web, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.loadUrl(BASE + "index.html#" + route);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
