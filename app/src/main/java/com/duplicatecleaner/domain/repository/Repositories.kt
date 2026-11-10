package com.duplicatecleaner.domain.repository

import android.content.IntentSender
import com.duplicatecleaner.domain.model.DuplicateFile
import com.duplicatecleaner.domain.model.PermissionState
import com.duplicatecleaner.domain.model.ScanEvent
import com.duplicatecleaner.domain.model.ScanResults
import com.duplicatecleaner.domain.model.ScanSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ScanRepository {
    /** Cold flow; collecting starts the scan, cancelling the collector cancels it. */
    fun scan(settings: ScanSettings): Flow<ScanEvent>
}

interface DuplicateRepository {
    val results: StateFlow<ScanResults>
    suspend fun restore()
    suspend fun publish(results: ScanResults)
    suspend fun removeFiles(ids: Set<String>)
    suspend fun clearScanData()
}

interface PermissionRepository {
    val state: StateFlow<PermissionState>
    fun requiredPermissions(): Array<String>
    fun refresh()
    /** [rationaleAvailable] = shouldShowRequestPermissionRationale for the requested permissions. */
    fun onPermissionResult(rationaleAvailable: Boolean)
}

sealed interface LegacyDeleteOutcome {
    data object Deleted : LegacyDeleteOutcome
    data object AlreadyGone : LegacyDeleteOutcome
    data object Failed : LegacyDeleteOutcome
    data class NeedsConsent(val intentSender: IntentSender) : LegacyDeleteOutcome
}

interface DeletionRepository {
    /** Android 11+: one system dialog for the whole batch. */
    fun buildMediaDeleteRequest(files: List<DuplicateFile>): IntentSender
    /** Android 10: per-file delete that may require user consent via a RecoverableSecurityException. */
    fun tryDeleteLegacyMedia(file: DuplicateFile): LegacyDeleteOutcome
    suspend fun deleteDocument(file: DuplicateFile): Boolean
    /** Re-queries the provider; false only when the file is verifiably gone. */
    suspend fun isPresent(file: DuplicateFile): Boolean
}

interface SettingsRepository {
    val settings: Flow<ScanSettings>
    val lastScanAt: Flow<Long?>
    suspend fun update(transform: (ScanSettings) -> ScanSettings)
    suspend fun setLastScanAt(timestamp: Long)
}
