package com.duplicatecleaner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Metadata only - file contents are never copied. [memberships] encodes the groups the file belongs to
 * ("groupId<US>type" records separated by <RS>) because a photo can be in an exact AND a similar group.
 */
@Entity(tableName = "scanned_files")
data class DuplicateFileEntity(
    @PrimaryKey val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val location: String,
    val category: String,
    val source: String,
    val physicalKey: String?,
    val width: Int,
    val height: Int,
    val sha256: String?,
    val perceptualHash: Long?,
    val memberships: String,
)
