package com.jackbhai.omnitools.nativ;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Menu — every other OmniTools tool, grouped by category exactly as the
 * PWA registry has them (110 tools; Music, Downloader, Games and Live TV
 * are the native sections in the bottom nav, so they are not repeated
 * here). Tapping a tool opens its bundled page in the native-hosted frame,
 * so nothing from OmniTools is missing in this app.
 */
public final class MenuSection {

    private final Activity act;
    private View root;
    private final Adapter adapter = new Adapter();
    private final List<Object> display = new ArrayList<>(); // String header | Registry.Tool

    public MenuSection(Activity act) {
        this.act = act;
        rebuild("");
        build();
    }

    public View view() { return root; }

    private void rebuild(String filter) {
        display.clear();
        Map<String, List<Registry.Tool>> byCat = new LinkedHashMap<>();
        for (Registry.Tool t : Registry.TOOLS) {
            if (!filter.isEmpty()
                    && !t.name.toLowerCase(Locale.US).contains(filter)
                    && !t.cat.toLowerCase(Locale.US).contains(filter)) continue;
            byCat.computeIfAbsent(t.cat, k -> new ArrayList<>()).add(t);
        }
        for (Map.Entry<String, List<Registry.Tool>> e : byCat.entrySet()) {
            display.add(e.getKey());
            display.addAll(e.getValue());
        }
    }

    private void build() {
        Context c = act;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        Glass.pad(col, c, 14, 12, 14, 8);

        col.addView(Glass.text(c, "All Tools", 24, Glass.TEXT, true));
        TextView sub = Glass.text(c, Registry.TOOLS.length
                + " more tools, grouped by category. TV, Downloader, Games and Songs "
                + "have their own tabs below.", 12.5f, Glass.TEXT_DIM, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = (int) Glass.dp(c, 6);
        col.addView(sub, subLp);

        // Settings row: UI-sound mute toggle (Kenney sounds, persisted).
        LinearLayout setRow = new LinearLayout(c);
        setRow.setOrientation(LinearLayout.HORIZONTAL);
        setRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView sndLabel = Glass.text(c, "UI sounds", 13.5f, Glass.TEXT, false);
        setRow.addView(sndLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView sndChip = Glass.text(c, Sfx.muted() ? "Off" : "On",
                13, Glass.TEXT, false);
        sndChip.setBackground(Glass.neonPill(c, Glass.NEON_CYAN));
        Glass.pad(sndChip, c, 14, 6, 14, 6);
        sndChip.setOnClickListener(v -> {
            if (Sfx.muted()) {
                Sfx.setMuted(false);
                Sfx.toggleOn();
                sndChip.setText("On");
            } else {
                Sfx.toggleOff();
                Sfx.setMuted(true);
                sndChip.setText("Off");
            }
        });
        setRow.addView(sndChip);
        LinearLayout.LayoutParams setLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        setLp.topMargin = (int) Glass.dp(c, 10);
        col.addView(setRow, setLp);

        // Settings row: Check for updates (chip shows current version).
        String versionName = "";
        try {
            PackageInfo pi = act.getPackageManager().getPackageInfo(act.getPackageName(), 0);
            if (pi.versionName != null) versionName = pi.versionName;
        } catch (Exception ignored) {}
        LinearLayout updRow = settingsRow(c, "Check for updates", versionName,
                v -> com.jackbhai.omnitools.nativ.update.UpdateChecker.checkNow(act));
        LinearLayout.LayoutParams updLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        updLp.topMargin = (int) Glass.dp(c, 10);
        col.addView(updRow, updLp);

        // Settings row: Back up data.
        LinearLayout backupRow = settingsRow(c, "Back up data", "Save",
                v -> com.jackbhai.omnitools.nativ.backup.BackupManager.exportAndShare(act));
        LinearLayout.LayoutParams backupLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        backupLp.topMargin = (int) Glass.dp(c, 10);
        col.addView(backupRow, backupLp);

        // Settings row: Restore backup. After a restore the running
        // sections hold in-memory copies, so the user should reopen
        // sections / restart the app to see restored values.
        LinearLayout restoreRow = settingsRow(c, "Restore backup", "Open",
                v -> {
                    Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    it.addCategory(Intent.CATEGORY_OPENABLE);
                    it.setType("application/json");
                    act.startActivityForResult(it, MainActivity.REQ_RESTORE_BACKUP);
                });
        LinearLayout.LayoutParams restoreLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        restoreLp.topMargin = (int) Glass.dp(c, 10);
        col.addView(restoreRow, restoreLp);

        EditText filter = new EditText(c);
        filter.setHint("Filter tools…");
        filter.setSingleLine(true);
        filter.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        filter.setTextColor(Glass.TEXT);
        filter.setHintTextColor(Glass.TEXT_DIM);
        filter.setBackground(Glass.panel(c, 14));
        Glass.pad(filter, c, 14, 10, 14, 10);
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fLp.topMargin = (int) Glass.dp(c, 10);
        col.addView(filter, fLp);
        filter.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int n) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int n) {
                rebuild(s.toString().trim().toLowerCase(Locale.US));
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        RecyclerView list = new RecyclerView(c);
        list.setLayoutManager(new LinearLayoutManager(c));
        list.setAdapter(adapter);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listLp.topMargin = (int) Glass.dp(c, 6);
        col.addView(list, listLp);
        root = col;
    }

    /** A settings row in the same style as the UI-sounds row: label on
     *  the left, neon pill chip on the right; row and chip both tappable. */
    private LinearLayout settingsRow(Context c, String label, String chipText,
                                     View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView labelView = Glass.text(c, label, 13.5f, Glass.TEXT, false);
        row.addView(labelView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView chip = Glass.text(c, chipText, 13, Glass.TEXT, false);
        chip.setBackground(Glass.neonPill(c, Glass.NEON_CYAN));
        Glass.pad(chip, c, 14, 6, 14, 6);
        chip.setOnClickListener(listener);
        row.addView(chip);
        row.setOnClickListener(listener);
        return row;
    }

    private final class Holder extends RecyclerView.ViewHolder {
        final TextView text;
        final boolean header;
        Holder(View v, TextView t, boolean h) { super(v); text = t; header = h; }
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @Override public int getItemViewType(int pos) {
            return display.get(pos) instanceof String ? 0 : 1;
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int vt) {
            Context c = parent.getContext();
            if (vt == 0) {
                TextView t = Glass.text(c, "", 13, Glass.NEON_VIOLET, true);
                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = (int) Glass.dp(c, 14);
                lp.bottomMargin = (int) Glass.dp(c, 4);
                t.setLayoutParams(lp);
                return new Holder(t, t, true);
            }
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Glass.panel(c, 14));
            Glass.pad(row, c, 14, 12, 14, 12);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) Glass.dp(c, 4);
            lp.bottomMargin = (int) Glass.dp(c, 4);
            row.setLayoutParams(lp);
            TextView t = Glass.text(c, "", 15, Glass.TEXT, false);
            row.addView(t, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView open = Glass.text(c, "Open", 12.5f, Glass.NEON_CYAN, true);
            row.addView(open);
            return new Holder(row, t, false);
        }

        @Override public void onBindViewHolder(Holder h, int pos) {
            Object item = display.get(pos);
            if (item instanceof String) {
                h.text.setText(((String) item).toUpperCase(Locale.US));
                return;
            }
            Registry.Tool t = (Registry.Tool) item;
            h.text.setText(t.name);
            h.itemView.setOnClickListener(v -> {
                // Batch 3 Track A: native tools launch natively; the rest
                // keep the bundled-page frame.
                if (com.jackbhai.omnitools.nativ.tools.NativeTools.isNative(t.id)) {
                    com.jackbhai.omnitools.nativ.tools.NativeTools.launch(act, t.id, t.name);
                    return;
                }
                Intent it = new Intent(act, WebFrameActivity.class);
                it.putExtra(WebFrameActivity.EXTRA_ROUTE, t.id);
                it.putExtra(WebFrameActivity.EXTRA_TITLE, t.name);
                act.startActivity(it);
            });
        }

        @Override public int getItemCount() { return display.size(); }
    }
}
