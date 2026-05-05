package com.gsmtrick.musicplayer.data

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * v3.4 — On-device translation of lyric lines via Google ML Kit. Models
 * are downloaded once on first use (over Wi-Fi by default) and cached on
 * the device thereafter, so subsequent translations work offline.
 *
 * Heuristic source-language detection: we treat all input as English by
 * default unless the caller passes [sourceLanguage]. ML Kit translation
 * is best-effort and will silently fall through to the original text if
 * either the model can't be downloaded or the translator init fails.
 */
object LyricsTranslator {

    private val cache = HashMap<String, String>()

    /**
     * Translate [text] from [sourceLanguage] (BCP-47 like "en", "bn") to
     * [targetLanguage]. Returns the original text if translation fails or
     * if the language code isn't supported by ML Kit.
     */
    suspend fun translate(
        text: String,
        sourceLanguage: String = "en",
        targetLanguage: String,
    ): String {
        if (text.isBlank() || targetLanguage.isBlank() || sourceLanguage == targetLanguage) {
            return text
        }
        val cacheKey = "$sourceLanguage|$targetLanguage|$text"
        cache[cacheKey]?.let { return it }
        val src = TranslateLanguage.fromLanguageTag(sourceLanguage) ?: return text
        val dst = TranslateLanguage.fromLanguageTag(targetLanguage) ?: return text

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(src)
            .setTargetLanguage(dst)
            .build()
        val translator = Translation.getClient(options)
        return try {
            // Download (or no-op if cached) before we translate.
            suspendCancellableCoroutine<Unit> { cont ->
                translator.downloadModelIfNeeded(
                    DownloadConditions.Builder().requireWifi().build()
                )
                    .addOnSuccessListener { cont.resume(Unit) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            val translated = suspendCancellableCoroutine<String> { cont ->
                translator.translate(text)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            cache[cacheKey] = translated
            translated
        } catch (_: Throwable) {
            text
        } finally {
            translator.close()
        }
    }

    /**
     * Returns the BCP-47 language codes ML Kit's translator supports, in a
     * stable display order with the Bangladeshi-relevant languages first.
     */
    val supportedLanguages: List<Pair<String, String>> = listOf(
        "" to "Off",
        "bn" to "Bangla (বাংলা)",
        "en" to "English",
        "hi" to "Hindi (हिन्दी)",
        "ur" to "Urdu (اردو)",
        "ar" to "Arabic (العربية)",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "it" to "Italian",
        "pt" to "Portuguese",
        "ru" to "Russian",
        "ja" to "Japanese",
        "ko" to "Korean",
        "zh" to "Chinese",
        "tr" to "Turkish",
        "id" to "Indonesian",
        "ms" to "Malay",
        "ta" to "Tamil",
        "vi" to "Vietnamese",
    )
}
