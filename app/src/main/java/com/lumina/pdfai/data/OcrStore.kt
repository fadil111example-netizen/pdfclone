package com.lumina.pdfai.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Keeps OCR text per page on the phone, so a scanned PDF only has to be
 * recognised once. Every page is written the moment it is done: if the
 * connection drops at page 60 of 111, the next run continues from page 61.
 */
class OcrStore(context: Context) {

    private val root = File(context.filesDir, "ocr")

    private fun dirFor(uri: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(uri.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }.take(24)
        return File(root, name)
    }

    private fun pageFile(uri: String, index: Int): File =
        File(dirFor(uri), "p_%05d.txt".format(index))

    fun hasPage(uri: String, index: Int): Boolean = pageFile(uri, index).isFile

    fun countDone(uri: String, total: Int): Int =
        (0 until total).count { hasPage(uri, it) }

    fun savePage(uri: String, index: Int, text: String) {
        val dir = dirFor(uri)
        dir.mkdirs()
        val target = pageFile(uri, index)
        val tmp = File(dir, target.name + ".part")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.writeText(text, Charsets.UTF_8)
            tmp.delete()
        }
    }

    /** All pages in order, or null when at least one page is still missing. */
    fun loadComplete(uri: String, total: Int): List<String>? {
        if (total <= 0) return null
        val pages = ArrayList<String>(total)
        for (index in 0 until total) {
            val file = pageFile(uri, index)
            if (!file.isFile) return null
            pages.add(file.readText(Charsets.UTF_8))
        }
        return pages
    }

    fun clear(uri: String) {
        runCatching { dirFor(uri).deleteRecursively() }
    }
}
