package com.duplicatecleaner.data.hashing

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileHashCalculatorTest {
    private val calc = FileHashCalculator()

    @Test fun `sha256 of abc matches known vector`() = runTest {
        val h = calc.sha256 { ByteArrayInputStream("abc".toByteArray()) }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h)
    }

    @Test fun `sha256 of empty stream matches known vector`() = runTest {
        val h = calc.sha256 { ByteArrayInputStream(ByteArray(0)) }
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", h)
    }

    @Test fun `same content gives same hash even with different sources`() = runTest {
        val data = ByteArray(200_000) { (it % 251).toByte() }
        assertEquals(calc.sha256 { ByteArrayInputStream(data) }, calc.sha256 { ByteArrayInputStream(data.copyOf()) })
    }

    @Test fun `different content with identical size gives different hash`() = runTest {
        val a = ByteArray(1024) { 1 }
        val b = ByteArray(1024) { 2 }
        assertNotEquals(calc.sha256 { ByteArrayInputStream(a) }, calc.sha256 { ByteArrayInputStream(b) })
    }

    @Test fun `null stream returns null`() = runTest { assertNull(calc.sha256 { null }) }

    @Test fun `open that throws SecurityException returns null`() = runTest {
        assertNull(calc.sha256 { throw SecurityException("revoked") })
    }

    @Test fun `stream failing mid-read returns null instead of crashing`() = runTest {
        val broken = object : InputStream() {
            var n = 0
            override fun read(): Int = if (n++ < 10) 1 else throw IOException("disk gone")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("disk gone")
        }
        assertNull(calc.sha256 { broken })
    }

    @Test fun `large stream is hashed in chunks and matches reference digest`() = runTest {
        val total = 40L * 1024 * 1024
        fun gen() = object : InputStream() {
            var pos = 0L
            override fun read(): Int = if (pos >= total) -1 else ((pos++ * 31) and 0xFF).toInt()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos >= total) return -1
                val n = minOf(len.toLong(), total - pos).toInt()
                for (i in 0 until n) b[off + i] = (((pos + i) * 31) and 0xFF).toByte()
                pos += n
                return n
            }
        }
        val ref = MessageDigest.getInstance("SHA-256").also { d ->
            val s = gen(); val buf = ByteArray(8192)
            while (true) { val r = s.read(buf); if (r < 0) break; d.update(buf, 0, r) }
        }.digest().joinToString("") { "%02x".format(it) }
        assertEquals(ref, calc.sha256 { gen() })
    }

    @Test fun `cancellation stops hashing of an endless stream`() = runBlocking {
        val endless = object : InputStream() {
            override fun read(): Int = 7
            override fun read(b: ByteArray, off: Int, len: Int): Int = len
        }
        val job = launch(Dispatchers.Default) { calc.sha256 { endless } }
        delay(100)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
}
