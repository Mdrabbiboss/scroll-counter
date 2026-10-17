package com.rabbi.scrollcounter.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
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
     * Read every saved copy back (async): the storage vault (survives clear data AND uninstall)
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

    /**
     * A hidden backup file in the phone's shared storage (outside the app's own folder), so
     * Android's "Clear data" and uninstalling can't touch it. Needs "All files access"
     * (Android 11+) or the storage permission (older phones), asked for during setup.
     */
    object Vault {
        private const val NAME = "history.dat"

        /** Set once this process has read the vault, so a fresh empty app never overwrites it. */
        @Volatile var loaded = false
            private set

        fun hasAccess(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
            else context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

        @Suppress("DEPRECATION")
        private fun dirs(): List<File> {
            val root = Environment.getExternalStorageDirectory()
            return listOf(
                File(root, ".ScrollCounter"),
                File(File(root, Environment.DIRECTORY_DOCUMENTS), ".ScrollCounter"),
            )
        }

        @Synchronized
        fun readAll(context: Context): List<String> {
            if (!hasAccess(context)) return emptyList()
            val out = dirs().mapNotNull { d ->
                val f = File(d, NAME)
                if (!f.isFile) null else runCatching { gunzip(f.readBytes()) }.getOrNull()
            }
            loaded = true
            return out
        }

        @Synchronized
        fun write(context: Context, text: String) {
            if (!hasAccess(context) || !loaded) return
            val bytes = runCatching { gzip(text) }.getOrNull() ?: return
            dirs().forEach { d ->
                runCatching {
                    d.mkdirs()
                    runCatching { File(d, ".nomedia").createNewFile() }
                    val tmp = File(d, "$NAME.tmp")
                    tmp.writeBytes(bytes)
                    val f = File(d, NAME)
                    if (!tmp.renameTo(f)) { f.writeBytes(bytes); tmp.delete() }
                }
            }
        }
    }
}
