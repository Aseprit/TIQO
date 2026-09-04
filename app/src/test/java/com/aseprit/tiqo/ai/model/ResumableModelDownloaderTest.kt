package com.aseprit.tiqo.ai.model

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumableModelDownloaderTest {
    @Test fun resumeAppendsToExistingPartialInsteadOfOverwritingIt() = runBlocking {
        val directory = Files.createTempDirectory("tiqo-resume").toFile()
        try {
            val partial = File(directory, "model.litertlm.partial").apply { writeText("hello ") }
            val connection = FakeHttpConnection(
                response = HttpURLConnection.HTTP_PARTIAL,
                body = "world".toByteArray(),
                headers = mapOf("Content-Range" to "bytes 6-10/11"),
            )
            val downloader = ResumableModelDownloader(connectionFactory = { connection }, retryDelaysMillis = emptyList())

            val downloaded = downloader.download("https://example.test/model", partial, "test", 11L) { _, _ -> }

            assertEquals("bytes=6-", connection.requestHeaders["Range"])
            assertEquals("identity", connection.requestHeaders["Accept-Encoding"])
            assertEquals("hello world", partial.readText())
            assertEquals(11L, downloaded)
        } finally {
            directory.deleteRecursively()
        }
    }
}

private class FakeHttpConnection(
    private val response: Int,
    private val body: ByteArray,
    private val headers: Map<String, String>,
) : HttpURLConnection(URL("https://example.test")) {
    val requestHeaders = mutableMapOf<String, String>()
    override fun connect() { connected = true }
    override fun disconnect() { connected = false }
    override fun usingProxy() = false
    override fun getResponseCode() = response
    override fun getInputStream(): InputStream = ByteArrayInputStream(body)
    override fun getHeaderField(name: String?): String? = name?.let(headers::get)
    override fun getContentLengthLong(): Long = body.size.toLong()
    override fun setRequestProperty(key: String, value: String) { requestHeaders[key] = value }
}
