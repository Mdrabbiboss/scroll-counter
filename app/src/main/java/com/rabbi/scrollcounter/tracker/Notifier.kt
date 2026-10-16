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
    private const val LIVE_CHANNEL_ID = "live_counter"
    private const val LIVE_ID = 1003
    private const val VISIBLE_MS = 6000L

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily limit alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Warns you near your daily scroll limit and when you pass it" }
        nm.createNotificationChannel(channel)
        // Quiet, always-on entry that puts the buddy icon in the status bar while counting.
        val live = NotificationChannel(LIVE_CHANNEL_ID, "Live reel counter", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows the buddy in the status bar and today's reel count while Scroll Counter is on"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(live)
    }

    /** The small buddy icon in the status bar, with today's count in the shade. Silent; updates in place. */
    @SuppressLint("MissingPermission")
    fun live(context: Context, count: Int, limit: Int) {
        val nmc = NotificationManagerCompat.from(context)
        if (!nmc.areNotificationsEnabled()) return
        ensureChannel(context)
        val pi = PendingIntent.getActivity(
            context, LIVE_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val left = (limit - count).coerceAtLeast(0)
        // The full-colour 3D buddy (in today's mood) as the picture, like a proper app card.
        val mood = com.rabbi.scrollcounter.face.Mood.of(count, limit)
        val sizePx = (context.resources.displayMetrics.density * 48).toInt()
        val face = runCatching { buddyTile(sizePx, mood) }.getOrNull()
        val n = NotificationCompat.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_buddy)
            .setLargeIcon(face)
            .setColor(0xFF2DD4BF.toInt())
            .setContentTitle("Scroll Counter")
            .setContentText(
                if (left > 0) "Counting your reels · $count today, $left left"
                else "Daily limit reached · $count reels today"
            )
            .setContentIntent(pi)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Only while counting: it disappears by itself a few seconds after the last reel.
            .setTimeoutAfter(VISIBLE_MS)
            .build()
        runCatching { nmc.notify(LIVE_ID, n) }
    }

    /** The buddy on a soft rounded tile, for the notification picture. */
    private fun buddyTile(size: Int, mood: com.rabbi.scrollcounter.face.Mood): android.graphics.Bitmap {
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.LinearGradient(0f, 0f, 0f, size.toFloat(), 0xFFFFF4DC.toInt(), 0xFFF3DDB0.toInt(), android.graphics.Shader.TileMode.CLAMP)
        c.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * 0.26f, size * 0.26f, p)
        com.rabbi.scrollcounter.face.FaceRenderer.draw(c, size / 2f, size / 2f, size * 0.38f, mood)
        return bmp
    }

    fun clearLive(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(LIVE_ID) }
    }

    fun limitReached(context: Context, count: Int, limit: Int) = post(
        context, LIMIT_ID,
        "Daily reel limit reached",
        "You've watched $count reels today (limit $limit). Time for a break?"
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
            .setSmallIcon(R.drawable.ic_buddy)
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
