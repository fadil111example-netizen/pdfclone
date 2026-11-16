package com.lumina.pdfai.pdf

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Failure while talking to OCR.space.
 * [fatal] means retrying is pointless (wrong API key, quota used up).
 */
class OcrException(message: String, val fatal: Boolean = false) : Exception(message)

/**
 * Minimal OCR.space client (https://ocr.space/ocrapi) with no extra
 * dependencies. One call = one image (JPEG of a single PDF page), which works
 * for free keys (1 MB / 3 pages per request) as well as PRO keys.
 */
class OcrSpaceClient {

    /** Blocking call: run it on an IO dispatcher. Returns the recognised text. */
    fun recognize(jpeg: ByteArray, apiKey: String, engine: Int): String {
        val boundary = "----Lumina" + UUID.randomUUID().toString().replace("-", "")
        val body = buildBody(boundary, jpeg, engine)

        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 150_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", apiKey)
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code == 401 || code == 403) {
                throw OcrException(
                    "API key ditolak atau kuota OCR.space habis (HTTP $code). Periksa API key Anda.",
                    fatal = true
                )
            }
            if (code !in 200..299) {
                throw OcrException("Server OCR.space membalas HTTP $code")
            }
            return parse(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(text: String): String {
        val json = try {
            JSONObject(text)
        } catch (t: Throwable) {
            throw OcrException("Balasan OCR.space tidak bisa dibaca")
        }

        if (json.optBoolean("IsErroredOnProcessing", false)) {
            val message = errorText(json.opt("ErrorMessage")).ifBlank { "OCR gagal diproses" }
            val lower = message.lowercase()
            val fatal = "api key" in lower || "apikey" in lower || "limit" in lower || "exceeded" in lower
            throw OcrException(message, fatal = fatal)
        }

        val results = json.optJSONArray("ParsedResults")
            ?: throw OcrException("Hasil OCR kosong")
        val first = results.optJSONObject(0) ?: return ""
        return if (first.isNull("ParsedText")) "" else first.optString("ParsedText", "")
    }

    private fun errorText(value: Any?): String = when (value) {
        null, JSONObject.NULL -> ""
        is JSONArray -> (0 until value.length()).joinToString("; ") { value.optString(it) }
        else -> value.toString()
    }

    private fun buildBody(boundary: String, jpeg: ByteArray, engine: Int): ByteArray {
        val out = ByteArrayOutputStream(jpeg.size + 2048)

        fun field(name: String, value: String) {
            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
            out.write(value.toByteArray())
            out.write("\r\n".toByteArray())
        }

        field("OCREngine", engine.toString())
        field("isOverlayRequired", "false")
        field("detectOrientation", "true")
        field("scale", "true")

        out.write("--$boundary\r\n".toByteArray())
        out.write(
            "Content-Disposition: form-data; name=\"file\"; filename=\"page.jpg\"\r\n".toByteArray()
        )
        out.write("Content-Type: image/jpeg\r\n\r\n".toByteArray())
        out.write(jpeg)
        out.write("\r\n".toByteArray())
        out.write("--$boundary--\r\n".toByteArray())
        return out.toByteArray()
    }

    companion object {
        private const val ENDPOINT = "https://api.ocr.space/parse/image"
    }
}
