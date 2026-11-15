package com.jackbhai.omnitools.nativ.online;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jackbhai.omnitools.nativ.Glass;
import com.jackbhai.omnitools.nativ.Img;
import com.jackbhai.omnitools.nativ.Sfx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Online Games catalogue — our glass UI over the GamePix feed.
 * Header credits GamePix; every card carries an "Online" chip; search,
 * category chips, favourites and recently-played are ours and stored
 * locally. Offline (or before the first fetch lands) the section shows
 * the cache, or the built-in verified starter list.
 */
public final class OnlineGamesActivity extends Activity {

    private static final String FILTER_ALL = "__all";
    private static final String FILTER_FAV = "__fav";
    private static final String FILTER_RECENT = "__recent";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private final List<OnlineGames.Game> all = new ArrayList<>();
    private final List<OnlineGames.Game> shown = new ArrayList<>();
    private final Adapter adapter = new Adapter();

    private LinearLayout chipRow;
    private TextView status;
    private String filter = FILTER_ALL;
    private String query = "";
    private boolean showingFallback = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context c = this;

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Glass.BG_DEEP);
        Glass.pad(col, c, 14, 12, 14, 8);

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Glass.text(c, "< Back", 15, Glass.NEON_CYAN, true);
        back.setBackground(Glass.neonPill(c, Glass.NEON_CYAN));
        Glass.pad(back, c, 14, 7, 14, 7);
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView heading = Glass.text(c, "Online Games", 20, Glass.TEXT, true);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.leftMargin = (int) Glass.dp(c, 12);
        top.addView(heading, hlp);
        col.addView(top);

        TextView title = Glass.text(c, "Online Games \u2014 provided by GamePix",
                15.5f, Glass.TEXT, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = (int) Glass.dp(c, 12);
        col.addView(title, tlp);

        TextView sub = Glass.text(c,
                "Needs internet \u00B7 Games show ads\nOnline game \u2014 needs internet",
                12.5f, Glass.TEXT_DIM, false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = (int) Glass.dp(c, 4);
        col.addView(sub, slp);

        EditText search = new EditText(c);
        search.setHint("Search games\u2026");
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setTextColor(Glass.TEXT);
        search.setHintTextColor(Glass.TEXT_DIM);
        search.setBackground(Glass.panel(c, 14));
        Glass.pad(search, c, 14, 10, 14, 10);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qlp.topMargin = (int) Glass.dp(c, 10);
        col.addView(search, qlp);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int n) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int n) {}
            @Override public void afterTextChanged(Editable s) {
                query = s == null ? "" : s.toString().trim().toLowerCase(Locale.US);
                applyFilter();
            }
        });

        chipRow = new LinearLayout(c);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView chipScroll = new HorizontalScrollView(c);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.addView(chipRow);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = (int) Glass.dp(c, 10);
        col.addView(chipScroll, clp);

        status = Glass.text(c, "Loading games\u2026", 12.5f, Glass.TEXT_DIM, false);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stlp.topMargin = (int) Glass.dp(c, 8);
        col.addView(status, stlp);

        RecyclerView grid = new RecyclerView(c);
        grid.setLayoutManager(new GridLayoutManager(c, 2));
        grid.setAdapter(adapter);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        glp.topMargin = (int) Glass.dp(c, 6);
        col.addView(grid, glp);

        setContentView(col);

        // Show something immediately: cache first, starter list otherwise.
        List<OnlineGames.Game> cached = OnlineGames.loadCached(this);
        if (!cached.isEmpty()) {
            all.addAll(cached);
        } else {
            all.addAll(OnlineGames.fallback());
            showingFallback = true;
        }
        rebuildChips();
        applyFilter();

        if (!OnlineGames.cacheFresh(this)) {
            status.setText("Loading latest games\u2026");
            net.execute(() -> {
                try {
                    final List<OnlineGames.Game> fresh = OnlineGames.fetchFresh(this);
                    ui.post(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        all.clear();
                        all.addAll(fresh);
                        showingFallback = false;
                        rebuildChips();
                        applyFilter();
                    });
                } catch (Exception e) {
                    ui.post(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        applyFilter(); // refreshes the status line for what we have
                    });
                }
            });
        }

        if (!OnlineGames.noticeSeen(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Online Games")
                    .setMessage("Online games are provided by GamePix and include ads "
                            + "inside the games. OmniTools doesn't control the ads.")
                    .setPositiveButton("Got it", (d, w) -> OnlineGames.markNoticeSeen(this))
                    .setOnCancelListener(d -> OnlineGames.markNoticeSeen(this))
                    .show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Favourites / recent may have changed in the player.
        if (filter.equals(FILTER_FAV) || filter.equals(FILTER_RECENT)) applyFilter();
        else adapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        net.shutdownNow();
        super.onDestroy();
    }

    // -------------------------------------------------------------- filter

    private void rebuildChips() {
        Context c = this;
        chipRow.removeAllViews();
        addChip(c, FILTER_ALL, "All");
        addChip(c, FILTER_FAV, "Favourites");
        addChip(c, FILTER_RECENT, "Recent");
        Set<String> cats = new TreeSet<>();
        for (OnlineGames.Game g : all) {
            if (!g.category.isEmpty()) cats.add(g.category);
        }
        for (String cat : cats) addChip(c, cat, pretty(cat));
    }

    private void addChip(Context c, final String value, String label) {
        TextView chip = Glass.text(c, label, 13, Glass.TEXT, false);
        Glass.pad(chip, c, 13, 7, 13, 7);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = (int) Glass.dp(c, 8);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> {
            Sfx.tap();
            filter = value;
            refreshChipStyles();
            applyFilter();
        });
        chip.setTag(value);
        chipRow.addView(chip);
        styleChip(chip, value.equals(filter));
    }

    private void refreshChipStyles() {
        for (int i = 0; i < chipRow.getChildCount(); i++) {
            View v = chipRow.getChildAt(i);
            if (v instanceof TextView && v.getTag() instanceof String) {
                styleChip((TextView) v, filter.equals(v.getTag()));
            }
        }
    }

    private void styleChip(TextView chip, boolean selected) {
        if (selected) {
            chip.setBackground(Glass.neonPill(this, Glass.NEON_CYAN));
            chip.setTextColor(Glass.TEXT);
        } else {
            chip.setBackground(Glass.panel(this, 999));
            chip.setTextColor(Glass.TEXT_DIM);
        }
    }

    private void applyFilter() {
        shown.clear();
        Set<String> favs = OnlineGames.favourites(this);
        Map<String, OnlineGames.Game> byNs = new LinkedHashMap<>();
        for (OnlineGames.Game g : all) byNs.put(g.namespace, g);

        List<OnlineGames.Game> base = new ArrayList<>();
        if (FILTER_FAV.equals(filter)) {
            for (OnlineGames.Game g : all) if (favs.contains(g.namespace)) base.add(g);
        } else if (FILTER_RECENT.equals(filter)) {
            for (String ns : OnlineGames.recent(this)) {
                OnlineGames.Game g = byNs.get(ns);
                if (g != null) base.add(g);
            }
        } else if (FILTER_ALL.equals(filter)) {
            base.addAll(all);
        } else {
            for (OnlineGames.Game g : all) if (filter.equals(g.category)) base.add(g);
        }
        for (OnlineGames.Game g : base) {
            if (query.isEmpty() || g.title.toLowerCase(Locale.US).contains(query)) {
                shown.add(g);
            }
        }
        adapter.notifyDataSetChanged();

        if (status != null) {
            if (FILTER_FAV.equals(filter) && shown.isEmpty() && query.isEmpty()) {
                status.setText("No favourites yet \u2014 tap the star on any game.");
            } else if (FILTER_RECENT.equals(filter) && shown.isEmpty() && query.isEmpty()) {
                status.setText("Nothing played yet \u2014 your recent games will appear here.");
            } else {
                status.setText(shown.size() + (shown.size() == 1 ? " game" : " games")
                        + (showingFallback ? " \u00B7 starter list (offline)" : ""));
            }
        }
    }

    private void openGame(OnlineGames.Game g) {
        Sfx.tap();
        Intent it = new Intent(this, OnlineGameActivity.class);
        it.putExtra(OnlineGameActivity.EXTRA_NAMESPACE, g.namespace);
        it.putExtra(OnlineGameActivity.EXTRA_TITLE, g.title);
        it.putExtra(OnlineGameActivity.EXTRA_ORIENTATION, g.orientation);
        startActivity(it);
    }

    private static String pretty(String category) {
        if (category == null || category.isEmpty()) return "Other";
        return category.substring(0, 1).toUpperCase(Locale.US) + category.substring(1);
    }

    // -------------------------------------------------------------- adapter

    private final class Holder extends RecyclerView.ViewHolder {
        final ImageView banner;
        final TextView title, star;
        final TextView catChip, onlineChip;
        Holder(View v, ImageView b, TextView t, TextView s, TextView cc, TextView oc) {
            super(v); banner = b; title = t; star = s; catChip = cc; onlineChip = oc;
        }
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @Override public Holder onCreateViewHolder(ViewGroup parent, int vt) {
            Context c = parent.getContext();
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setBackground(Glass.panel(c, 16));
            Glass.pad(cell, c, 10, 10, 10, 10);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) Glass.dp(c, 5);
            lp.bottomMargin = (int) Glass.dp(c, 5);
            lp.leftMargin = (int) Glass.dp(c, 5);
            lp.rightMargin = (int) Glass.dp(c, 5);
            cell.setLayoutParams(lp);

            ImageView banner = new ImageView(c);
            banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            banner.setBackgroundColor(Color.rgb(18, 26, 40));
            cell.addView(banner, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) Glass.dp(c, 96)));

            LinearLayout titleRow = new LinearLayout(c);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.TOP);
            TextView t = Glass.text(c, "", 14.5f, Glass.TEXT, true);
            t.setMaxLines(2);
            titleRow.addView(t, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView star = Glass.text(c, "\u2606", 20, Glass.TEXT_DIM, true);
            Glass.pad(star, c, 6, 0, 2, 0);
            titleRow.addView(star);
            LinearLayout.LayoutParams trlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            trlp.topMargin = (int) Glass.dp(c, 8);
            cell.addView(titleRow, trlp);

            LinearLayout chips = new LinearLayout(c);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setGravity(Gravity.CENTER_VERTICAL);
            TextView cat = Glass.text(c, "", 11, Glass.NEON_CYAN, false);
            cat.setBackground(Glass.neonPill(c, Glass.NEON_CYAN));
            Glass.pad(cat, c, 9, 3, 9, 3);
            chips.addView(cat);
            TextView online = Glass.text(c, "Online", 11, Glass.NEON_VIOLET, false);
            online.setBackground(Glass.neonPill(c, Glass.NEON_VIOLET));
            Glass.pad(online, c, 9, 3, 9, 3);
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            olp.leftMargin = (int) Glass.dp(c, 6);
            chips.addView(online, olp);
            LinearLayout.LayoutParams chlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chlp.topMargin = (int) Glass.dp(c, 8);
            cell.addView(chips, chlp);

            return new Holder(cell, banner, t, star, cat, online);
        }

        @Override public void onBindViewHolder(Holder h, int pos) {
            final OnlineGames.Game g = shown.get(pos);
            h.title.setText(g.title);
            h.catChip.setText(pretty(g.category));
            h.catChip.setVisibility(g.category.isEmpty() ? View.GONE : View.VISIBLE);
            Img.load(!g.banner.isEmpty() ? g.banner : g.image, h.banner);
            boolean fav = OnlineGames.isFavourite(OnlineGamesActivity.this, g.namespace);
            h.star.setText(fav ? "\u2605" : "\u2606");
            h.star.setTextColor(fav ? Glass.NEON_CYAN : Glass.TEXT_DIM);
            h.star.setOnClickListener(v -> {
                Sfx.tap();
                boolean now = !OnlineGames.isFavourite(
                        OnlineGamesActivity.this, g.namespace);
                OnlineGames.setFavourite(OnlineGamesActivity.this, g.namespace, now);
                if (FILTER_FAV.equals(filter)) applyFilter();
                else notifyDataSetChanged();
            });
            h.itemView.setOnClickListener(v -> openGame(g));
        }

        @Override public int getItemCount() { return shown.size(); }
    }
}
