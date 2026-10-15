package com.rabbi.scrollcounter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.rabbi.scrollcounter.MainActivity
import com.rabbi.scrollcounter.R
import com.rabbi.scrollcounter.data.ScrollStore
import com.rabbi.scrollcounter.face.FaceRenderer
import com.rabbi.scrollcounter.face.Mood

/** Home-screen widget: today's scrolls, progress to the limit, and the mood face. */
class ScrollWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, build(context)) }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ScrollWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            ids.forEach { runCatching { manager.updateAppWidget(it, views) } }
        }

        private fun build(context: Context): RemoteViews {
            val store = ScrollStore.get(context)
            val total = store.todayTotal()
            val limit = store.dailyLimit
            val mood = Mood.of(total, limit)
            val pct = if (limit > 0) (total * 100 / limit).coerceAtMost(100) else 0

            val faceSize = (context.resources.displayMetrics.density * 44).toInt()
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return RemoteViews(context.packageName, R.layout.widget_scroll).apply {
                setImageViewBitmap(R.id.widget_face, FaceRenderer.bitmap(faceSize, mood))
                setTextViewText(R.id.widget_count, total.toString())
                setTextViewText(R.id.widget_limit, "of $limit scrolls today")
                setTextViewText(R.id.widget_mood, if (store.paused) "Tracking paused" else mood.label)
                setProgressBar(R.id.widget_progress, 100, pct, false)
                setOnClickPendingIntent(R.id.widget_root, open)
            }
        }
    }
}
