package com.duplicatecleaner.data.hashing

import com.duplicatecleaner.domain.model.SimilarityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DHashTest {
    private fun ramp(descending: Boolean) =
        IntArray(72) { i -> val x = i % 9; if (descending) 255 - x * 20 else x * 20 }

    @Test fun `descending ramp sets all 64 bits`() = assertEquals(-1L, DHash.fromGray(ramp(true)))
    @Test fun `ascending ramp sets no bits`() = assertEquals(0L, DHash.fromGray(ramp(false)))
    @Test fun `flat image hashes to zero`() = assertEquals(0L, DHash.fromGray(IntArray(72) { 128 }))

    @Test fun `brightness shift does not change hash`() {
        val base = ramp(true)
        val brighter = IntArray(72) { (base[it] - 30).coerceAtLeast(0) }
        assertEquals(DHash.fromGray(base), DHash.fromGray(brighter))
    }

    @Test fun `different structure gives different hash`() =
        assertNotEquals(DHash.fromGray(ramp(true)), DHash.fromGray(ramp(false)))

    @Test fun `hamming distance counts differing bits`() {
        assertEquals(0, DHash.hamming(5L, 5L))
        assertEquals(3, DHash.hamming(0b1011L, 0b0000L))
        assertEquals(64, DHash.hamming(0L, -1L))
    }

    @Test fun `luma of pure colours`() {
        assertEquals(255, DHash.luma(0xFFFFFFFF.toInt()))
        assertEquals(0, DHash.luma(0xFF000000.toInt()))
    }

    @Test fun `classification thresholds`() {
        assertEquals(SimilarityLevel.IDENTICAL, DHash.classify(0))
        assertEquals(SimilarityLevel.VERY_SIMILAR, DHash.classify(1))
        assertEquals(SimilarityLevel.VERY_SIMILAR, DHash.classify(5))
        assertEquals(SimilarityLevel.POTENTIALLY_SIMILAR, DHash.classify(6))
        assertEquals(SimilarityLevel.POTENTIALLY_SIMILAR, DHash.classify(10))
        assertEquals(SimilarityLevel.NOT_SIMILAR, DHash.classify(11))
        assertEquals(SimilarityLevel.NOT_SIMILAR, DHash.classify(8, maxDistance = 6))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `wrong matrix size is rejected`() { DHash.fromGray(IntArray(10)) }
}
