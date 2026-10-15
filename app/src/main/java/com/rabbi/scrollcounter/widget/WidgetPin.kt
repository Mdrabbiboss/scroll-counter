package com.rabbi.scrollcounter.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** Helpers for putting the widget on the home screen with the system "Add to home screen" prompt. */
object WidgetPin {
    private fun provider(context: Context) = ComponentName(context, ScrollWidget::class.java)

    /** True when at least one Scroll Counter widget is on the home screen. */
    fun isPlaced(context: Context): Boolean =
        runCatching {
            AppWidgetManager.getInstance(context).getAppWidgetIds(provider(context)).isNotEmpty()
        }.getOrDefault(false)

    /** Whether the launcher supports the one-tap "Add to home screen" prompt (most do on Android 8+). */
    fun canPin(context: Context): Boolean =
        runCatching { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }.getOrDefault(false)

    /** Shows the system prompt. Returns false if the launcher doesn't support it. */
    fun request(context: Context): Boolean =
        canPin(context) && runCatching {
            AppWidgetManager.getInstance(context).requestPinAppWidget(provider(context), null, null)
        }.getOrDefault(false)
}
