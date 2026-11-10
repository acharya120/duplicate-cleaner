package com.duplicatecleaner.presentation

import android.content.IntentSender
import com.duplicatecleaner.domain.model.DuplicateFile
import com.duplicatecleaner.domain.model.DuplicateGroup
import com.duplicatecleaner.domain.model.PermissionState
import com.duplicatecleaner.domain.model.ScanEvent
import com.duplicatecleaner.domain.model.ScanResults
import com.duplicatecleaner.domain.model.ScanSettings
import com.duplicatecleaner.domain.repository.DeletionRepository
import com.duplicatecleaner.domain.repository.DuplicateRepository
import com.duplicatecleaner.domain.repository.LegacyDeleteOutcome
import com.duplicatecleaner.domain.repository.PermissionRepository
import com.duplicatecleaner.domain.repository.ScanRepository
import com.duplicatecleaner.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeDuplicateRepository(initial: List<DuplicateGroup> = emptyList()) : DuplicateRepository {
    private val _r = MutableStateFlow(ScanResults(initial, 1L))
    override val results: StateFlow<ScanResults> = _r.asStateFlow()
    var removed: Set<String> = emptySet()
    override suspend fun restore() {}
    override suspend fun publish(results: ScanResults) { _r.value = results }
    override suspend fun removeFiles(ids: Set<String>) {
        removed = removed + ids
        _r.update { r -> r.copy(groups = r.groups.mapNotNull { g -> g.files.filter { it.id !in ids }.let { k -> if (k.size >= 2) g.copy(files = k) else null } }) }
    }
    override suspend fun clearScanData() { _r.value = ScanResults() }
}

class FakeSettingsRepository(initial: ScanSettings = ScanSettings()) : SettingsRepository {
    private val s = MutableStateFlow(initial)
    private val last = MutableStateFlow<Long?>(null)
    override val settings: Flow<ScanSettings> = s
    override val lastScanAt: Flow<Long?> = last
    override suspend fun update(transform: (ScanSettings) -> ScanSettings) { s.value = transform(s.value) }
    override suspend fun setLastScanAt(timestamp: Long) { last.value = timestamp }
}

class FakePermissionRepository(initial: PermissionState) : PermissionRepository {
    private val s = MutableStateFlow(initial)
    override val state: StateFlow<PermissionState> = s.asStateFlow()
    var nextStateOnResult: PermissionState? = null
    override fun requiredPermissions(): Array<String> = arrayOf("perm")
    override fun refresh() {}
    override fun onPermissionResult(rationaleAvailable: Boolean) { nextStateOnResult?.let { s.value = it } }
}

class FakeScanRepository(private val flowFactory: (ScanSettings) -> Flow<ScanEvent>) : ScanRepository {
    var lastSettings: ScanSettings? = null
    override fun scan(settings: ScanSettings): Flow<ScanEvent> { lastSettings = settings; return flowFactory(settings) }
}

class FakeDeletionRepository(private val consentSender: IntentSender? = null) : DeletionRepository {
    val present = mutableSetOf<String>()
    /** Scripted outcomes per file, consumed in order; default is Deleted. */
    val script = mutableMapOf<String, ArrayDeque<LegacyDeleteOutcome>>()
    var silentlyKeepsFile = setOf<String>()
    override fun buildMediaDeleteRequest(files: List<DuplicateFile>): IntentSender = throw UnsupportedOperationException()
    override fun tryDeleteLegacyMedia(file: DuplicateFile): LegacyDeleteOutcome {
        val o = script[file.uri]?.removeFirstOrNull() ?: LegacyDeleteOutcome.Deleted
        if (o is LegacyDeleteOutcome.Deleted && file.uri !in silentlyKeepsFile) present.remove(file.uri)
        return o
    }
    override suspend fun deleteDocument(file: DuplicateFile): Boolean { present.remove(file.uri); return true }
    override suspend fun isPresent(file: DuplicateFile) = file.uri in present
}
