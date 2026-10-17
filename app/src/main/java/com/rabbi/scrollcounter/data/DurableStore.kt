package com.rabbi.scrollcounter.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Keeps the reel history safe from "uninstall & reinstall" (and clearing the app's data), so
 * nobody can wipe today's count to cheat their limit, and the last 30 days stay visible.
 *
 * It uses Google Play services' Block Store: a small, private storage area for this app that the
 * phone keeps outside the app's own data. On every start the app reads it back and merges it
 * with what's on the phone — always keeping the higher number, so nothing is ever lowered.
 * Only counts are stored (date, app, reels, distance, hour) — nothing about what was watched.
 */
object DurableStore {
    private const val KEY = "scroll_counter_history_v1"
    private const val MAX_BYTES = 3800           // Block Store keeps up to 4 KB per entry
    private const val SAVE_DELAY_MS = 2500L

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var pending: (() -> Unit)? = null

    /** Save soon (coalesced) after counts change. */
    fun scheduleSave(context: Context, snapshot: () -> String) {
        val app = context.applicationContext
        pending?.let { main.removeCallbacks(it) }
        val task = { save(app, snapshot()) }
        pending = task
        main.postDelayed(task, SAVE_DELAY_MS)
    }

    /** Save immediately (e.g. right after deleting history). */
    fun saveNow(context: Context, text: String) {
        pending?.let { main.removeCallbacks(it) }
        pending = null
        save(context.applicationContext, text)
    }

    private fun save(context: Context, text: String) {
        io.execute { Vault.write(context, text) }
        var bytes = gzip(text)
        // Too big? Drop the oldest days (lines are oldest first) until it fits.
        val all = text.lines()
        val header = all.firstOrNull() ?: return
        var body = all.drop(1)
        while (bytes.size > MAX_BYTES && body.size > 10) {
            body = body.drop(body.size / 10 + 1)
            bytes = gzip((listOf(header) + body).joinToString("\n"))
        }
        if (bytes.size > MAX_BYTES) return
        runCatching {
            Blockstore.getClient(context).storeBytes(
                StoreBytesData.Builder().setKey(KEY).setBytes(bytes).build()
            )
        }
    }

    private val io = Executors.newSingleThreadExecutor()

    /**
     * Read every saved copy back (async): the backup file in Download/ScrollCounter (survives uninstall)
     * and Block Store. [onResult] is called once per copy found.
     */
    fun restore(context: Context, onResult: (String?) -> Unit) {
        val app = context.applicationContext
        io.execute { Vault.readAll(app).forEach { onResult(it) } }
        runCatching {
            Blockstore.getClient(context.applicationContext)
                .retrieveBytes(RetrieveBytesRequest.Builder().setKeys(listOf(KEY)).build())
                .addOnSuccessListener { resp ->
                    val data = resp.blockstoreDataMap[KEY]?.bytes
                    onResult(data?.let { runCatching { gunzip(it) }.getOrNull() })
                }
                .addOnFailureListener { onResult(null) }
        }.onFailure { onResult(null) }
    }

    private fun gzip(s: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(s.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    private fun gunzip(b: ByteArray): String =
        GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes().toString(Charsets.UTF_8) }

    /** A Scroll Counter backup is gzip text starting with "v1|"; anything else is rejected. */
    fun decode(bytes: ByteArray): String? =
        runCatching { gunzip(bytes) }.getOrNull()?.takeIf { it.startsWith("v1|") }

    /** Read a backup file the user picked (e.g. after reinstalling). Null if it isn't one. */
    fun readPicked(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { s ->
            val b = s.readBytes(); if (b.size > 2_000_000) null else decode(b)
        }
    }.getOrNull()

    /**
     * The backup file in the phone's Download/ScrollCounter folder. Android's "Clear data" and
     * uninstalling don't delete it. Android 10+ needs no permission at all (the app only writes its
     * own file through MediaStore); Android 8–9 use the normal storage permission.
     * After a reinstall Android hides the old file from the app, so the user picks it once
     * ("Restore from backup file") and from then on the app keeps writing to that same file.
     */
    object Vault {
        const val FOLDER = "ScrollCounter"
        private const val NAME = "scrollcounter_backup.scb"
        private const val MIME = "application/octet-stream"
        private const val PREFS = "vault"          // not part of the Google backup
        private const val KEY_URI = "picked_uri"

        /** Set once this process has read the vault, so a fresh empty app never overwrites it. */
        @Volatile var loaded = false
            private set
        @Volatile private var own: Uri? = null

        fun hasAccess(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

        @Suppress("DEPRECATION")
        private fun legacyFile() =
            File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER), NAME)

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        @Synchronized
        fun readAll(context: Context): List<String> {
            if (!hasAccess(context)) return emptyList()
            val out = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cr = context.contentResolver
                // 1) A file the user picked earlier (old backup from before a reinstall).
                prefs(context).getString(KEY_URI, null)?.let { Uri.parse(it) }?.let { uri ->
                    val text = runCatching { cr.openInputStream(uri)?.use { decode(it.readBytes()) } }.getOrNull()
                    if (text != null) { out += text; own = uri }
                    else prefs(context).edit().remove(KEY_URI).apply()
                }
                // 2) The file this install created itself.
                val coll = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                var newest: Uri? = null; var newestTime = -1L
                runCatching {
                    cr.query(
                        coll, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_MODIFIED),
                        "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                        arrayOf("${Environment.DIRECTORY_DOWNLOADS}/$FOLDER%", "scrollcounter_backup%"), null
                    )?.use { c ->
                        while (c.moveToNext()) {
                            val uri = ContentUris.withAppendedId(coll, c.getLong(0))
                            val text = runCatching { cr.openInputStream(uri)?.use { decode(it.readBytes()) } }.getOrNull() ?: continue
                            out += text
                            if (c.getLong(1) > newestTime) { newestTime = c.getLong(1); newest = uri }
                        }
                    }
                }
                if (own == null) own = newest
            } else {
                legacyFile().takeIf { it.isFile }?.let { f -> runCatching { decode(f.readBytes()) }.getOrNull()?.let { out += it } }
            }
            loaded = true
            return out
        }

        /** Keep writing to the backup file the user just restored from, instead of making a new one. */
        @Synchronized
        fun adopt(context: Context, uri: Uri) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val kept = runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }.isSuccess
            if (!kept) return
            own = uri
            prefs(context).edit().putString(KEY_URI, uri.toString()).apply()
        }

        @Synchronized
        fun write(context: Context, text: String) {
            if (!hasAccess(context) || !loaded) return
            val bytes = runCatching { gzip(text) }.getOrNull() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cr = context.contentResolver
                own?.let { uri ->
                    val ok = runCatching { cr.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null }.getOrDefault(false)
                    if (ok) return
                    own = null; prefs(context).edit().remove(KEY_URI).apply()
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, NAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, MIME)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = runCatching { cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) }.getOrNull() ?: return
                val ok = runCatching { cr.openOutputStream(uri, "w")?.use { it.write(bytes) } != null }.getOrDefault(false)
                if (!ok) { runCatching { cr.delete(uri, null, null) }; return }
                runCatching { cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) }
                own = uri
            } else runCatching {
                val f = legacyFile(); f.parentFile?.mkdirs()
                val tmp = File(f.parentFile, "$NAME.tmp")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(f)) { f.writeBytes(bytes); tmp.delete() }
            }
        }
    }
}
