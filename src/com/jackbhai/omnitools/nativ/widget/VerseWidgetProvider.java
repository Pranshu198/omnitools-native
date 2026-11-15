package com.jackbhai.omnitools.nativ.widget;

import android.content.Context;
import android.widget.RemoteViews;

/** Verse of the Day widget (2x1 / 4x1): deterministic daily verse from
 *  the built-in offline public-domain pool; tap opens the app. */
public class VerseWidgetProvider extends BaseWidgetProvider {
    @Override protected RemoteViews build(Context c, int id) {
        return WidgetViews.buildVerse(c, id);
    }
}
