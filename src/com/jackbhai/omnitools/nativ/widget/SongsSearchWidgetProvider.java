package com.jackbhai.omnitools.nativ.widget;

import android.content.Context;
import android.widget.RemoteViews;

/** Songs Search widget (4x1): a glass search bar; tapping it opens the
 *  app on the Songs section (MainActivity extra "open_section"="songs"). */
public class SongsSearchWidgetProvider extends BaseWidgetProvider {
    @Override protected RemoteViews build(Context c, int id) {
        return WidgetViews.buildSearch(c, id);
    }
}
