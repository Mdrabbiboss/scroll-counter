package com.rabbi.scrollcounter.guide

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.rabbi.scrollcounter.ui.tr

/** Which phone's Settings look the guide copies. */
internal enum class Os { SAMSUNG, XIAOMI, COLOROS, STOCK }

internal class Skin(val os: Os, val accent: Color, val cards: Boolean, val radius: Float) {
    /** The entry on the Accessibility page that lists installed accessibility apps (null = listed right there). */
    val appsEntry: String? = when (os) {
        Os.SAMSUNG -> "Installed apps"
        Os.XIAOMI, Os.COLOROS -> "Downloaded apps"
        Os.STOCK -> null
    }

    companion object {
        fun detect(): Skin {
            val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
            return when {
                "samsung" in m -> Skin(Os.SAMSUNG, Color(0xFF3E91FF), cards = true, radius = 20f)
                listOf("xiaomi", "redmi", "poco").any { it in m } -> Skin(Os.XIAOMI, Color(0xFF0D84FF), cards = false, radius = 14f)
                listOf("oppo", "realme", "oneplus").any { it in m } -> Skin(Os.COLOROS, Color(0xFF2E7CF6), cards = true, radius = 16f)
                else -> Skin(Os.STOCK, Color(0xFF4C8DF6), cards = false, radius = 16f)
            }
        }
    }
}

// ---- A tiny model of a Settings screen, drawn by GuideMock ----

internal data class MRow(
    val title: String,
    val sub: String? = null,
    val icon: ImageVector? = null,
    val iconColor: Color = Color(0xFF6E7781),
    val app: Boolean = false,
    val toggle: Boolean? = null,
    val dim: Boolean = false,
)

internal data class MGroup(val rows: List<MRow>, val header: String? = null)

internal data class MScreen(val title: String, val groups: List<MGroup>, val appHeader: Boolean = false)

internal sealed interface Overlay {
    data class Dialog(val title: String, val body: String?, val buttons: List<String>, val target: Int) : Overlay
    data class Menu(val items: List<String>) : Overlay
    data object Biometric : Overlay
}

internal sealed interface Target {
    /** A row; [flips] = its switch turns on when tapped. */
    data class Row(val group: Int, val row: Int, val flips: Boolean = false) : Target
    data object Dots : Target
    data object InOverlay : Target
}

internal data class Scene(val screen: MScreen, val target: Target?, val caption: String, val overlay: Overlay? = null)

private const val APP = "Scroll Counter"
private const val RESTRICTED_SUB = "Controlled by Restricted Setting"

private val blue = Color(0xFF3E7BFA)
private val violet = Color(0xFF7B61FF)
private val green = Color(0xFF1FA67A)
private val orange = Color(0xFFF08A24)
private val grey = Color(0xFF6E7781)

/** The scenes for this phone and this stage, in order (they loop). */
internal fun scenesFor(skin: Skin, stage: SetupGuide.Stage): List<Scene> {
    val appRowOff = MRow(APP, "Off", app = true)
    val appRowBlocked = MRow(APP, RESTRICTED_SUB, app = true, dim = true)

    fun main(appRow: MRow): MScreen = when (skin.os) {
        Os.SAMSUNG -> MScreen("Accessibility", listOf(
            MGroup(listOf(MRow("Vision enhancements", icon = Icons.Rounded.ZoomIn, iconColor = violet), MRow("TalkBack", icon = Icons.Rounded.RecordVoiceOver, iconColor = blue))),
            MGroup(listOf(MRow("Interaction and dexterity", icon = Icons.Rounded.TouchApp, iconColor = orange))),
            MGroup(listOf(MRow("Advanced settings", icon = Icons.Rounded.Settings, iconColor = blue), MRow("Installed apps", "3 apps", icon = Icons.Rounded.Download, iconColor = blue))),
        ))
        Os.XIAOMI -> MScreen("Accessibility", listOf(
            MGroup(listOf(MRow("TalkBack", icon = Icons.Rounded.RecordVoiceOver, iconColor = blue), MRow("Select to Speak", icon = Icons.Rounded.VolumeUp, iconColor = green), MRow("Text-to-speech output", icon = Icons.Rounded.ClosedCaption, iconColor = orange))),
            MGroup(listOf(MRow("Downloaded apps", icon = Icons.Rounded.Download, iconColor = blue))),
        ))
        Os.COLOROS -> MScreen("Accessibility", listOf(
            MGroup(listOf(MRow("Vision", icon = Icons.Rounded.Visibility, iconColor = blue), MRow("Hearing", icon = Icons.Rounded.Hearing, iconColor = green), MRow("Interaction", icon = Icons.Rounded.TouchApp, iconColor = orange))),
            MGroup(listOf(MRow("Downloaded apps", icon = Icons.Rounded.Download, iconColor = blue))),
        ))
        Os.STOCK -> MScreen("Accessibility", listOf(
            MGroup(listOf(appRow, MRow("Live Transcribe", "Off", icon = Icons.Rounded.ClosedCaption, iconColor = blue)), header = "Downloaded apps"),
            MGroup(listOf(MRow("TalkBack", "Off", icon = Icons.Rounded.RecordVoiceOver, iconColor = blue), MRow("Select to Speak", "Off", icon = Icons.Rounded.VolumeUp, iconColor = green)), header = "Screen readers"),
            MGroup(listOf(MRow("Color and motion", icon = Icons.Rounded.Contrast, iconColor = violet)), header = "Display"),
        ))
    }
    val mainTarget: Target = when (skin.os) {
        Os.SAMSUNG -> Target.Row(2, 1)
        Os.XIAOMI, Os.COLOROS -> Target.Row(1, 0)
        Os.STOCK -> Target.Row(0, 0)
    }
    fun list(appRow: MRow) = MScreen(skin.appsEntry ?: "Accessibility", listOf(
        MGroup(listOf(MRow("Live Transcribe", "Off", icon = Icons.Rounded.ClosedCaption, iconColor = blue), appRow, MRow("Sound Notifications", "Off", icon = Icons.Rounded.Hearing, iconColor = green)))
    ))
    val switchLabel = if (skin.os == Os.SAMSUNG) "Off" else "Use $APP"
    fun appPage(on: Boolean) = MScreen(APP, listOf(
        MGroup(listOf(MRow(if (skin.os == Os.SAMSUNG && on) "On" else switchLabel, toggle = on))),
        MGroup(listOf(MRow("$APP shortcut", toggle = false), MRow("Settings"), MRow("App info"))),
    ))
    val allow = Overlay.Dialog("Allow $APP to have full control of your device?", null, listOf("Allow", "Deny"), 0)
    val refused = if (skin.os == Os.SAMSUNG)
        Overlay.Dialog("App was denied access", "Access to this permission can put your personal and financial info at risk.", listOf("Close"), 0)
    else Overlay.Dialog("Restricted setting", "For your security, this setting is currently unavailable.", listOf("OK"), 0)
    val appInfo = MScreen("App info", listOf(
        MGroup(listOf(MRow("Notifications", icon = Icons.Rounded.Notifications, iconColor = orange), MRow("Permissions", icon = Icons.Rounded.Lock, iconColor = blue))),
        MGroup(listOf(MRow("Storage", icon = Icons.Rounded.Folder, iconColor = green), MRow("Battery", icon = Icons.Rounded.BatteryFull, iconColor = green))),
    ), appHeader = true)

    val openEntry = skin.appsEntry?.let { tr("Find and tap “%1\$s”", it) }
    val tapApp = tr("Tap “%1\$s”", APP)
    return when (stage) {
        SetupGuide.Stage.BLOCKED -> buildList {
            if (skin.appsEntry != null) {
                add(Scene(main(appRowBlocked), mainTarget, openEntry!!))
                add(Scene(list(appRowBlocked), Target.Row(0, 1), tr("Tap “%1\$s” (it looks grey)", APP)))
                add(Scene(list(appRowBlocked), Target.InOverlay, tr("Tap “%1\$s”. App info opens next.", refused.buttons[0]), refused))
            } else {
                add(Scene(main(appRowBlocked), mainTarget, tr("Tap “%1\$s” (it looks grey)", APP)))
                add(Scene(main(appRowBlocked), Target.InOverlay, tr("Tap “%1\$s”. App info opens next.", refused.buttons[0]), refused))
            }
        }
        SetupGuide.Stage.CAN_ALLOW -> listOf(
            Scene(appInfo, Target.Dots, tr("Tap ⋮ at the top right")),
            Scene(appInfo, Target.InOverlay, tr("Tap “%1\$s”", "Allow restricted settings"), Overlay.Menu(listOf("Allow restricted settings"))),
            Scene(appInfo, null, tr("Confirm with your fingerprint or PIN"), Overlay.Biometric),
        )
        else -> buildList {
            if (skin.appsEntry != null) {
                add(Scene(main(appRowOff), mainTarget, openEntry!!))
                add(Scene(list(appRowOff), Target.Row(0, 1), tapApp))
            } else add(Scene(main(appRowOff), mainTarget, tr("Find “%1\$s” and tap “%2\$s”", "Downloaded apps", APP)))
            add(Scene(appPage(false), Target.Row(0, 0, flips = true), tr("Turn on the switch")))
            add(Scene(appPage(true), Target.InOverlay, tr("Tap “%1\$s”", "Allow"), allow))
        }
    }
}
