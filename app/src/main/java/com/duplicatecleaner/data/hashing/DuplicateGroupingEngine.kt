package com.duplicatecleaner.data.hashing

import com.duplicatecleaner.domain.model.DuplicateFile
import com.duplicatecleaner.domain.model.DuplicateGroup
import com.duplicatecleaner.domain.model.FileCategory
import com.duplicatecleaner.domain.model.GroupType
import javax.inject.Inject
import kotlin.math.abs

/** Pure grouping logic. Exact groups require identical SHA-256 AND size; names are never used. */
class DuplicateGroupingEngine @Inject constructor() {

    /** Removes zero-byte files and entries pointing at the same physical file. */
    fun eligible(files: List<DuplicateFile>): List<DuplicateFile> =
        files.filter { it.sizeBytes > 0 }
            .distinctBy { it.uri }
            .distinctBy { it.physicalKey ?: it.uri }

    /** Only files sharing a size with at least one other file of the same category need hashing. */
    fun sizeCandidates(files: List<DuplicateFile>): List<DuplicateFile> =
        eligible(files).groupBy { it.category to it.sizeBytes }.values.filter { it.size > 1 }.flatten()

    fun groupExact(hashed: List<DuplicateFile>): List<DuplicateGroup> =
        eligible(hashed).filter { it.sha256 != null }
            .groupBy { Triple(it.category, it.sizeBytes, it.sha256) }
            .values.filter { it.size >= 2 }
            .map { members ->
                DuplicateGroup(
                    id = "exact:${members.first().sha256}:${members.first().sizeBytes}",
                    type = when (members.first().category) {
                        FileCategory.PHOTO -> GroupType.EXACT_PHOTO
                        FileCategory.VIDEO -> GroupType.EXACT_VIDEO
                        FileCategory.DOCUMENT -> GroupType.EXACT_DOCUMENT
                    },
                    files = members.sortedBy { it.lastModified },
                )
            }
            .sortedByDescending { it.recoverableBytes }

    /**
     * Visually similar photos. Exact copies are collapsed to one representative first (they are shown in the
     * exact-duplicate section). Candidate pairs come from multi-index bucketing over the 64-bit hash: with
     * threshold t the hash is split into t+1 bands, so any pair within distance t shares at least one identical
     * band (pigeonhole). Oversized buckets are compared only inside a bounded sliding window, trading a little
     * recall for a bounded workload on very large galleries.
     */
    fun groupSimilar(
        photos: List<DuplicateFile>,
        maxDistance: Int,
        maxBucketSize: Int = DEFAULT_MAX_BUCKET,
        burstWindowMillis: Long = BURST_WINDOW_MS,
    ): List<DuplicateGroup> {
        val reps = eligible(photos)
            .filter { it.category == FileCategory.PHOTO && it.perceptualHash != null }
            .let { list ->
                val seen = HashSet<String>()
                list.filter { f -> f.sha256 == null || seen.add("${f.sha256}:${f.sizeBytes}") }
            }
        if (reps.size < 2) return emptyList()

        val bands = (maxDistance + 1).coerceIn(1, MAX_BANDS)
        val uf = UnionFind(reps.size)
        for (band in 0 until bands) {
            val start = band * 64 / bands
            val width = (band + 1) * 64 / bands - start
            val mask = if (width >= 64) -1L else (1L shl width) - 1
            val buckets = HashMap<Long, MutableList<Int>>()
            for (i in reps.indices) {
                val key = (reps[i].perceptualHash!! ushr start) and mask
                buckets.getOrPut(key) { ArrayList() }.add(i)
            }
            for (bucket in buckets.values) {
                if (bucket.size < 2) continue
                if (bucket.size <= maxBucketSize) {
                    for (a in 0 until bucket.size) for (b in a + 1 until bucket.size) {
                        tryUnion(reps, bucket[a], bucket[b], maxDistance, uf)
                    }
                } else {
                    val sorted = bucket.sortedBy { reps[it].perceptualHash!! }
                    for (a in sorted.indices) {
                        for (b in a + 1 until minOf(sorted.size, a + 1 + WINDOW)) {
                            tryUnion(reps, sorted[a], sorted[b], maxDistance, uf)
                        }
                    }
                }
            }
        }

        return reps.indices.groupBy { uf.find(it) }.values
            .filter { it.size >= 2 }
            .map { idx ->
                val members = idx.map { reps[it] }.sortedBy { it.lastModified }
                val spread = members.last().lastModified - members.first().lastModified
                val firstId = members.map { it.uri }.minOrNull()!!
                DuplicateGroup(
                    id = "sim:$firstId",
                    type = if (spread <= burstWindowMillis) GroupType.BURST_PHOTO else GroupType.SIMILAR_PHOTO,
                    files = members,
                )
            }
            .sortedByDescending { it.files.size }
    }

    private fun tryUnion(reps: List<DuplicateFile>, a: Int, b: Int, maxDistance: Int, uf: UnionFind) {
        if (uf.find(a) == uf.find(b)) return
        val fa = reps[a]
        val fb = reps[b]
        if (DHash.hamming(fa.perceptualHash!!, fb.perceptualHash!!) > maxDistance) return
        if (!aspectCompatible(fa, fb)) return
        uf.union(a, b)
    }

    private fun aspectCompatible(a: DuplicateFile, b: DuplicateFile): Boolean {
        if (a.width <= 0 || a.height <= 0 || b.width <= 0 || b.height <= 0) return true
        val ra = a.width.toDouble() / a.height
        val rb = b.width.toDouble() / b.height
        return abs(ra - rb) / maxOf(ra, rb) <= ASPECT_TOLERANCE
    }

    private class UnionFind(n: Int) {
        private val parent = IntArray(n) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) {
                val next = parent[c]
                parent[c] = r
                c = next
            }
            return r
        }
        fun union(a: Int, b: Int) {
            parent[find(a)] = find(b)
        }
    }

    companion object {
        const val DEFAULT_MAX_BUCKET = 200
        const val WINDOW = 50
        const val MAX_BANDS = 16
        const val BURST_WINDOW_MS = 10_000L
        const val ASPECT_TOLERANCE = 0.15
    }
}
