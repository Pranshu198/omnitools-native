package com.jackbhai.omnitools.nativ.games;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jackbhai.omnitools.nativ.Glass;

/** Hosts one native game: glass background, title bar with back button,
 *  game view filling the rest. Intent extras: EXTRA_ID, EXTRA_TITLE. */
public final class GameActivity extends Activity {
    public static final String EXTRA_ID = "game_id";
    public static final String EXTRA_TITLE = "game_title";

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        String id = getIntent().getStringExtra(EXTRA_ID);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null) title = "Game";

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

        View game = NativeGames.createView(this, id);
        if (game == null) {
            TextView msg = Glass.text(this, "This game is not available natively yet.", 14, Glass.TEXT_DIM, false);
            Glass.pad(msg, this, 16, 16, 16, 16);
            root.addView(msg);
        } else {
            root.addView(game, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        setContentView(root);
    }
}
