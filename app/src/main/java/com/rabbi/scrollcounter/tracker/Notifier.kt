package com.rabbi.scrollcounter.tracker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rabbi.scrollcounter.MainActivity
import com.rabbi.scrollcounter.R

object Notifier {
    private const val CHANNEL_ID = "scroll_limit"
    private const val LIMIT_ID = 1001
    private const val WARN_ID = 1002

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily limit alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Warns you near your daily scroll limit and when you pass it" }
        nm.createNotificationChannel(channel)
    }

    fun limitReached(context: Context, count: Int, limit: Int) = post(
        context, LIMIT_ID,
        "Daily scroll limit reached",
        "You've scrolled $count times today (limit $limit). Time for a break?"
    )

    fun almostThere(context: Context, count: Int, limit: Int) = post(
        context, WARN_ID,
        "80% of today's limit used",
        "$count of $limit reels. Only ${limit - count} left today."
    )

    /** Sends a test notification from Settings so the user can check alerts work. */
    fun test(context: Context) = post(
        context, WARN_ID,
        "Alerts are working",
        "You'll get a note like this near and at your daily limit."
    )

    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, title: String, text: String) {
        val nmc = NotificationManagerCompat.from(context)
        // Covers both the Android 13+ runtime permission and the user switching alerts off.
        if (!nmc.areNotificationsEnabled()) return
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_scroll)
            .setColor(0xFF0E8C7F.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { nmc.notify(id, n) }
    }
}
