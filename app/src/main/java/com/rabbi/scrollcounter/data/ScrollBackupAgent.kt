package com.rabbi.scrollcounter.data

import android.app.backup.BackupAgentHelper
import android.app.backup.SharedPreferencesBackupHelper

/**
 * Backs up the reel counts and settings to the phone's Google backup soon after every change,
 * so uninstalling and reinstalling Scroll Counter brings today's count (and history) back
 * instead of starting from zero. Device-only flags live in a separate file that isn't backed up.
 */
class ScrollBackupAgent : BackupAgentHelper() {
    override fun onCreate() {
        addHelper("prefs", SharedPreferencesBackupHelper(this, "scroll_stats", "settings"))
    }
}
