package com.jackbhai.omnitools.nativ.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

/**
 * Shared AppWidgetProvider plumbing: onUpdate / resize handling, and —
 * for the player widgets — refresh on the NOW_PLAYING_CHANGED broadcast
 * from the now-playing contract. Concrete providers only supply their
 * RemoteViews builder.
 */
public abstract class BaseWidgetProvider extends AppWidgetProvider {

    /** The RemoteViews for one widget instance of this kind. */
    protected abstract RemoteViews build(Context context, int widgetId);

    /** True when this kind must refresh on now-playing changes. */
    protected boolean refreshOnNowPlaying() { return false; }

    public final void updateOne(Context context, AppWidgetManager mgr, int widgetId) {
        try {
            mgr.updateAppWidget(widgetId, build(context, widgetId));
        } catch (Exception ignored) {
            // A failed refresh must never crash the host process; the next
            // scheduled update retries.
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] appWidgetIds) {
        for (int id : appWidgetIds) updateOne(context, mgr, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager mgr,
                                          int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, mgr, appWidgetId, newOptions);
        // Size changed -> the glass bitmap must be re-rendered at the
        // new size, so rebuild this instance.
        updateOne(context, mgr, appWidgetId);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && WidgetViews.NOW_PLAYING_ACTION.equals(intent.getAction())
                && refreshOnNowPlaying()) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(context, getClass()));
            for (int id : ids) updateOne(context, mgr, id);
        }
    }
}
