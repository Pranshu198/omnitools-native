package com.jackbhai.omnitools.nativ.games;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;

/** Registry bridge for the native games batch. GamesSection routes taps
 *  here first; ids match Registry.GAMES ids exactly. */
public final class NativeGames {
    private static final String[] IDS = {
            "g2048", "snake", "mines", "memory", "ttt",
            "breakout", "flappy", "slide15", "simon", "reaction"
    };

    private NativeGames() {}

    public static boolean isNative(String registryId) {
        if (registryId == null) return false;
        for (String id : IDS) if (id.equals(registryId)) return true;
        return false;
    }

    public static String[] nativeIds() { return IDS.clone(); }

    public static void launch(Activity act, String registryId, String title) {
        Intent it = new Intent(act, GameActivity.class);
        it.putExtra(GameActivity.EXTRA_ID, registryId);
        it.putExtra(GameActivity.EXTRA_TITLE, title);
        act.startActivity(it);
    }

    /** Factory used by GameActivity. Null for unknown ids. */
    public static View createView(Context c, String registryId) {
        if (registryId == null) return null;
        switch (registryId) {
            case "g2048": return new Game2048View(c);
            case "snake": return new SnakeView(c);
            case "mines": return new MinesView(c);
            case "memory": return new MemoryView(c);
            case "ttt": return new TttView(c);
            case "breakout": return new BreakoutView(c);
            case "flappy": return new FlappyView(c);
            case "slide15": return new SlideView(c);
            case "simon": return new SimonView(c);
            case "reaction": return new ReactionView(c);
            default: return null;
        }
    }
}
