package com.duplicatecleaner.data.hashing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/**
 * Decodes a down-sampled bitmap, applies EXIF orientation, shrinks to 9x8 greyscale and returns a
 * 64-bit dHash. Never decodes a full-resolution image.
 */
class PerceptualHashCalculator @Inject constructor() {

    data class ImageInfo(val hash: Long, val width: Int, val height: Int)

    /** [open] must return a fresh stream on every call (it is invoked up to three times). */
    fun compute(open: () -> InputStream?): ImageInfo? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = open()?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (decoded == null) {
                null
            } else {
                val rotation = open()?.use { readRotation(it) } ?: 0
                val oriented = rotate(decoded, rotation)
                val small = Bitmap.createScaledBitmap(oriented, DHash.WIDTH, DHash.HEIGHT, true)
                val pixels = IntArray(DHash.WIDTH * DHash.HEIGHT)
                small.getPixels(pixels, 0, DHash.WIDTH, 0, 0, DHash.WIDTH, DHash.HEIGHT)
                val gray = IntArray(pixels.size) { DHash.luma(pixels[it]) }
                if (small !== oriented) small.recycle()
                if (oriented !== decoded) oriented.recycle()
                decoded.recycle()
                val swap = rotation == 90 || rotation == 270
                ImageInfo(
                    hash = DHash.fromGray(gray),
                    width = if (swap) bounds.outHeight else bounds.outWidth,
                    height = if (swap) bounds.outWidth else bounds.outHeight,
                )
            }
        }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    } catch (e: OutOfMemoryError) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun sampleSize(w: Int, h: Int): Int {
        var sample = 1
        while (w / (sample * 2) >= TARGET_DECODE && h / (sample * 2) >= TARGET_DECODE) sample *= 2
        return sample
    }

    private fun readRotation(stream: InputStream): Int = try {
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: IOException) {
        0
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private companion object {
        const val TARGET_DECODE = 64
    }
}
