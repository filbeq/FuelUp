package io.github.filbeq.fuelup.data

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** The server answered, but not with the file (e.g. HTTP 404 or 500). */
class ServerException(val code: Int) : IOException("HTTP $code")

/** Opens a remote file for reading. Any other [IOException] means "no connection". */
fun interface Fetcher {
    @Throws(IOException::class)
    fun open(url: String): InputStream

    /**
     * The start of a file: at least its first [bytes] bytes, when the server
     * honours a range request; otherwise the whole file (read only what you need).
     */
    @Throws(IOException::class)
    fun openStart(url: String, bytes: Int): InputStream = open(url)
}

/**
 * Plain HttpURLConnection. Android's implementation asks for gzip and
 * decompresses transparently, so the ~5 MB stations.json travels as ~1.5 MB
 * (MIMIT's files are not compressed).
 */
class HttpFetcher(private val userAgent: String) : Fetcher {
    override fun open(url: String): InputStream = connect(url, range = null)

    override fun openStart(url: String, bytes: Int): InputStream = connect(url, range = "bytes=0-${bytes - 1}")

    private fun connect(url: String, range: String?): InputStream {
        val connection = URL(url).openConnection() as HttpURLConnection
        // Short: from outside Italy MIMIT doesn't answer at all, and the app then
        // falls back to the published copy.
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        // Applies to each read: a stalled download fails instead of hanging forever.
        connection.readTimeout = READ_TIMEOUT_MS
        connection.useCaches = false
        connection.setRequestProperty("User-Agent", userAgent)
        range?.let { connection.setRequestProperty("Range", it) }
        try {
            val code = connection.responseCode
            val ok = code == HttpURLConnection.HTTP_OK || (range != null && code == HttpURLConnection.HTTP_PARTIAL)
            if (!ok) throw ServerException(code)
            return connection.inputStream
        } catch (e: IOException) {
            connection.disconnect()
            throw e
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
