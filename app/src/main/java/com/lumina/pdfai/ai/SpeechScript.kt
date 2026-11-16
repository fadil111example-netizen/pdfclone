package com.lumina.pdfai.ai

import com.lumina.pdfai.pdf.OutlineNode
import kotlin.math.max

/** A heading detected in the document; read with a short pause before it. */
data class ScriptHeading(val pageIndex: Int, val title: String, val depth: Int)

/**
 * One speakable unit. Chunks are sentence sized so the reader can highlight the
 * exact place being read and so a single failure never kills a whole page.
 */
data class SpeechChunk(
    val index: Int,
    val text: String,
    val pageIndex: Int,
    val isHeading: Boolean = false,
    /** Silence inserted before speaking, in milliseconds. */
    val leadInMs: Int = 0,
    /** Silence inserted after speaking, in milliseconds. */
    val tailMs: Int = 220
) {
    val charCount: Int get() = text.length
}

data class SpeechScript(
    val chunks: List<SpeechChunk>,
    val headings: List<ScriptHeading>,
    val pageOffsets: Map<Int, Int>,
    val language: String,
    val totalChars: Int
) {
    val isEmpty: Boolean get() = chunks.isEmpty()
    val estimatedMinutes: Int get() = max(1, (totalChars / 14) / 60)

    fun chunkAt(index: Int): SpeechChunk? = chunks.getOrNull(index)

    fun firstChunkOfPage(page: Int): Int =
        chunks.indexOfFirst { it.pageIndex >= page }.let { if (it < 0) chunks.lastIndex else it }

    companion object {
        val EMPTY = SpeechScript(emptyList(), emptyList(), emptyMap(), "id", 0)
    }
}

/**
 * Builds the narration script.
 *
 * The quality of read-aloud lives or dies here: we drop repeated headers,
 * repair hyphenation, merge broken lines, keep headings separate (so they get a
 * dramatic little pause) and split at real sentence boundaries instead of
 * blindly every N characters.
 */
class ScriptBuilder {

    data class Options(
        val skipHeaders: Boolean = true,
        val naturalPauses: Boolean = true,
        val maxChunkChars: Int = 320,
        val markHeadings: Boolean = true
    )

    fun build(
        rawPages: List<String>,
        outline: List<OutlineNode> = emptyList(),
        options: Options = Options()
    ): SpeechScript {
        if (rawPages.isEmpty()) return SpeechScript.EMPTY

        val headingSet = outline.filter { it.pageIndex >= 0 && it.title.isNotBlank() }
            .map { it.pageIndex to it.title.trim() }

        val heads = if (options.skipHeaders) TextNormalizer.detectRunningHeads(rawPages) else emptySet()
        val cleanPages = rawPages.mapIndexed { pageIndex, page ->
            val withoutHeads = TextNormalizer.stripRunningHeads(page, heads)
            // The chapter title is both in the outline and printed at the top of
            // the page. Speak it once: keep the outline copy, drop the printed
            // one before the lines get glued into the first sentence. When
            // headings are not spoken at all, the printed copy must stay.
            val titles = if (options.markHeadings) {
                headingSet.filter { it.first == pageIndex }.map { it.second.speakKey() }.toSet()
            } else {
                emptySet()
            }
            val deduped = dropPrintedTitle(withoutHeads, titles)
            TextNormalizer.cleanPage(deduped, stripRunningHeads = options.skipHeaders)
        }

        val headings = mutableListOf<ScriptHeading>()
        val chunks = mutableListOf<SpeechChunk>()
        val pageOffsets = mutableMapOf<Int, Int>()
        var paragraphBreak = false

        cleanPages.forEachIndexed { pageIndex, page ->
            val pageHeadings = headingSet.filter { it.first == pageIndex }
            // A chapter divider page often contains nothing but its title. In
            // that case the outline chunk is the only thing worth speaking, so
            // the blank-page guard must not swallow it.
            if (page.isBlank() && (pageHeadings.isEmpty() || !options.markHeadings)) {
                return@forEachIndexed
            }
            pageOffsets[pageIndex] = chunks.size

            if (options.markHeadings) {
                pageHeadings.forEach { (_, title) ->
                    headings += ScriptHeading(pageIndex, title, 0)
                    chunks += SpeechChunk(
                        index = chunks.size,
                        text = title.ensureSentenceEnd(),
                        pageIndex = pageIndex,
                        isHeading = true,
                        leadInMs = if (options.naturalPauses) 520 else 0,
                        tailMs = if (options.naturalPauses) 520 else 200
                    )
                }
            }

            if (page.isBlank()) return@forEachIndexed

            val paragraphs = page.split(Regex("\\n\\s*\\n"))
            paragraphs.forEach { paragraph ->
                val sentences = splitSentences(paragraph)
                sentences.forEach { sentence ->
                    val spoken = TextNormalizer.forSpeech(sentence)
                    if (spoken.isBlank() || spoken.length < 2) return@forEach
                    splitLong(spoken, options.maxChunkChars).forEachIndexed { i, part ->
                        chunks += SpeechChunk(
                            index = chunks.size,
                            text = part,
                            pageIndex = pageIndex,
                            leadInMs = if (options.naturalPauses && paragraphBreak && i == 0) 260 else 0,
                            tailMs = if (options.naturalPauses) naturalTail(part) else 180
                        )
                    }
                }
                paragraphBreak = true
            }
            paragraphBreak = true
        }

        val language = LanguageDetector.detect(cleanPages.joinToString(" ")).language
        return SpeechScript(
            chunks = chunks.mapIndexed { i, chunk -> chunk.copy(index = i) },
            headings = headings,
            pageOffsets = pageOffsets,
            language = language,
            totalChars = chunks.sumOf { it.charCount }
        )
    }

    /** Splits prose into sentences, keeping punctuation-based rhythm. */
    fun splitSentences(paragraph: String): List<String> {
        val compact = TextNormalizer.collapseNewlines(paragraph)
        if (compact.isBlank()) return emptyList()

        val result = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0

        while (i < compact.length) {
            val c = compact[i]
            current.append(c)
            val isStop = c == '.' || c == '!' || c == '?' || c == ';' || c == '…'
            if (isStop) {
                // Absorb closing quotes/brackets that belong to this sentence.
                var j = i + 1
                while (j < compact.length && compact[j] in "\"')]}") {
                    current.append(compact[j]); j++
                }
                val hasSpaceAfter = j >= compact.length || compact[j].isWhitespace()
                // Skip the whitespace itself before looking at the next letter,
                // otherwise every ordinary ". " would look like lowercase and
                // the whole paragraph would collapse into one huge chunk.
                var k = j
                while (k < compact.length && compact[k].isWhitespace()) k++
                val nextStartsNewSentence =
                    k < compact.length && (compact[k].isUpperCase() || compact[k].isDigit())
                val isDecimal = c == '.' && i > 0 && i + 1 < compact.length &&
                    compact[i - 1].isDigit() && compact[i + 1].isDigit()
                val isAbbrev = c == '.' && looksAbbreviated(compact.substring(0, i + 1))

                if (hasSpaceAfter && !isDecimal && !isAbbrev && (nextStartsNewSentence || c == '!' || c == '?')) {
                    result += current.toString().trim()
                    current.clear()
                }
                i = j
                continue
            }
            i++
        }
        if (current.isNotBlank()) result += current.toString().trim()
        return result.filter { it.length > 1 }
    }

    private val abbreviations = setOf(
        "mr", "mrs", "ms", "dr", "prof", "st", "jr", "sr", "vs", "etc", "e.g", "i.e",
        "no", "vol", "fig", "hlm", "hal", "dsb", "dll", "tsb", "a.n", "u.p", "ybs", "s.d"
    )

    private fun looksAbbreviated(head: String): Boolean {
        val lastWord = head.trimEnd('.', ',', ';', ':').substringAfterLast(' ').lowercase()
        return abbreviations.contains(lastWord) || lastWord.length == 1
    }

    /** Hard split for sentences that exceed the engine's comfortable limit. */
    private fun splitLong(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)

        val parts = mutableListOf<String>()
        var rest = text
        while (rest.length > maxChars) {
            val window = rest.take(maxChars)
            val cut = maxOf(
                window.lastIndexOf(", "),
                window.lastIndexOf("; "),
                window.lastIndexOf(" - "),
                window.lastIndexOf(' ')
            )
            val at = if (cut > maxChars / 3) cut + 1 else maxChars
            parts += rest.take(at).trim()
            rest = rest.drop(at).trim()
        }
        if (rest.isNotBlank()) parts += rest
        return parts.filter { it.isNotBlank() }
    }

    /** Gives the engine a rhythm hint: longer silence after a finished idea. */
    private fun naturalTail(chunk: String): Int {
        val end = chunk.trimEnd()
        return when {
            end.endsWith("?") || end.endsWith("!") -> 560
            end.endsWith(".") -> 620
            end.endsWith(";") || end.endsWith(":") -> 380
            end.endsWith(",") -> 260
            end.endsWith("…") -> 700
            else -> 240
        }
    }

    private fun String.ensureSentenceEnd(): String {
        val t = trim()
        return if (t.isEmpty() || t.last() in ".!?:…") t else "$t."
    }

    /**
     * Normalised comparison key: case, punctuation and spacing are irrelevant
     * when deciding whether a printed line is the outline title.
     */
    private fun String.speakKey(): String =
        lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

    /**
     * Removes the first printed line when it merely repeats an outline title.
     * Only the leading lines are inspected, so a sentence that happens to quote
     * the chapter name further down the page is left untouched.
     */
    private fun dropPrintedTitle(rawPage: String, titleKeys: Set<String>): String {
        if (titleKeys.isEmpty()) return rawPage
        val lines = rawPage.lines()
        val firstContent = lines.indexOfFirst { it.isNotBlank() }
        if (firstContent < 0) return rawPage

        // Look at the first two non-blank lines: titles wrap, headers do not.
        val candidates = mutableListOf<Int>()
        var seen = 0
        var i = firstContent
        while (i < lines.size && seen < 2) {
            if (lines[i].isNotBlank()) {
                candidates += i
                seen++
            }
            i++
        }
        if (candidates.isEmpty()) return rawPage

        for (idx in candidates) {
            if (lines[idx].speakKey() in titleKeys) {
                val mutable = lines.toMutableList()
                mutable.removeAt(idx)
                return mutable.joinToString("\n")
            }
        }
        return rawPage
    }
}
