package com.lumina.pdfai.ai

/**
 * Turns the raw, badly-behaved text that PDF extraction produces into prose a
 * human would actually want to listen to.
 *
 * PDFs break lines anywhere, hyphenate words, repeat running headers on every
 * page and sprinkle page numbers in the middle of sentences. Feeding that to a
 * speech engine is exactly what makes read-aloud sound robotic and confusing,
 * so everything is repaired here first, before the text reaches the voice.
 */
object TextNormalizer {

    private val multiSpace = Regex("[ \\t\\u00A0\\u2007\\u202F]+")
    private val multiNewline = Regex("\\n{3,}")

    /** Abbreviations expanded to full words, because TTS reads dots badly. */
    private val expansions: List<Pair<Regex, String>> = listOf(
        Regex("\\bdsb\\.") to "dan sebagainya",
        Regex("\\bdll\\.") to "dan lain lain",
        Regex("\\btsb\\.") to "tersebut",
        Regex("\\bhlm\\.") to "halaman",
        Regex("\\bhal\\.") to "halaman",
        Regex("\\bbab\\s+(\\d+)") to "bab $1",
        Regex("\\bNo\\.") to "nomor",
        Regex("\\bvol\\.") to "volume",
        Regex("\\bed\\.", RegexOption.IGNORE_CASE) to "edisi",
        Regex("\\bhlm", RegexOption.IGNORE_CASE) to "halaman",
        Regex("\\be\\.g\\.") to "for example",
        Regex("\\bi\\.e\\.") to "that is",
        Regex("\\betc\\.") to "etcetera",
        Regex("\\bvs\\.") to "versus",
        Regex("\\bMr\\.") to "Mister",
        Regex("\\bMrs\\.") to "Missus",
        Regex("\\bDr\\.") to "Doctor",
        Regex("\\bProf\\.") to "Professor",
        Regex("\\bSt\\.") to "Saint",
        Regex("\\bFig\\.") to "Figure",
        Regex("\\bEq\\.") to "Equation"
    )

    /** Footnote markers / reference brackets that should not be spoken. */
    private val referenceBrackets = Regex("\\[\\s*\\d{1,3}(\\s*[,–-]\\s*\\d{1,3})*\\s*]")
    private val figureOnly = Regex("(?m)^\\s*(Gambar|Tabel|Figure|Table)\\s+\\d+(\\.\\d+)*\\s*[:.]?\\s*$")
    private val urlPattern = Regex("(https?://\\S+|www\\.\\S+)")
    private val emailPattern = Regex("\\S+@\\S+\\.[a-zA-Z]{2,}")
    private val dotLeader = Regex("\\.{3,}")
    private val loneBullet = Regex("(?m)^[ \\t]*[•▪◦●○■□►▶–—*·][ \\t]*")
    private val pageNumberLine = Regex(
        "(?mi)^[ \\t]*(halaman|page|hal\\.?)?[ \\t]*-?[ \\t]*\\d{1,4}[ \\t]*-?[ \\t]*(dari|of|/)?[ \\t]*\\d{0,4}[ \\t]*$"
    )

    /**
     * Roman numerals 2..39 (front matter never goes higher in practice).
     * Checked against a real set instead of a permissive regex, because "di",
     * "mi" or "mix" all match a naive roman-numeral pattern and would delete
     * real sentences. Single-letter numerals are left alone on purpose: a lone
     * "i" or "v" is far more likely to be text than a page number.
     */
    private val romanNumerals: Set<String> = buildSet {
        for (n in 2..39) roman(n).takeIf { it.length >= 2 }?.let { add(it) }
    }

    private fun roman(value: Int): String {
        val table = listOf(
            10 to "x", 9 to "ix", 5 to "v", 4 to "iv", 1 to "i"
        )
        var rest = value
        val sb = StringBuilder()
        for ((amount, symbol) in table) {
            while (rest >= amount) {
                sb.append(symbol)
                rest -= amount
            }
        }
        return sb.toString()
    }

    /**
     * Full clean-up of one page of extracted text.
     *
     * @param text raw text of a single page
     * @param stripRunningHeads whether to drop detected headers/footers
     */
    fun cleanPage(text: String, stripRunningHeads: Boolean = true): String {
        if (text.isBlank()) return ""

        // A soft hyphen is an invisible break hint: keep it only where the line
        // actually broke, and drop it when the word continues inline.
        var out = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("\u00AD[ \\t]*\\n"), "-\n")
            .replace("\u00AD", "")
            .replace('\u00A0', ' ')
            .replace('\u200B', ' ')
            .replace("ﬁ", "fi").replace("ﬂ", "fl")
            .replace("ﬀ", "ff").replace("ﬃ", "ffi").replace("ﬄ", "ffl")
            .replace('\u2018', '\'').replace('\u2019', '\'')
            .replace('\u201C', '"').replace('\u201D', '"')
            .replace('\u2013', '-').replace('\u2014', '-')

        out = urlPattern.replace(out, " ")
        out = emailPattern.replace(out, " alamat surel ")
        out = referenceBrackets.replace(out, " ")
        out = dotLeader.replace(out, ". ")

        if (stripRunningHeads) {
            out = out.lines()
                .filterNot { line ->
                    val trimmed = line.trim()
                    trimmed.isNotEmpty() &&
                        (
                            pageNumberLine.matches(trimmed) ||
                                romanNumerals.contains(trimmed.lowercase()) ||
                                figureOnly.matches(trimmed)
                            )
                }
                .joinToString("\n")
        }

        out = loneBullet.replace(out, "")

        // Re-join words split across lines: "pem-\nbaca" -> "pembaca".
        out = Regex("(\\p{Ll})-[ \\t]*\\n[ \\t]*(\\p{Ll})").replace(out) { m ->
            m.groupValues[1] + m.groupValues[2]
        }
        // A hard line break inside a paragraph is a space, not a stop. A newline
        // that follows another newline is a paragraph break and must survive,
        // otherwise the narration loses every pause between paragraphs.
        out = Regex("(?<![.!?:;\\n])\\n(?=[\\p{Ll}\\p{Lu}\\d\"'(])").replace(out, " ")

        out = multiSpace.replace(out, " ")
        out = out.lines().joinToString("\n") { it.trim() }
        out = multiNewline.replace(out, "\n\n")

        return out.trim()
    }

    /**
     * Removes running headers/footers: lines that repeat at the top or bottom
     * of many pages. Requires at least 4 pages to be reliable.
     */
    fun detectRunningHeads(pages: List<String>): Set<String> {
        if (pages.size < 4) return emptySet()
        val counter = mutableMapOf<String, Int>()

        pages.forEach { page ->
            val lines = page.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < 3) return@forEach
            val candidates = (lines.take(2) + lines.takeLast(2))
                .filter { it.length in 2..90 }
                .map { it.replace(multiSpace, " ").lowercase() }
                .toSet()
            candidates.forEach { counter[it] = (counter[it] ?: 0) + 1 }
        }

        val threshold = (pages.size * 0.5f).toInt().coerceAtLeast(2)
        return counter.filterValues { it >= threshold }.keys
    }

    fun stripRunningHeads(page: String, heads: Set<String>): String {
        if (heads.isEmpty()) return page
        return page.lines()
            .filterNot { heads.contains(it.trim().replace(multiSpace, " ").lowercase()) }
            .joinToString("\n")
    }

    /** Expands abbreviations and tidies punctuation right before speaking. */
    fun forSpeech(text: String): String {
        var out = text
        expansions.forEach { (regex, replacement) -> out = regex.replace(out, replacement) }

        // Spoken dashes / slashes that would otherwise be silent.
        out = out.replace(" / ", " atau ")
        out = out.replace(Regex("(\\d)\\s*[–-]\\s*(\\d)"), "$1 sampai $2")
        out = out.replace(Regex("(\\d)\\s*%"), "$1 persen")
        out = out.replace(Regex("\\s*&\\s*"), " dan ")
        out = out.replace(Regex("\\s+#{1,6}\\s*"), ". ")
        out = out.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ")
        out = multiSpace.replace(out, " ")
        return out.trim()
    }

    /** Short one-liner used on library cards. */
    fun snippet(text: String, maxChars: Int = 160): String {
        if (text.isBlank()) return ""
        val cleaned = forSpeech(cleanPage(text))
            .replace('\n', ' ')
            .trim()
        if (cleaned.length <= maxChars) return cleaned
        val cut = cleaned.take(maxChars)
        val lastStop = cut.lastIndexOfAny(charArrayOf('.', '!', '?', ' '))
        return (if (lastStop > maxChars / 2) cut.take(lastStop) else cut).trim() + "…"
    }

    fun collapseNewlines(text: String): String = multiSpace.replace(text.replace('\n', ' '), " ").trim()
}
