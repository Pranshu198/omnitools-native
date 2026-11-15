package com.jackbhai.omnitools.nativ.widget;

import android.content.Context;
import android.widget.RemoteViews;

/** Music Player widget — Mini (4x1): art thumb, title/artist, transport. */
public class PlayerMiniWidgetProvider extends BaseWidgetProvider {
    @Override protected RemoteViews build(Context c, int id) {
        return WidgetViews.buildPlayerMini(c, id);
    }
    @Override protected boolean refreshOnNowPlaying() { return true; }
}
