package com.duplicatecleaner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class DuplicateDao {
    @Query("SELECT * FROM scanned_files")
    abstract suspend fun getAll(): List<DuplicateFileEntity>

    @Query("SELECT * FROM scanned_files WHERE memberships != ''")
    abstract suspend fun getGrouped(): List<DuplicateFileEntity>

    @Query("SELECT uri FROM scanned_files")
    abstract suspend fun allUris(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertChunk(list: List<DuplicateFileEntity>)

    @Query("DELETE FROM scanned_files WHERE uri IN (:uris)")
    abstract suspend fun deleteChunk(uris: List<String>)

    @Query("UPDATE scanned_files SET memberships = ''")
    abstract suspend fun clearMemberships()

    @Query("UPDATE scanned_files SET memberships = :memberships WHERE uri = :uri")
    abstract suspend fun setMemberships(uri: String, memberships: String)

    @Query("DELETE FROM scanned_files")
    abstract suspend fun clear()

    /** Writes the fresh scan and drops rows for files that no longer exist, in one transaction. */
    @Transaction
    open suspend fun sync(entities: List<DuplicateFileEntity>, presentUris: Set<String>) {
        entities.chunked(CHUNK).forEach { upsertChunk(it) }
        val stale = allUris().filter { it !in presentUris }
        stale.chunked(CHUNK).forEach { deleteChunk(it) }
    }

    @Transaction
    open suspend fun removeAndRegroup(removed: Set<String>, memberships: Map<String, String>) {
        removed.toList().chunked(CHUNK).forEach { deleteChunk(it) }
        clearMemberships()
        memberships.forEach { (uri, m) -> setMemberships(uri, m) }
    }

    private companion object { const val CHUNK = 500 }
}
