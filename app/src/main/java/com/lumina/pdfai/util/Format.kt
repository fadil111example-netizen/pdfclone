package com.lumina.pdfai.util

import android.content.Context
import android.net.Uri
import android.text.format.DateUtils
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Format {

    fun bytes(value: Long): String {
        if (value <= 0) return "—"
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = value.toDouble()
        var index = 0
        while (size >= 1024 && index < units.lastIndex) {
            size /= 1024
            index++
        }
        return if (index == 0) "${size.roundToInt()} ${units[index]}"
        else String.format(Locale.US, "%.1f %s", size, units[index])
    }

    fun relativeTime(context: Context, millis: Long): String {
        if (millis <= 0) return "Belum pernah dibuka"
        return DateUtils.getRelativeTimeSpanString(
            millis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    fun duration(seconds: Long): String {
        if (seconds <= 0) return "0 mnt"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return when {
            h > 0 && m > 0 -> "$h jam $m mnt"
            h > 0 -> "$h jam"
            m > 0 -> "$m mnt"
            else -> "$seconds dtk"
        }
    }

    fun countdown(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val m = total / 60
        val s = total % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun clock(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun pageLabel(page: Int, total: Int): String =
        if (total <= 0) "Hal. ${page + 1}" else "Hal. ${page + 1} / $total"

    fun percent(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100).roundToInt()}%"

    /** Zoom multiplier as a percentage, e.g. `1.5f` → `"150%"`. */
    fun zoomLabel(zoom: Float): String = "${(zoom.coerceIn(1f, 5f) * 100).roundToInt()}%"

    fun speechRateLabel(rate: Float): String = String.format(Locale.US, "%.2f×", rate)

    fun fileStem(name: String): String =
        name.substringBeforeLast('.', name).replace('_', ' ').trim()

    fun initials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts.first().take(2).uppercase(Locale.ROOT)
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase(Locale.ROOT)
        }
    }

    fun readableUri(uri: String): String {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return uri
        return parsed.lastPathSegment?.takeIf { it.isNotBlank() } ?: uri
    }

    /** Deterministic accent per document, used by the generated cover. */
    fun coverSeed(uri: String): Int = abs(uri.hashCode()) % 6
}
