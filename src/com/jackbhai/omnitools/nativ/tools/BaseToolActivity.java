package com.jackbhai.omnitools.nativ.tools;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jackbhai.omnitools.nativ.Glass;

/**
 * Shared scaffold for the native tools batch, mirroring GameActivity:
 * glass background, a title bar with a back pill, and a content column
 * the subclass fills via {@link #body()}. Intent extras EXTRA_ID /
 * EXTRA_TITLE come from {@link NativeTools#launch}.
 */
public abstract class BaseToolActivity extends Activity {

    private LinearLayout bodyCol;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        String title = getIntent() != null
                ? getIntent().getStringExtra(NativeTools.EXTRA_TITLE) : null;
        if (title == null) title = toolTitle();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Glass.BG_DEEP);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        Glass.pad(bar, this, 8, 10, 14, 6);

        TextView back = Glass.text(this, "< Back", 15, Glass.NEON_CYAN, true);
        back.setBackground(Glass.neonPill(this, Glass.NEON_CYAN));
        Glass.pad(back, this, 14, 7, 14, 7);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView heading = Glass.text(this, title, 19, Glass.TEXT, true);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.leftMargin = (int) Glass.dp(this, 12);
        bar.addView(heading, hlp);
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        bodyCol = new LinearLayout(this);
        bodyCol.setOrientation(LinearLayout.VERTICAL);
        Glass.pad(bodyCol, this, 14, 8, 14, 20);
        scroll.addView(bodyCol);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        buildUi();
    }

    /** Fallback title when launched without the EXTRA_TITLE extra. */
    protected abstract String toolTitle();

    /** Build the tool's UI into {@link #body()}. Called from onCreate. */
    protected abstract void buildUi();

    protected final LinearLayout body() { return bodyCol; }

    /** Neon pill button in the glass style. */
    protected TextView pill(String label, int neon) {
        TextView b = Glass.text(this, label, 15, Glass.TEXT, true);
        b.setBackground(Glass.neonPill(this, neon));
        b.setGravity(Gravity.CENTER);
        Glass.pad(b, this, 16, 10, 16, 10);
        return b;
    }

    /** Dim explanatory note. */
    protected TextView note(String s) {
        TextView t = Glass.text(this, s, 13, Glass.TEXT_DIM, false);
        Glass.pad(t, this, 2, 8, 2, 4);
        return t;
    }

    /** Section heading. */
    protected TextView heading(String s) {
        TextView t = Glass.text(this, s, 16, Glass.TEXT, true);
        Glass.pad(t, this, 2, 14, 2, 6);
        return t;
    }

    protected void addGap(int dp) {
        android.view.View v = new android.view.View(this);
        bodyCol.addView(v, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) Glass.dp(this, dp)));
    }

    protected void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /** True if the permission is already granted; otherwise requests it
     *  (result arrives in onRequestPermissionResult) and returns false. */
    protected boolean ensurePerm(String perm, int requestCode) {
        if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) return true;
        requestPermissions(new String[]{perm}, requestCode);
        return false;
    }
}
