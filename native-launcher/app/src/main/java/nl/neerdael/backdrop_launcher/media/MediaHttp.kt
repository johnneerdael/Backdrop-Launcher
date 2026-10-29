package nl.neerdael.backdrop_launcher.media

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL

internal data class MediaRequest(val url: String, val method: String = "GET",
    val headers: Map<String, String> = emptyMap(), val body: ByteArray? = null,
    val maxBytes: Int = 1024 * 1024, val timeoutMillis: Int = 20_000,
    val followImageRedirects: Boolean = false)
internal data class MediaResponse(val status: Int, val headers: Map<String, String>, val bytes: ByteArray) {
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
    fun text(): String = bytes.toString(Charsets.UTF_8)
}
internal fun interface MediaTransport { fun execute(request: MediaRequest): MediaResponse }

/** Does not include remote URLs or headers in exceptions: they can contain API credentials. */
internal object UrlMediaTransport : MediaTransport {
    override fun execute(request: MediaRequest): MediaResponse = execute(request, 0,
        System.nanoTime() + request.timeoutMillis.toLong() * 1_000_000)
    private fun execute(request: MediaRequest, redirects: Int, deadlineNanos: Long): MediaResponse {
        ensureMediaActive()
        val url = URL(request.url)
        require(url.protocol == "https" || url.protocol == "http") { "Unsupported media transport" }
        val connection = url.openConnection() as HttpURLConnection
        try {
            val remainingMillis = ((deadlineNanos - System.nanoTime()) / 1_000_000).coerceAtMost(request.timeoutMillis.toLong())
            if (remainingMillis <= 0) throw IOException("Media request timed out")
            connection.connectTimeout = remainingMillis.toInt()
            connection.readTimeout = remainingMillis.toInt()
            connection.instanceFollowRedirects = false
            connection.requestMethod = request.method
            request.headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            request.body?.let { body ->
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            if (request.followImageRedirects && status in setOf(301, 302, 303, 307, 308)) {
                if (redirects >= 3) throw IOException("Too many media redirects")
                val location = connection.getHeaderField("Location") ?: throw IOException("Invalid media redirect")
                val next = URL(url, location)
                if (next.protocol !in setOf("http", "https") || next.userInfo != null ||
                    (url.protocol == "https" && next.protocol != "https") || request.headers.isNotEmpty() || request.body != null)
                    throw IOException("Unsupported media redirect")
                connection.disconnect()
                return execute(request.copy(url = next.toString()), redirects + 1, deadlineNanos)
            }
            val length = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1
            if (length > request.maxBytes) throw IOException("Media response exceeds size limit")
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { readBounded(it, request.maxBytes, deadlineNanos) } ?: byteArrayOf()
            val headers = connection.headerFields.entries.filter { it.key != null }
                .associate { it.key!! to it.value.joinToString(",") }
            return MediaResponse(status, headers, bytes)
        } catch (_: IOException) {
            throw IOException("Media request failed")
        } finally {
            connection.disconnect()
        }
    }
}

internal fun readBounded(input: InputStream, maxBytes: Int, deadlineNanos: Long = Long.MAX_VALUE): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        ensureMediaActive()
        if (System.nanoTime() >= deadlineNanos) throw IOException("Media request timed out")
        val count = input.read(buffer)
        ensureMediaActive()
        if (System.nanoTime() >= deadlineNanos) throw IOException("Media request timed out")
        if (count < 0) break
        if (output.size().toLong() + count > maxBytes) throw IOException("Media response exceeds size limit")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal fun ensureMediaActive() {
    if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Media operation cancelled")
}
