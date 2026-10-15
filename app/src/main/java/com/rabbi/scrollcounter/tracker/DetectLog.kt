package com.rabbi.scrollcounter.tracker

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * A small in-memory list of what the tracker saw recently (app, container size, how it scrolled,
 * and what it decided). Shown in Settings → "Detection check" so a problem in a specific app can
 * be seen and fixed. Nothing here is saved; it never contains text from the screen.
 */
object DetectLog {
    private const val MAX = 60
    private val lines = ArrayDeque<String>()
    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")
    private var lastLine = ""

    @Synchronized
    fun add(pkg: String, what: String) {
        val app = pkg.substringAfterLast('.').take(14)
        val line = "$app · $what"
        if (line == lastLine) return
        lastLine = line
        lines.addFirst("${LocalTime.now().format(fmt)}  $line")
        while (lines.size > MAX) lines.removeLast()
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun clear() { lines.clear(); lastLine = "" }
}
