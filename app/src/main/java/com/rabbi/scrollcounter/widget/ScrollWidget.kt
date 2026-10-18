package com.rabbi.scrollcounter.widget

import com.rabbi.scrollcounter.ui.tr

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.widget.RemoteViews
import com.rabbi.scrollcounter.MainActivity
import com.rabbi.scrollcounter.R
import com.rabbi.scrollcounter.data.ScrollStore

/**
 * Home-screen widget. The whole card is drawn by [WidgetArt] at the phone's real resolution for
 * the widget's actual size (square 2×2 or wide 4×2), so it stays razor-sharp and matches the
 * preview inside the app. Drawing happens on a background thread.
 */
class ScrollWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context, ids)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        refresh(context, intArrayOf(id))
    }

    companion object {
        private val worker: Handler by lazy {
            Handler(HandlerThread("widget-render").apply { start() }.looper)
        }

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ScrollWidget::class.java))
            if (ids.isEmpty()) return
            refresh(context, ids)
        }

        private fun refresh(context: Context, ids: IntArray) {
            val app = context.applicationContext
            worker.post {
                val manager = AppWidgetManager.getInstance(app) ?: return@post
                val state = currentState(app)
                ids.forEach { id -> runCatching { manager.updateAppWidget(id, build(app, manager, id, state)) } }
            }
        }

        fun currentState(context: Context): WidgetState {
            val store = ScrollStore.get(context)
            return WidgetState(store.todayUsed(), store.dailyLimit, runCatching { store.streak() }.getOrDefault(0), store.paused)
        }

        private fun build(context: Context, manager: AppWidgetManager, id: Int, state: WidgetState): RemoteViews {
            val dm = context.resources.displayMetrics
            val opts = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
            // Portrait size: min width × max height (what launchers report for portrait).
            var wDp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            var hDp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
            if (wDp <= 0) wDp = 160
            if (hDp <= 0) hDp = 160
            wDp = wDp.coerceIn(100, 520); hDp = hDp.coerceIn(100, 520)

            // Full resolution, but keep the bitmap well inside the widget memory budget.
            var scale = dm.density
            val maxPixels = 1_300_000f
            if (wDp * hDp * scale * scale > maxPixels) scale = kotlin.math.sqrt(maxPixels / (wDp * hDp))
            val bmp = WidgetArt.bitmap((wDp * scale).toInt(), (hDp * scale).toInt(), scale, state)

            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return RemoteViews(context.packageName, R.layout.widget_scroll).apply {
                setImageViewBitmap(R.id.widget_image, bmp)
                setContentDescription(R.id.widget_image, tr("%1\$s of %2\$s reels today", state.total, state.limit))
                setOnClickPendingIntent(R.id.widget_root, open)
            }
        }
    }
}
