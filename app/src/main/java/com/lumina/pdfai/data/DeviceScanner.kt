package com.lumina.pdfai.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.lumina.pdfai.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Finds PDF files already on the phone so the library is useful on first launch
 * without opening the system picker ten times.
 *
 * Uses MediaStore (fast, no full disk walk) and falls back to a bounded manual
 * walk of the usual document folders when MediaStore has not indexed the file
 * yet. Never writes anything.
 */
class DeviceScanner(private val context: Context) {

    data class ScanResult(
        val entries: List<PdfEntry>,
        val readable: Boolean,
        val note: String? = null
    )

    suspend fun scan(limit: Int = 400): ScanResult = withContext(Dispatchers.IO) {
        val found = mutableMapOf<String, PdfEntry>()

        runCatching { scanMediaStore(found, limit) }
        if (found.size < limit) runCatching { scanFolders(found, limit) }

        ScanResult(
            entries = found.values.sortedByDescending { it.addedAt }.take(limit),
            readable = true,
            note = if (found.isEmpty()) "Tidak ada PDF di penyimpanan yang bisa diakses." else null
        )
    }

    private fun scanMediaStore(target: MutableMap<String, PdfEntry>, limit: Int) {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA
        )

        val selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=?" + " OR " +
            MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ?"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_NONE.toString(),
            "%.pdf"
        )

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

            while (cursor.moveToNext() && target.size < limit) {
                val name = cursor.getString(nameCol) ?: continue
                if (!name.lowercase().endsWith(".pdf")) continue

                val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                val itemUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || path == null) {
                    ContentUris.withAppendedId(uri, cursor.getLong(idCol))
                } else {
                    Uri.fromFile(File(path))
                }

                val size = cursor.getLong(sizeCol)
                if (size <= 0) continue
                val modified = cursor.getLong(dateCol) * 1000

                // keyed by real path when known, so the folder walk cannot add the same file again
                target[path ?: itemUri.toString()] = PdfEntry(
                    uri = itemUri.toString(),
                    name = name,
                    sizeBytes = size,
                    addedAt = modified,
                    snippet = Format.fileStem(name)
                )
            }
        }
    }

    private fun scanFolders(target: MutableMap<String, PdfEntry>, limit: Int) {
        val roots = buildList {
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).parentFile)
        }.filterNotNull().distinct()

        roots.forEach { root ->
            if (target.size >= limit) return
            walk(root, target, limit, depth = 0)
        }
    }

    private fun walk(dir: File?, target: MutableMap<String, PdfEntry>, limit: Int, depth: Int) {
        if (dir == null || !dir.isDirectory || depth > 6 || target.size >= limit) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return

        children.forEach { child ->
            if (target.size >= limit) return
            if (child.isDirectory) {
                val name = child.name.lowercase()
                if (!name.startsWith(".") && name !in SKIP_DIRS) {
                    walk(child, target, limit, depth + 1)
                }
            } else if (child.name.lowercase().endsWith(".pdf") && child.length() > 0) {
                val uri = Uri.fromFile(child)
                target.putIfAbsent(
                    child.absolutePath,
                    PdfEntry(
                        uri = uri.toString(),
                        name = child.name,
                        sizeBytes = child.length(),
                        addedAt = child.lastModified(),
                        snippet = Format.fileStem(child.name)
                    )
                )
            }
        }
    }

    /** Imports everything inside a folder the user picked (tree uri). */
    suspend fun scanTree(treeUri: Uri, limit: Int = 400): List<PdfEntry> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val out = mutableListOf<PdfEntry>()
        collect(root, out, limit, 0)
        out
    }

    private fun collect(dir: DocumentFile, out: MutableList<PdfEntry>, limit: Int, depth: Int) {
        if (depth > 8 || out.size >= limit) return
        runCatching { dir.listFiles() }.getOrDefault(emptyArray()).forEach { file ->
            if (out.size >= limit) return
            if (file.isDirectory) {
                collect(file, out, limit, depth + 1)
            } else if (file.name?.lowercase()?.endsWith(".pdf") == true) {
                out += PdfEntry(
                    uri = file.uri.toString(),
                    name = file.name ?: "Dokumen.pdf",
                    sizeBytes = file.length(),
                    addedAt = file.lastModified(),
                    snippet = Format.fileStem(file.name ?: "")
                )
            }
        }
    }

    companion object {
        private val SKIP_DIRS = setOf("android", "dcim", "movies", "music", "pictures", "ringtones", "alarms", "notifications")
    }
}
