package com.jackbhai.omnitools.nativ.widget;

import android.content.Context;
import android.widget.RemoteViews;

/** Quick Tools widget (4x1 / 4x2): up to 4 shortcut tiles chosen in the
 *  widget config screen. */
public class QuickToolsWidgetProvider extends BaseWidgetProvider {
    @Override protected RemoteViews build(Context c, int id) {
        return WidgetViews.buildQuickTools(c, id);
    }
}
