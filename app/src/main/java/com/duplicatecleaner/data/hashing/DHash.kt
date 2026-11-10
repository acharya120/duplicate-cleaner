package com.duplicatecleaner.data.hashing

import com.duplicatecleaner.domain.model.SimilarityLevel

/** Pure difference-hash helpers (no Android dependencies, fully unit-testable). */
object DHash {
    const val WIDTH = 9
    const val HEIGHT = 8

    /** Rec.601 luma of an ARGB pixel, 0..255. */
    fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (299 * r + 587 * g + 114 * b) / 1000
    }

    /** [gray] is a row-major 9x8 matrix (72 values). Bit = left pixel brighter than right neighbour. */
    fun fromGray(gray: IntArray): Long {
        require(gray.size == WIDTH * HEIGHT) { "Expected ${WIDTH * HEIGHT} samples" }
        var hash = 0L
        var bit = 0
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH - 1) {
                val left = gray[y * WIDTH + x]
                val right = gray[y * WIDTH + x + 1]
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /**
     * 0 = identical signature, 1..5 very similar, 6..[maxDistance] potentially similar.
     * Starting-point thresholds; the user can lower [maxDistance] in settings.
     */
    fun classify(distance: Int, maxDistance: Int = 10): SimilarityLevel = when {
        distance == 0 -> SimilarityLevel.IDENTICAL
        distance > maxDistance -> SimilarityLevel.NOT_SIMILAR
        distance <= 5 -> SimilarityLevel.VERY_SIMILAR
        else -> SimilarityLevel.POTENTIALLY_SIMILAR
    }
}
