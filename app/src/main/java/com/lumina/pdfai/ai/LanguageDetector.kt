package com.lumina.pdfai.ai

import java.util.Locale

/**
 * Cheap but good enough language guesser used to pick the right TTS voice and
 * the right "natural" reading rhythm.
 *
 * It counts high-frequency function words of the languages a PDF reader is
 * likely to meet. No model download, no network, instant.
 */
object LanguageDetector {

    data class Guess(val language: String, val confidence: Float, val label: String)

    private val profiles: Map<String, Map<String, Double>> = mapOf(
        "id" to mapOf(
            "yang" to 3.0, "dan" to 3.0, "di" to 2.6, "itu" to 2.2, "dengan" to 2.4,
            "untuk" to 2.2, "tidak" to 2.2, "dari" to 2.0, "ini" to 2.0, "dalam" to 2.0,
            "akan" to 1.8, "pada" to 1.8, "juga" to 1.8, "karena" to 1.8, "bisa" to 1.6,
            "sudah" to 1.6, "adalah" to 2.2, "atau" to 1.6, "saya" to 1.4, "kamu" to 1.2,
            "mereka" to 1.6, "kita" to 1.4, "secara" to 1.4, "terhadap" to 1.4
        ),
        "en" to mapOf(
            "the" to 3.2, "and" to 3.0, "of" to 2.6, "to" to 2.4, "in" to 2.0,
            "that" to 2.0, "is" to 2.0, "for" to 1.8, "with" to 1.8, "as" to 1.6,
            "this" to 1.6, "are" to 1.6, "be" to 1.4, "by" to 1.4, "not" to 1.4,
            "from" to 1.4, "you" to 1.2, "which" to 1.4, "it" to 1.0
        ),
        "ms" to mapOf(
            "yang" to 2.6, "dan" to 2.6, "di" to 2.2, "itu" to 1.8, "dengan" to 2.0,
            "untuk" to 1.8, "tidak" to 1.8, "dari" to 1.6, "ini" to 1.6, "dalam" to 1.6,
            "ialah" to 2.4, "adalah" to 1.2, "saya" to 1.4, "boleh" to 1.6, "kerana" to 2.4,
            "juga" to 1.4, "akan" to 1.4, "pada" to 1.4
        ),
        "es" to mapOf(
            "el" to 2.6, "la" to 2.6, "de" to 2.6, "que" to 2.2, "y" to 2.0,
            "en" to 2.0, "los" to 2.0, "del" to 2.0, "las" to 1.8, "por" to 1.6,
            "con" to 1.6, "para" to 1.6, "una" to 1.6, "es" to 1.6
        ),
        "fr" to mapOf(
            "le" to 2.4, "la" to 2.4, "les" to 2.2, "de" to 2.6, "et" to 2.2,
            "des" to 2.0, "du" to 1.8, "que" to 1.8, "pour" to 1.6, "dans" to 1.6,
            "une" to 1.8, "est" to 1.6, "sur" to 1.4, "pas" to 1.4
        ),
        "de" to mapOf(
            "der" to 2.4, "die" to 2.4, "und" to 2.6, "der" to 2.4, "das" to 2.2,
            "ist" to 2.0, "nicht" to 2.0, "mit" to 1.8, "ein" to 1.8, "eine" to 1.6,
            "auch" to 1.6, "sich" to 1.6, "werden" to 1.4
        ),
        "ar" to mapOf(
            "في" to 3.0, "من" to 2.6, "على" to 2.4, "أن" to 2.0, "إلى" to 2.0,
            "هذا" to 2.0, "التي" to 2.0, "مع" to 1.8
        )
    )

    private val labels = mapOf(
        "id" to "Bahasa Indonesia",
        "en" to "English",
        "ms" to "Bahasa Melayu",
        "es" to "Español",
        "fr" to "Français",
        "de" to "Deutsch",
        "ar" to "العربية"
    )

    fun label(language: String): String =
        labels[language] ?: language.uppercase(Locale.ROOT)

    /** Returns a language code understood by [Locale.forLanguageTag]. */
    fun detect(text: String): Guess {
        val sample = text.take(12_000).lowercase(Locale.ROOT)
        if (sample.isBlank()) return Guess("id", 0f, label("id"))

        val words = Regex("[\\p{L}']+").findAll(sample).map { it.value }.toList()
        if (words.isEmpty()) return Guess("id", 0f, label("id"))

        val total = words.size.toDouble()
        var best = "id"
        var bestScore = 0.0

        profiles.forEach { (lang, weights) ->
            var score = 0.0
            words.forEach { word -> score += weights[word] ?: 0.0 }
            val normalized = score / total
            if (normalized > bestScore) {
                bestScore = normalized
                best = lang
            }
        }

        // Distinguish Indonesian vs Malay only on the strongest signal.
        if (best == "en" && bestScore < 0.12) {
            profile@ for (fallback in listOf("id", "ms")) {
                val weights = profiles.getValue(fallback)
                var score = 0.0
                words.forEach { word -> score += weights[word] ?: 0.0 }
                if (score / total > bestScore) {
                    best = fallback
                    bestScore = score / total
                }
            }
        }

        val confidence = (bestScore / 2.4).coerceIn(0.0, 1.0).toFloat()
        return Guess(best, confidence, label(best))
    }

    fun localeFor(language: String): Locale = when (language) {
        "id" -> Locale("id", "ID")
        "ms" -> Locale("ms", "MY")
        "en" -> Locale.US
        else -> Locale.forLanguageTag(language)
    }
}
