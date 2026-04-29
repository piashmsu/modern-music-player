package com.gsmtrick.musicplayer.util

/**
 * Convert ASCII digits in [text] to Bengali numerals (০–৯). Non-digit
 * characters and decimal markers are left untouched, so this is safe to
 * apply to formatted durations like "01:23" or counts like "Songs: 482".
 */
fun toBanglaNumerals(text: String): String {
    if (text.isEmpty()) return text
    val sb = StringBuilder(text.length)
    for (ch in text) {
        sb.append(
            when (ch) {
                '0' -> '০'
                '1' -> '১'
                '2' -> '২'
                '3' -> '৩'
                '4' -> '৪'
                '5' -> '৫'
                '6' -> '৬'
                '7' -> '৭'
                '8' -> '৮'
                '9' -> '৯'
                else -> ch
            }
        )
    }
    return sb.toString()
}

/** Conditionally apply [toBanglaNumerals] when [enabled] is true. */
fun banglaNumeralsIf(enabled: Boolean, text: String): String =
    if (enabled) toBanglaNumerals(text) else text

/**
 * Process-wide Bangla-numerals flag. The [PlayerViewModel] writes this
 * whenever the user toggles the setting so non-composable utilities like
 * [com.gsmtrick.musicplayer.ui.screens.formatDuration] can localise
 * numerals without threading prefs through every call-site.
 */
@Volatile
var banglaNumeralsGlobal: Boolean = false

