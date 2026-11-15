package com.jackbhai.omnitools.nativ;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

/**
 * Shared glassmorphism styling: dark ambient base, translucent frosted
 * panels with a hairline light border, neon accents (cyan + violet).
 * Everything is built in code — no appcompat/material dependency.
 */
public final class Glass {

    public static final int BG_DEEP = Color.rgb(7, 11, 20);
    public static final int NEON_CYAN = Color.rgb(0, 229, 255);
    public static final int NEON_VIOLET = Color.rgb(179, 136, 255);
    public static final int TEXT = Color.rgb(235, 242, 250);
    public static final int TEXT_DIM = Color.rgb(160, 175, 195);
    public static final int GLASS_FILL = Color.argb(38, 255, 255, 255);
    public static final int GLASS_FILL_STRONG = Color.argb(56, 255, 255, 255);
    public static final int GLASS_BORDER = Color.argb(64, 255, 255, 255);

    private Glass() {}

    public static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    /** Frosted panel background: translucent fill, light border, soft corners. */
    public static GradientDrawable panel(Context c, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(c, radiusDp));
        g.setColor(GLASS_FILL);
        g.setStroke((int) Math.max(1, dp(c, 0.75f)), GLASS_BORDER);
        return g;
    }

    public static GradientDrawable panelStrong(Context c, float radiusDp) {
        GradientDrawable g = panel(c, radiusDp);
        g.setColor(GLASS_FILL_STRONG);
        return g;
    }

    /** Neon-tinted pill (chips, buttons). */
    public static GradientDrawable neonPill(Context c, int neon) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(c, 999));
        g.setColor(Color.argb(46, Color.red(neon), Color.green(neon), Color.blue(neon)));
        g.setStroke((int) Math.max(1, dp(c, 1)), Color.argb(190,
                Color.red(neon), Color.green(neon), Color.blue(neon)));
        return g;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    public static void pad(View v, Context c, int l, int t, int r, int b) {
        v.setPadding((int) dp(c, l), (int) dp(c, t), (int) dp(c, r), (int) dp(c, b));
    }
}
