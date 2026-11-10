package com.duplicatecleaner.data.repository

import com.duplicatecleaner.data.local.DuplicateDao
import com.duplicatecleaner.data.local.membershipsOf
import com.duplicatecleaner.data.local.rebuildGroups
import com.duplicatecleaner.domain.model.DuplicateGroup
import com.duplicatecleaner.domain.model.ScanResults
import com.duplicatecleaner.domain.repository.DuplicateRepository
import com.duplicatecleaner.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

@Singleton
class DuplicateRepositoryImpl @Inject constructor(
    private val dao: DuplicateDao,
    private val settings: SettingsRepository,
) : DuplicateRepository {
    private val _results = MutableStateFlow(ScanResults())
    override val results: StateFlow<ScanResults> = _results.asStateFlow()

    override suspend fun restore() {
        if (_results.value.groups.isNotEmpty()) return
        try {
            val groups = rebuildGroups(dao.getGrouped())
            _results.value = ScanResults(groups, settings.lastScanAt.first())
        } catch (e: Exception) {
            // A corrupt/unavailable cache must never block the app; the user can simply rescan.
            _results.value = ScanResults()
        }
    }

    override suspend fun publish(results: ScanResults) { _results.value = results }

    override suspend fun removeFiles(ids: Set<String>) {
        if (ids.isEmpty()) return
        val remaining: List<DuplicateGroup> = _results.value.groups.mapNotNull { g ->
            val kept = g.files.filter { it.id !in ids }
            if (kept.size >= 2) g.copy(files = kept) else null
        }
        _results.value = _results.value.copy(groups = remaining)
        try {
            dao.removeAndRegroup(ids, membershipsOf(remaining))
        } catch (e: Exception) {
            // In-memory results are already correct; the cache self-heals on the next scan.
        }
    }

    override suspend fun clearScanData() {
        _results.value = ScanResults()
        try { dao.clear() } catch (e: Exception) { /* ignore */ }
    }
}
