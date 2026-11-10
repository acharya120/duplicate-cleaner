package com.duplicatecleaner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duplicatecleaner.data.hashing.DHash
import com.duplicatecleaner.data.hashing.PerceptualHashCalculator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Validates the dHash thresholds against real decoded bitmaps (resize, recompress, different scene). */
@RunWith(AndroidJUnit4::class)
class PerceptualHashInstrumentedTest {
    private val calc = PerceptualHashCalculator()

    private fun scene(w: Int, h: Int, seed: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(20 + seed * 7, 40, 90))
        val p = Paint()
        val rnd = java.util.Random(seed.toLong())
        repeat(14) {
            p.color = Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            val x = rnd.nextInt(w - w / 4); val y = rnd.nextInt(h - h / 4)
            c.drawRect(x.toFloat(), y.toFloat(), (x + w / 4).toFloat(), (y + h / 4).toFloat(), p)
        }
        return bmp
    }

    private fun encode(b: Bitmap, fmt: Bitmap.CompressFormat, q: Int) = ByteArrayOutputStream().also { b.compress(fmt, q, it) }.toByteArray()
    private fun hash(bytes: ByteArray) = calc.compute { ByteArrayInputStream(bytes) }

    @Test fun resizedAndRecompressedCopyIsVerySimilar() {
        val original = scene(1600, 1200, 1)
        val small = Bitmap.createScaledBitmap(original, 400, 300, true)
        val a = hash(encode(original, Bitmap.CompressFormat.JPEG, 95))!!
        val b = hash(encode(small, Bitmap.CompressFormat.JPEG, 60))!!
        val d = DHash.hamming(a.hash, b.hash)
        assertTrue("distance $d should be within the very-similar band", d <= 5)
    }

    @Test fun differentSceneIsNotSimilar() {
        val a = hash(encode(scene(800, 600, 1), Bitmap.CompressFormat.PNG, 100))!!
        val b = hash(encode(scene(800, 600, 9), Bitmap.CompressFormat.PNG, 100))!!
        assertTrue(DHash.hamming(a.hash, b.hash) > 10)
    }

    @Test fun identicalImagesHaveDistanceZeroAndReportDimensions() {
        val bytes = encode(scene(640, 480, 3), Bitmap.CompressFormat.PNG, 100)
        val a = hash(bytes)!!; val b = hash(bytes)!!
        assertEquals(0, DHash.hamming(a.hash, b.hash))
        assertEquals(640, a.width); assertEquals(480, a.height)
    }

    @Test fun corruptedAndEmptyStreamsReturnNullWithoutCrashing() {
        assertNull(hash(ByteArray(100) { 7 }))
        assertNull(hash(ByteArray(0)))
        assertNull(calc.compute { null })
    }

    @Test fun largeImageIsDecodedDownsampled() {
        assertNotNull(hash(encode(scene(6000, 4000, 2), Bitmap.CompressFormat.JPEG, 80)))
    }
}
