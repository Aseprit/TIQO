package com.aseprit.tiqo.ai.model

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Single-file downloader based on AI Edge Gallery's Range + append flow, with strict resume checks. */
internal class ResumableModelDownloader(
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val retryDelaysMillis: List<Long> = listOf(500L, 1_500L, 3_000L),
) {
    suspend fun download(
        source: String,
        partial: File,
        userAgent: String,
        expectedSizeBytes: Long?,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Long {
        partial.parentFile?.mkdirs()
        var attempt = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                return transfer(source, partial, userAgent, expectedSizeBytes, onProgress)
            } catch (error: IOException) {
                if (attempt >= retryDelaysMillis.size) throw error
                delay(retryDelaysMillis[attempt++])
            }
        }
    }

    private suspend fun transfer(
        source: String,
        partial: File,
        userAgent: String,
        expectedSizeBytes: Long?,
        onProgress: (Long, Long?) -> Unit,
    ): Long {
        val requestedOffset = partial.takeIf(File::isFile)?.length() ?: 0L
        val connection = connectFollowingRedirects(URL(source), requestedOffset, userAgent)
        try {
            val responseCode = connection.responseCode
            if (responseCode == HTTP_RANGE_NOT_SATISFIABLE && requestedOffset > 0L) {
                val remoteSize = parseUnsatisfiedTotal(connection.getHeaderField("Content-Range"))
                if (remoteSize == requestedOffset || expectedSizeBytes == requestedOffset) {
                    onProgress(requestedOffset, remoteSize ?: expectedSizeBytes)
                    return requestedOffset
                }
            }
            if (responseCode in TRANSIENT_HTTP_CODES) {
                throw IOException("Serwer zwrócił przejściowy HTTP $responseCode")
            }
            require(responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                "Serwer zwrócił HTTP $responseCode"
            }

            val append = responseCode == HttpURLConnection.HTTP_PARTIAL
            val baseBytes = if (append) {
                val start = parseContentRange(connection.getHeaderField("Content-Range"))?.first
                    ?: error("Brak poprawnego Content-Range dla wznowienia")
                require(start == requestedOffset) {
                    "Serwer wznowił od bajtu $start zamiast $requestedOffset"
                }
                requestedOffset
            } else {
                0L
            }
            val rangeTotal = parseContentRange(connection.getHeaderField("Content-Range"))?.second
            val totalBytes = rangeTotal
                ?: connection.contentLengthLong.takeIf { it > 0 }?.let { baseBytes + it }
                ?: expectedSizeBytes
            var downloadedBytes = baseBytes
            onProgress(downloadedBytes, totalBytes)
            connection.inputStream.buffered().use { input ->
                java.io.FileOutputStream(partial, append).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloadedBytes += count
                        onProgress(downloadedBytes, totalBytes)
                    }
                }
            }
            return downloadedBytes
        } finally {
            connection.disconnect()
        }
    }

    private fun connectFollowingRedirects(initialUrl: URL, offset: Long, userAgent: String): HttpURLConnection {
        var current = initialUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = connectionFactory(current).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept-Encoding", "identity")
                if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
            }
            connection.connect()
            if (connection.responseCode !in REDIRECT_HTTP_CODES) return connection
            val location = connection.getHeaderField("Location")
            if (location == null) {
                connection.disconnect()
                throw IOException("Przekierowanie HTTP bez nagłówka Location")
            }
            connection.disconnect()
            if (redirectCount == MAX_REDIRECTS) throw IOException("Zbyt wiele przekierowań podczas pobierania")
            current = URL(current, location)
        }
        error("Nieosiągalne")
    }

    private fun parseContentRange(value: String?): Pair<Long, Long?>? {
        val match = CONTENT_RANGE.matchEntire(value?.trim().orEmpty()) ?: return null
        return match.groupValues[1].toLong() to match.groupValues[3].takeUnless { it == "*" }?.toLong()
    }

    private fun parseUnsatisfiedTotal(value: String?): Long? =
        UNSATISFIED_RANGE.matchEntire(value?.trim().orEmpty())?.groupValues?.get(1)?.toLong()

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 20_000
        const val READ_TIMEOUT_MILLIS = 30_000
        const val MAX_REDIRECTS = 8
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        val REDIRECT_HTTP_CODES = setOf(301, 302, 303, 307, 308)
        val TRANSIENT_HTTP_CODES = setOf(408, 425, 429, 500, 502, 503, 504)
        val CONTENT_RANGE = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)
        val UNSATISFIED_RANGE = Regex("""bytes\s+\*/(\d+)""", RegexOption.IGNORE_CASE)
    }
}
