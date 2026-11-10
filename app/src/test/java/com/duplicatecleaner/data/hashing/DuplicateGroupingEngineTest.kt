package com.duplicatecleaner.data.hashing

import com.duplicatecleaner.domain.model.FileCategory
import com.duplicatecleaner.domain.model.GroupType
import com.duplicatecleaner.testFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateGroupingEngineTest {
    private val engine = DuplicateGroupingEngine()

    @Test fun `same content in different names is grouped`() {
        val g = engine.groupExact(listOf(
            testFile("a", "x.jpg", sha = "H1"), testFile("b", "y.jpg", sha = "H1"), testFile("c", "z.jpg", sha = "H2"),
        ))
        assertEquals(1, g.size)
        assertEquals(setOf("a", "b"), g[0].files.map { it.uri }.toSet())
        assertEquals(GroupType.EXACT_PHOTO, g[0].type)
    }

    @Test fun `identical names but different content are not grouped`() {
        val g = engine.groupExact(listOf(testFile("a", "IMG.jpg", sha = "H1"), testFile("b", "IMG.jpg", sha = "H2")))
        assertTrue(g.isEmpty())
    }

    @Test fun `identical sizes without hashes are never exact duplicates`() {
        assertTrue(engine.groupExact(listOf(testFile("a", size = 5), testFile("b", size = 5))).isEmpty())
    }

    @Test fun `equal hash but different size is not grouped`() {
        assertTrue(engine.groupExact(listOf(testFile("a", size = 5, sha = "H"), testFile("b", size = 6, sha = "H"))).isEmpty())
    }

    @Test fun `empty files are ignored`() {
        val g = engine.groupExact(listOf(testFile("a", size = 0, sha = "E"), testFile("b", size = 0, sha = "E")))
        assertTrue(g.isEmpty())
    }

    @Test fun `same file seen via two URIs is not a duplicate of itself`() {
        val g = engine.groupExact(listOf(
            testFile("content://media/1", sha = "H", key = "primary:docs/a.pdf"),
            testFile("content://tree/a", sha = "H", key = "primary:docs/a.pdf"),
        ))
        assertTrue(g.isEmpty())
    }

    @Test fun `same MediaStore item listed twice is processed once`() {
        assertTrue(engine.groupExact(listOf(testFile("a", sha = "H"), testFile("a", sha = "H"))).isEmpty())
    }

    @Test fun `categories are kept apart`() {
        val g = engine.groupExact(listOf(
            testFile("a", sha = "H", category = FileCategory.PHOTO), testFile("b", sha = "H", category = FileCategory.DOCUMENT),
        ))
        assertTrue(g.isEmpty())
    }

    @Test fun `size candidates keep only shared sizes`() {
        val c = engine.sizeCandidates(listOf(testFile("a", size = 1), testFile("b", size = 1), testFile("c", size = 2), testFile("d", size = 0), testFile("e", size = 0)))
        assertEquals(setOf("a", "b"), c.map { it.uri }.toSet())
    }

    @Test fun `recoverable bytes excludes the largest copy`() {
        val g = engine.groupExact(listOf(testFile("a", size = 10, sha = "H"), testFile("b", size = 10, sha = "H"), testFile("c", size = 10, sha = "H")))
        assertEquals(20L, g[0].recoverableBytes)
        assertEquals(2, g[0].extraCopies)
    }

    @Test fun `similar photos within threshold are grouped`() {
        val g = engine.groupSimilar(listOf(
            testFile("a", phash = 0L, modified = 0), testFile("b", phash = 0b111L, modified = 1_000_000),
            testFile("far", phash = -1L, modified = 5),
        ), maxDistance = 10)
        assertEquals(1, g.size)
        assertEquals(setOf("a", "b"), g[0].files.map { it.uri }.toSet())
        assertEquals(GroupType.SIMILAR_PHOTO, g[0].type)
    }

    @Test fun `photos above threshold are not grouped`() {
        val g = engine.groupSimilar(listOf(testFile("a", phash = 0L), testFile("b", phash = 0xFFFL)), maxDistance = 10)
        assertTrue(g.isEmpty())
    }

    @Test fun `threshold is configurable`() {
        val pair = listOf(testFile("a", phash = 0L), testFile("b", phash = 0x3FL)) // distance 6
        assertTrue(engine.groupSimilar(pair, maxDistance = 5).isEmpty())
        assertEquals(1, engine.groupSimilar(pair, maxDistance = 6).size)
    }

    @Test fun `incompatible aspect ratios are not grouped`() {
        val g = engine.groupSimilar(listOf(testFile("a", phash = 0L, w = 4000, h = 3000), testFile("b", phash = 1L, w = 3000, h = 4000 * 2)), 10)
        assertTrue(g.isEmpty())
    }

    @Test fun `resized copy with same aspect ratio is grouped`() {
        val g = engine.groupSimilar(listOf(testFile("a", phash = 0L, w = 4000, h = 3000), testFile("b", phash = 2L, w = 800, h = 600)), 10)
        assertEquals(1, g.size)
    }

    @Test fun `photos taken seconds apart are classified as burst`() {
        val g = engine.groupSimilar(listOf(testFile("a", phash = 0L, modified = 1000), testFile("b", phash = 1L, modified = 3000)), 10)
        assertEquals(GroupType.BURST_PHOTO, g[0].type)
    }

    @Test fun `exact copies are collapsed to one representative in similarity pass`() {
        val g = engine.groupSimilar(listOf(
            testFile("a", phash = 0L, sha = "H"), testFile("a2", phash = 0L, sha = "H"), testFile("b", phash = 1L, sha = "K", modified = 99_999_999),
        ), 10)
        assertEquals(1, g.size)
        assertEquals(2, g[0].files.size)
    }

    @Test fun `similarity bucketing stays bounded on many identical hashes`() {
        val many = (0 until 3000).map { testFile("p$it", phash = 0L, sha = "S$it") }
        val g = engine.groupSimilar(many, 10)
        assertTrue(g.isNotEmpty())
        assertEquals(3000, g.sumOf { it.files.size })
    }
}
