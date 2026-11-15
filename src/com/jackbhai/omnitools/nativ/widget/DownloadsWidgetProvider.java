package com.jackbhai.omnitools.nativ.widget;

import android.content.Context;
import android.widget.RemoteViews;

/** Downloads widget (4x1): most recent DownloadManager download owned by
 *  this app, with status and percent. Refreshes on the platform update
 *  period and when tapped. */
public class DownloadsWidgetProvider extends BaseWidgetProvider {
    @Override protected RemoteViews build(Context c, int id) {
        return WidgetViews.buildDownloads(c, id);
    }
}
