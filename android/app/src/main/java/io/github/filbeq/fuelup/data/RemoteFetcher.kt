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
}

/**
 * Plain HttpURLConnection. Android's implementation asks for gzip and
 * decompresses transparently, so the ~5 MB file travels as ~1.5 MB.
 */
class HttpFetcher(private val userAgent: String) : Fetcher {
    override fun open(url: String): InputStream {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        // Applies to each read: a stalled download fails instead of hanging forever.
        connection.readTimeout = READ_TIMEOUT_MS
        connection.useCaches = false
        connection.setRequestProperty("User-Agent", userAgent)
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw ServerException(code)
            return connection.inputStream
        } catch (e: IOException) {
            connection.disconnect()
            throw e
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
