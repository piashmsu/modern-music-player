package com.gsmtrick.musicplayer.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Toggles icon-only activity-aliases declared in the manifest so the user
 * can pick a launcher icon variant. The changeover takes effect after the
 * next system idle (~1–10s); on some launchers it requires re-pinning.
 */
object IconSwitcher {
    private const val PKG = "com.gsmtrick.musicplayer"
    private val ALIAS_BY_VARIANT = mapOf(
        "classic" to "$PKG.IconClassic",
        "neon" to "$PKG.IconNeon",
        "minimal" to "$PKG.IconMinimal",
        "vinyl" to "$PKG.IconVinyl",
        "dark" to "$PKG.IconDark",
    )

    fun applyIconVariant(context: Context, variant: String) {
        val pm = context.packageManager
        val target = ALIAS_BY_VARIANT[variant] ?: ALIAS_BY_VARIANT.getValue("classic")
        ALIAS_BY_VARIANT.values.forEach { alias ->
            val state = if (alias == target) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            try {
                pm.setComponentEnabledSetting(
                    ComponentName(PKG, alias),
                    state,
                    PackageManager.DONT_KILL_APP,
                )
            } catch (_: Throwable) { /* alias absent - fine */ }
        }
    }
}
