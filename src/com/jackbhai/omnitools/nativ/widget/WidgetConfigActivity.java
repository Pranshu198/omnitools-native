package com.jackbhai.omnitools.nativ.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.ArrayList;
import java.util.List;

/**
 * The single configuration activity for every OmniTools widget kind
 * (declared with the APPWIDGET_CONFIGURE intent-filter; each widget's
 * metadata names it via android:configure).
 *
 * Which kind is being configured is read from
 * AppWidgetManager.getAppWidgetInfo(id).provider. Common options —
 * neon accent (7 colours) and background transparency (0-90%) — are
 * offered for all kinds; player widgets also get a show-art toggle and
 * Quick Tools gets its tile picker (max 4). Choices persist to
 * "omni_widgets" keyed by widget id; Save immediately rebuilds that
 * widget, returns RESULT_OK with the widget id, and finishes.
 * RESULT_CANCELED is set up-front, per the platform contract, so
 * backing out removes a fresh placement.
 */
public class WidgetConfigActivity extends Activity {

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private String providerClass = "";
    private boolean isPlayer;
    private boolean isQuickTools;

    private int selectedAccent;
    private int transparency;
    private boolean showArt = true;
    private final List<CheckBox> toolBoxes = new ArrayList<>();
    private final List<View> swatches = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Platform contract: assume cancellation until Save succeeds.
        setResult(RESULT_CANCELED);

        widgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        AppWidgetProviderInfo info =
                AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId);
        if (info != null && info.provider != null) {
            providerClass = info.provider.getClassName();
        }
        isPlayer = providerClass.endsWith("PlayerMiniWidgetProvider")
                || providerClass.endsWith("PlayerBigWidgetProvider");
        isQuickTools = providerClass.endsWith("QuickToolsWidgetProvider");

        SharedPreferences p = WidgetPrefs.prefs(this);
        selectedAccent = p.getInt("accent_" + widgetId, WidgetPrefs.ACCENTS[0]);
        transparency = WidgetPrefs.transparencyFor(this, widgetId);
        showArt = p.getBoolean("showart_" + widgetId, true);

        buildUi();
    }

    // ------------------------------------------------------------------
    // UI (programmatic, glass-styled)
    // ------------------------------------------------------------------

    private LinearLayout.LayoutParams wrap(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) Glass.dp(this, topDp);
        return lp;
    }

    private TextView label(String s) {
        TextView t = Glass.text(this, s, 13, Glass.TEXT_DIM, true);
        return t;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Glass.BG_DEEP);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        Glass.pad(col, this, 22, 24, 22, 28);
        scroll.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        TextView title = Glass.text(this, "Widget settings", 21, Glass.TEXT, true);
        col.addView(title);
        String kindName = kindDisplayName();
        TextView sub = Glass.text(this,
                kindName.isEmpty() ? "OmniTools widget" : kindName + " widget",
                13, Glass.TEXT_DIM, false);
        col.addView(sub, wrap(4));

        // ---- Accent colour ----
        col.addView(label("ACCENT COLOUR"), wrap(22));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        col.addView(row, wrap(10));
        for (int i = 0; i < WidgetPrefs.ACCENTS.length; i++) {
            final int accent = WidgetPrefs.ACCENTS[i];
            View sw = new View(this);
            int sz = (int) Glass.dp(this, 42);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
            lp.rightMargin = (int) Glass.dp(this, 8);
            sw.setLayoutParams(lp);
            swatches.add(sw);
            row.addView(sw);
            sw.setContentDescription(WidgetPrefs.ACCENT_NAMES[i]);
        }
        refreshSwatches();
        final TextView accentName = Glass.text(this, accentNameOf(selectedAccent),
                12, Glass.TEXT_DIM, false);
        col.addView(accentName, wrap(6));
        for (View sw : swatches) {
            final View fsw = sw;
            sw.setOnClickListener(v -> {
                int idx = swatches.indexOf(fsw);
                if (idx >= 0) {
                    selectedAccent = WidgetPrefs.ACCENTS[idx];
                    accentName.setText(WidgetPrefs.ACCENT_NAMES[idx]);
                    refreshSwatches();
                }
            });
        }

        // ---- Background transparency ----
        col.addView(label("BACKGROUND TRANSPARENCY"), wrap(22));
        final TextView tVal = Glass.text(this, transparency + "%", 13, Glass.TEXT, false);
        col.addView(tVal, wrap(6));
        SeekBar seek = new SeekBar(this);
        seek.setMax(90);
        seek.setProgress(transparency);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int prog, boolean fromUser) {
                transparency = prog;
                tVal.setText(prog + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        col.addView(seek, wrap(2));

        // ---- Player: show art ----
        CheckBox artBox = null;
        if (isPlayer) {
            col.addView(label("MUSIC PLAYER"), wrap(22));
            artBox = checkBox("Show album art");
            artBox.setChecked(showArt);
            col.addView(artBox, wrap(8));
        }
        final CheckBox fArtBox = artBox;

        // ---- Quick Tools: tile picker ----
        if (isQuickTools) {
            col.addView(label("QUICK TOOLS (CHOOSE UP TO 4)"), wrap(22));
            String[] chosen = WidgetPrefs.toolsFor(this, widgetId);
            List<String> chosenList = new ArrayList<>();
            for (String s : chosen) chosenList.add(s);
            for (WidgetViews.ToolDef def : WidgetViews.TOOL_CATALOG) {
                CheckBox cb = checkBox(def.label);
                cb.setTag(def.key);
                cb.setChecked(chosenList.contains(def.key));
                cb.setOnCheckedChangeListener((button, checked) -> {
                    if (checked && countCheckedTools() > 4) {
                        button.setChecked(false);
                        Toast.makeText(this, "Up to 4 tools", Toast.LENGTH_SHORT).show();
                    }
                });
                toolBoxes.add(cb);
                col.addView(cb, wrap(4));
            }
        }

        // ---- Actions ----
        Button save = new Button(this);
        save.setText("Save and update widget");
        save.setTextColor(Glass.TEXT);
        save.setTypeface(Glass.font(this, true));
        save.setBackground(Glass.neonPill(this, Glass.NEON_CYAN));
        save.setOnClickListener(v -> saveAndFinish(fArtBox));
        col.addView(save, wrap(28));

        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setTextColor(Glass.TEXT_DIM);
        cancel.setBackground(Glass.panel(this, 999));
        cancel.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams clp = wrap(10);
        col.addView(cancel, clp);
    }

    private CheckBox checkBox(String text) {
        CheckBox cb = new CheckBox(this);
        cb.setText(text);
        cb.setTextSize(15);
        cb.setTextColor(Glass.TEXT);
        cb.setTypeface(Glass.font(this, false));
        return cb;
    }

    private int countCheckedTools() {
        int n = 0;
        for (CheckBox cb : toolBoxes) if (cb.isChecked()) n++;
        return n;
    }

    private void refreshSwatches() {
        for (int i = 0; i < swatches.size(); i++) {
            int accent = WidgetPrefs.ACCENTS[i];
            boolean sel = accent == selectedAccent;
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(accent);
            g.setStroke((int) Glass.dp(this, sel ? 3 : 1),
                    sel ? Color.WHITE : Color.argb(120, 255, 255, 255));
            swatches.get(i).setBackground(g);
        }
    }

    private String accentNameOf(int accent) {
        for (int i = 0; i < WidgetPrefs.ACCENTS.length; i++) {
            if (WidgetPrefs.ACCENTS[i] == accent) return WidgetPrefs.ACCENT_NAMES[i];
        }
        return "";
    }

    private String kindDisplayName() {
        if (providerClass.endsWith("PlayerMiniWidgetProvider")) return "Music Player (Mini)";
        if (providerClass.endsWith("PlayerBigWidgetProvider")) return "Music Player (Big)";
        if (providerClass.endsWith("VerseWidgetProvider")) return "Verse of the Day";
        if (providerClass.endsWith("QuickToolsWidgetProvider")) return "Quick Tools";
        if (providerClass.endsWith("SongsSearchWidgetProvider")) return "Songs Search";
        if (providerClass.endsWith("DownloadsWidgetProvider")) return "Downloads";
        return "";
    }

    // ------------------------------------------------------------------
    // Save
    // ------------------------------------------------------------------

    private void saveAndFinish(CheckBox artBox) {
        SharedPreferences.Editor e = WidgetPrefs.prefs(this).edit();
        e.putInt("accent_" + widgetId, selectedAccent);
        e.putInt("transp_" + widgetId, transparency);
        if (isPlayer) {
            e.putBoolean("showart_" + widgetId, artBox == null || artBox.isChecked());
        }
        if (isQuickTools) {
            StringBuilder sb = new StringBuilder();
            for (CheckBox cb : toolBoxes) {
                if (cb.isChecked()) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append((String) cb.getTag());
                }
            }
            e.putString("tools_" + widgetId, sb.toString());
        }
        e.apply();

        // Immediately refresh just this widget instance.
        try {
            android.widget.RemoteViews views =
                    WidgetViews.buildForProvider(this, widgetId, providerClass);
            if (views != null) {
                AppWidgetManager.getInstance(this).updateAppWidget(widgetId, views);
            }
        } catch (Exception ignored) {}

        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, result);
        finish();
    }
}
