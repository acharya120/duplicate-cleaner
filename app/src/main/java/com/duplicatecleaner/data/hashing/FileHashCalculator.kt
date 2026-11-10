package com.duplicatecleaner.data.hashing

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** Streaming SHA-256. Never holds more than one buffer of the file in memory. */
class FileHashCalculator @Inject constructor() {

    /**
     * @param open opens a fresh stream for the file, or returns null when it is not accessible.
     * @return lower-case hex SHA-256, or null if the file is unreadable/disappeared.
     * Cooperative cancellation: checked between every chunk; CancellationException propagates.
     */
    suspend fun sha256(open: () -> InputStream?): String? {
        val raw = try {
            open()
        } catch (e: IOException) {
            return null
        } catch (e: SecurityException) {
            return null
        } ?: return null
        return try {
            BufferedInputStream(raw, BUFFER_SIZE).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().toHex()
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    private fun ByteArray.toHex(): String {
        val hex = "0123456789abcdef"
        val out = CharArray(size * 2)
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            out[i * 2] = hex[v ushr 4]
            out[i * 2 + 1] = hex[v and 0x0F]
        }
        return String(out)
    }

    companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
