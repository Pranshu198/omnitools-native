package com.jackbhai.omnitools.nativ;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jackbhai.omnitools.nativ.games.NativeGames;
import com.jackbhai.omnitools.nativ.online.OnlineGamesActivity;

/**
 * Games — Phase-1 bridge, labelled honestly as such in the UI.
 *
 * The 50 mini-games are HTML/JS inside the bundled PWA; they are listed
 * here in a native glass grid (names + groups from the PWA's own games
 * registry), and tapping one opens the bundled arcade in the native-hosted
 * frame. Native ports of individual games are later-phase work.
 */
public final class GamesSection {

    private final Activity act;
    private View root;

    public GamesSection(Activity act) {
        this.act = act;
        build();
    }

    public View view() { return root; }

    private void build() {
        Context c = act;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        Glass.pad(col, c, 14, 12, 14, 8);

        col.addView(Glass.text(c, "Games", 24, Glass.TEXT, true));
        TextView sub = Glass.text(c, Registry.GAMES.length
                + " mini games — reflex, puzzle, brain, arcade and cards. "
                + NativeGames.nativeIds().length
                + " favourites now run natively and fully offline; "
                + "the rest open in the built-in arcade.",
                12.5f, Glass.TEXT_DIM, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = (int) Glass.dp(c, 6);
        col.addView(sub, subLp);

        // Online Games (GamePix) — a separate web catalogue, needs internet.
        LinearLayout online = new LinearLayout(c);
        online.setOrientation(LinearLayout.VERTICAL);
        online.setBackground(Glass.panelStrong(c, 16));
        Glass.pad(online, c, 14, 14, 14, 14);
        online.addView(Glass.text(c, "Online Games", 16.5f, Glass.TEXT, true));
        TextView onlineSub = Glass.text(c,
                "Online game \u2014 needs internet \u00B7 provided by GamePix",
                12, Glass.NEON_CYAN, false);
        LinearLayout.LayoutParams osLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        osLp.topMargin = (int) Glass.dp(c, 4);
        online.addView(onlineSub, osLp);
        online.setOnClickListener(v -> {
            Sfx.tap();
            act.startActivity(new Intent(act, OnlineGamesActivity.class));
        });
        LinearLayout.LayoutParams onLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        onLp.topMargin = (int) Glass.dp(c, 12);
        col.addView(online, onLp);

        RecyclerView grid = new RecyclerView(c);
        grid.setLayoutManager(new GridLayoutManager(c, 2));
        grid.setAdapter(new Adapter());
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        glp.topMargin = (int) Glass.dp(c, 8);
        col.addView(grid, glp);
        root = col;
    }

    private final class Holder extends RecyclerView.ViewHolder {
        final TextView name, group;
        Holder(View v, TextView n, TextView g) { super(v); name = n; group = g; }
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @Override public Holder onCreateViewHolder(ViewGroup parent, int vt) {
            Context c = parent.getContext();
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_VERTICAL);
            cell.setBackground(Glass.panel(c, 16));
            Glass.pad(cell, c, 14, 14, 14, 14);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) Glass.dp(c, 5);
            lp.bottomMargin = (int) Glass.dp(c, 5);
            lp.leftMargin = (int) Glass.dp(c, 5);
            lp.rightMargin = (int) Glass.dp(c, 5);
            cell.setLayoutParams(lp);
            TextView n = Glass.text(c, "", 15.5f, Glass.TEXT, true);
            TextView g = Glass.text(c, "", 12, Glass.NEON_CYAN, false);
            cell.addView(n);
            cell.addView(g);
            return new Holder(cell, n, g);
        }

        @Override public void onBindViewHolder(Holder h, int pos) {
            Registry.Game g = Registry.GAMES[pos];
            h.name.setText(g.name);
            h.group.setText(g.group);
            h.itemView.setOnClickListener(v -> {
                if (NativeGames.isNative(g.id)) {
                    NativeGames.launch(act, g.id, g.name);
                    return;
                }
                Intent it = new Intent(act, WebFrameActivity.class);
                it.putExtra(WebFrameActivity.EXTRA_ROUTE, "games");
                it.putExtra(WebFrameActivity.EXTRA_TITLE, g.name);
                act.startActivity(it);
            });
        }

        @Override public int getItemCount() { return Registry.GAMES.length; }
    }
}
