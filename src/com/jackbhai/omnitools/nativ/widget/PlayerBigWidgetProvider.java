package com.jackbhai.omnitools.nativ.widget;

import android.content.Context;
import android.widget.RemoteViews;

/** Music Player widget — Big (4x2, resizable to 4x4): larger art,
 *  title/artist and the transport row. No progress bar: RemoteViews
 *  cannot honestly track a live stream position, so none is shown. */
public class PlayerBigWidgetProvider extends BaseWidgetProvider {
    @Override protected RemoteViews build(Context c, int id) {
        return WidgetViews.buildPlayerBig(c, id);
    }
    @Override protected boolean refreshOnNowPlaying() { return true; }
}
