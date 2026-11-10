package com.duplicatecleaner.domain.model

enum class FileCategory { PHOTO, VIDEO, DOCUMENT }

enum class FileSource { MEDIASTORE, SAF }

enum class GroupType { EXACT_PHOTO, SIMILAR_PHOTO, BURST_PHOTO, EXACT_VIDEO, EXACT_DOCUMENT }

/**
 * A scanned file. [uri] is the content URI string and is the identity of the file.
 * [physicalKey] identifies the underlying on-disk file (volume:path) so the same file seen through
 * two providers is never reported as a duplicate of itself.
 */
data class DuplicateFile(
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val location: String,
    val category: FileCategory,
    val source: FileSource,
    val physicalKey: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val sha256: String? = null,
    val perceptualHash: Long? = null,
) {
    val id: String get() = uri
}

data class DuplicateGroup(
    val id: String,
    val type: GroupType,
    val files: List<DuplicateFile>,
) {
    /** Bytes freed if every file except the largest were removed. */
    val recoverableBytes: Long
        get() = files.sumOf { it.sizeBytes } - (files.maxOfOrNull { it.sizeBytes } ?: 0L)

    /** Files beyond the one that must be kept. */
    val extraCopies: Int get() = (files.size - 1).coerceAtLeast(0)
}

enum class SimilarityLevel { IDENTICAL, VERY_SIMILAR, POTENTIALLY_SIMILAR, NOT_SIMILAR }

data class ScanSettings(
    val scanPhotos: Boolean = true,
    val scanVideos: Boolean = true,
    val scanDocuments: Boolean = true,
    val findSimilarPhotos: Boolean = true,
    /** Maximum Hamming distance (0..10) for photos to be treated as visually similar. */
    val similarityThreshold: Int = 10,
    val documentFolderUris: Set<String> = emptySet(),
)

data class ScanResults(
    val groups: List<DuplicateGroup> = emptyList(),
    val completedAt: Long? = null,
) {
    fun groupsOf(type: GroupType) = groups.filter { it.type == type }
    fun extraCopies(vararg types: GroupType) = groups.filter { it.type in types }.sumOf { it.extraCopies }
    fun recoverable(vararg types: GroupType) = groups.filter { it.type in types }.sumOf { it.recoverableBytes }
    val totalRecoverableBytes: Long get() = groups.sumOf { it.recoverableBytes }
}

data class DeletionPlan(val files: List<DuplicateFile>, val estimatedBytes: Long)

data class DeletionReport(
    val deleted: List<DuplicateFile>,
    val alreadyGone: List<DuplicateFile>,
    val failed: List<DuplicateFile>,
    val cancelledByUser: Boolean,
) {
    val recoveredBytes: Long get() = deleted.sumOf { it.sizeBytes }
    val isFullSuccess: Boolean get() = failed.isEmpty() && !cancelledByUser
}
