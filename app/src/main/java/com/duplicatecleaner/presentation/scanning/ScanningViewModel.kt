package com.duplicatecleaner.presentation.scanning

import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duplicatecleaner.core.permissions.SafPermissionManager
import com.duplicatecleaner.domain.model.DeletionReport
import com.duplicatecleaner.domain.model.DuplicateFile
import com.duplicatecleaner.domain.model.FileSource
import com.duplicatecleaner.domain.model.GroupType
import com.duplicatecleaner.domain.model.MediaAccess
import com.duplicatecleaner.domain.model.ScanEvent
import com.duplicatecleaner.domain.model.ScanSettings
import com.duplicatecleaner.domain.model.ScanState
import com.duplicatecleaner.domain.repository.DeletionRepository
import com.duplicatecleaner.domain.repository.DuplicateRepository
import com.duplicatecleaner.domain.repository.LegacyDeleteOutcome
import com.duplicatecleaner.domain.repository.PermissionRepository
import com.duplicatecleaner.domain.repository.ScanRepository
import com.duplicatecleaner.domain.repository.SettingsRepository
import com.duplicatecleaner.domain.usecase.SelectionPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ScanningViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val duplicateRepository: DuplicateRepository,
    private val permissionRepository: PermissionRepository,
    private val deletionRepository: DeletionRepository,
    private val settingsRepository: SettingsRepository,
    private val safPermissions: SafPermissionManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var scanJob: Job? = null
    private var scanAfterPermission = false
    private var pending: PendingDeletion? = null

    init {
        viewModelScope.launch { duplicateRepository.restore() }
        viewModelScope.launch {
            duplicateRepository.results.collect { r ->
                _ui.update { s ->
                    val live = r.groups.flatMap { it.files }.map { it.id }.toSet()
                    s.copy(results = r, selected = s.selected.filter { it in live }.toSet(),
                        keepers = s.keepers.filterValues { it in live })
                }
            }
        }
        viewModelScope.launch { settingsRepository.settings.collect { st -> _ui.update { it.copy(settings = st) } } }
        viewModelScope.launch { permissionRepository.state.collect { p -> _ui.update { it.copy(permission = p) } } }
    }

    // ---------------- permissions ----------------

    fun onResume() {
        permissionRepository.refresh()
        val s = _ui.value
        val needsMedia = s.settings.scanPhotos || s.settings.scanVideos
        if (s.scanState.isRunning && needsMedia && permissionRepository.state.value.access == MediaAccess.DENIED &&
            !(s.settings.scanDocuments && s.settings.documentFolderUris.isNotEmpty())
        ) {
            cancelScan("Media access was removed, so the scan was stopped.")
        }
    }

    fun requestMediaAccess() {
        val p = permissionRepository.state.value
        if (p.access == MediaAccess.DENIED && p.permanentlyDenied) _events.trySend(UiEvent.OpenAppSettings)
        else _events.trySend(UiEvent.RequestMediaPermissions(permissionRepository.requiredPermissions()))
    }

    fun openAppSettings() { _events.trySend(UiEvent.OpenAppSettings) }

    fun onMediaPermissionResult(rationaleAvailable: Boolean) {
        permissionRepository.onPermissionResult(rationaleAvailable)
        val access = permissionRepository.state.value.access
        if (scanAfterPermission) {
            scanAfterPermission = false
            val s = _ui.value.settings
            val hasDocs = s.scanDocuments && s.documentFolderUris.isNotEmpty()
            when {
                access != MediaAccess.DENIED -> startScan(s)
                hasDocs -> startScan(s.copy(scanPhotos = false, scanVideos = false))
                else -> _ui.update { it.copy(scanState = ScanState.Idle, message = "Photos and videos can't be scanned without media access.") }
            }
        }
    }

    // ---------------- scanning ----------------

    fun onScanClicked() {
        val cur = _ui.value
        if (cur.scanState.isRunning) return
        permissionRepository.refresh()
        val st = cur.settings
        // Drop folders whose grant was revoked/cleared by the system.
        val validFolders = st.documentFolderUris.filter { safPermissions.isStillGranted(Uri.parse(it)) }.toSet()
        val staleCount = st.documentFolderUris.size - validFolders.size
        val effective = st.copy(documentFolderUris = validFolders)
        val needsMedia = effective.scanPhotos || effective.scanVideos
        val hasDocs = effective.scanDocuments && validFolders.isNotEmpty()
        if (!needsMedia && !hasDocs) {
            _ui.update { it.copy(message = "Turn on photos or videos, or add a document folder in Settings first.") }
            return
        }
        if (staleCount > 0) _ui.update { it.copy(message = "$staleCount selected folder(s) are no longer accessible and were skipped.") }
        val access = permissionRepository.state.value
        if (needsMedia && access.access == MediaAccess.DENIED) {
            if (hasDocs) {
                startScan(effective.copy(scanPhotos = false, scanVideos = false))
            } else {
                scanAfterPermission = true
                _ui.update { it.copy(scanState = ScanState.RequestingPermission) }
                requestMediaAccess()
            }
            return
        }
        startScan(effective)
    }

    private fun startScan(settings: ScanSettings) {
        scanJob?.cancel()
        _ui.update { it.copy(scanState = ScanState.Preparing, progress = com.duplicatecleaner.domain.model.ScanProgress(), skippedFiles = 0, selected = emptySet(), keepers = emptyMap(), deletion = DeletionUiState.None) }
        scanJob = viewModelScope.launch {
            try {
                scanRepository.scan(settings).collect { e ->
                    when (e) {
                        is ScanEvent.StateChanged -> _ui.update { it.copy(scanState = e.state, progress = e.progress) }
                        is ScanEvent.Progress -> _ui.update { it.copy(progress = e.progress) }
                        is ScanEvent.Finished -> _ui.update {
                            val r = e.results
                            it.copy(
                                scanState = ScanState.Completed, skippedFiles = e.skippedFiles,
                                progress = it.progress.copy(
                                    percent = 100, groupsFound = r.groups.size,
                                    potentialDuplicates = r.groups.sumOf { g -> g.extraCopies },
                                    recoverableBytes = r.totalRecoverableBytes,
                                ),
                            )
                        }
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: OutOfMemoryError) {
                _ui.update { it.copy(scanState = ScanState.Error("The device ran low on memory. Close other apps and try again.")) }
            } catch (e: Exception) {
                _ui.update { it.copy(scanState = ScanState.Error("The scan could not finish (${e.javaClass.simpleName}). Please try again.")) }
            }
        }
    }

    fun cancelScan(reason: String? = null) {
        scanJob?.cancel()
        scanJob = null
        scanAfterPermission = false
        _ui.update { it.copy(scanState = ScanState.Cancelled, message = reason) }
    }

    /** Called once the UI has navigated away from a terminal scan state. */
    fun acknowledgeScanEnd() { _ui.update { if (it.scanState.isRunning) it else it.copy(scanState = ScanState.Idle) } }

    // ---------------- settings ----------------

    fun updateSettings(transform: (ScanSettings) -> ScanSettings) { viewModelScope.launch { settingsRepository.update(transform) } }

    fun addFolder(uri: Uri) {
        if (!safPermissions.persist(uri)) {
            _ui.update { it.copy(message = "Android did not allow access to that folder. Choose a different folder.") }
            return
        }
        updateSettings { it.copy(documentFolderUris = it.documentFolderUris + uri.toString()) }
    }

    fun removeFolder(uri: String) {
        safPermissions.release(Uri.parse(uri))
        updateSettings { it.copy(documentFolderUris = it.documentFolderUris - uri) }
    }

    fun clearScanData() {
        viewModelScope.launch {
            duplicateRepository.clearScanData()
            _ui.update { it.copy(selected = emptySet(), keepers = emptyMap(), message = "Temporary scan data cleared.") }
        }
    }

    fun dismissMessage() { _ui.update { it.copy(message = null) } }

    // ---------------- selection ----------------

    fun openGroup(id: String) { _ui.update { it.copy(openGroupId = id) } }

    fun toggleFile(fileId: String) {
        val s = _ui.value
        if (fileId in s.selected) {
            _ui.update { it.copy(selected = it.selected - fileId) }
        } else if (s.keepers.values.contains(fileId)) {
            _ui.update { it.copy(message = "This is the copy you chose to keep. Choose a different original first.") }
        } else if (SelectionPolicy.canSelect(s.results.groups, s.selected, fileId)) {
            _ui.update { it.copy(selected = it.selected + fileId) }
        } else {
            _ui.update { it.copy(message = "At least one copy in every group must be kept.") }
        }
    }

    fun setKeeper(groupId: String, fileId: String) {
        _ui.update { it.copy(keepers = it.keepers + (groupId to fileId), selected = it.selected - fileId) }
    }

    fun selectAllExceptKeeper(groupId: String) {
        val s = _ui.value
        val group = s.results.groups.firstOrNull { it.id == groupId } ?: return
        val keeper = s.keepers[groupId] ?: SelectionPolicy.suggestedKeeper(group).id
        val candidate = s.selected + SelectionPolicy.selectAllExcept(group, keeper)
        if (!SelectionPolicy.isSafe(s.results.groups, candidate)) {
            _ui.update { it.copy(message = "That would remove the last copy of another group, so it was not applied.") }
            return
        }
        _ui.update { it.copy(selected = candidate, keepers = it.keepers + (groupId to keeper)) }
    }

    fun clearGroupSelection(groupId: String) {
        val group = _ui.value.results.groups.firstOrNull { it.id == groupId } ?: return
        _ui.update { it.copy(selected = it.selected - group.files.map { f -> f.id }.toSet()) }
    }

    fun clearSelection() { _ui.update { it.copy(selected = emptySet()) } }

    // ---------------- deletion ----------------

    private class PendingDeletion(
        val deleted: MutableList<DuplicateFile> = mutableListOf(),
        val gone: MutableList<DuplicateFile> = mutableListOf(),
        val failed: MutableList<DuplicateFile> = mutableListOf(),
        var mediaBatch: List<DuplicateFile> = emptyList(),
        val legacyQueue: ArrayDeque<DuplicateFile> = ArrayDeque(),
        var legacyCurrent: DuplicateFile? = null,
        var cancelledByUser: Boolean = false,
    )

    fun requestDelete() {
        val s = _ui.value
        val plan = SelectionPolicy.buildPlan(s.results.groups, s.selected)
        if (plan == null) {
            _ui.update { it.copy(message = if (s.selected.isEmpty()) "Select at least one file to remove." else "At least one copy in every group must be kept.") }
            return
        }
        _ui.update { it.copy(deletion = DeletionUiState.Confirm(plan)) }
    }

    fun cancelDeleteDialog() { _ui.update { it.copy(deletion = DeletionUiState.None) } }
    fun dismissReport() { _ui.update { it.copy(deletion = DeletionUiState.None) } }

    fun confirmDelete() {
        val s = _ui.value
        val confirm = s.deletion as? DeletionUiState.Confirm ?: return
        // Re-validate at the last moment: selection may have changed since the dialog was built.
        val plan = SelectionPolicy.buildPlan(s.results.groups, s.selected)
        if (plan == null || plan.files.map { it.id }.toSet() != confirm.plan.files.map { it.id }.toSet()) {
            _ui.update { it.copy(deletion = DeletionUiState.None, message = "The selection changed. Please review it again.") }
            return
        }
        _ui.update { it.copy(deletion = DeletionUiState.InProgress) }
        viewModelScope.launch {
            val p = PendingDeletion()
            pending = p
            val media = mutableListOf<DuplicateFile>()
            for (f in plan.files) {
                if (!deletionRepository.isPresent(f)) { p.gone += f; continue }
                if (f.source == FileSource.SAF) {
                    val ok = deletionRepository.deleteDocument(f)
                    if (ok && !deletionRepository.isPresent(f)) p.deleted += f else p.failed += f
                } else media += f
            }
            if (media.isEmpty()) { finishDeletion(); return@launch }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                p.mediaBatch = media
                try {
                    _events.send(UiEvent.LaunchDeleteIntent(deletionRepository.buildMediaDeleteRequest(media)))
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    p.failed += media
                    p.mediaBatch = emptyList()
                    finishDeletion()
                }
            } else {
                p.legacyQueue.addAll(media)
                processLegacyQueue()
            }
        }
    }

    /** Result of the system delete dialog (Android 10 consent or Android 11+ batch request). */
    fun onDeleteIntentResult(accepted: Boolean) {
        val p = pending ?: return
        viewModelScope.launch {
            val current = p.legacyCurrent
            if (current != null) {
                p.legacyCurrent = null
                if (accepted) {
                    when (deletionRepository.tryDeleteLegacyMedia(current)) {
                        LegacyDeleteOutcome.Deleted -> verifyInto(p, current)
                        LegacyDeleteOutcome.AlreadyGone -> p.gone += current
                        else -> p.failed += current
                    }
                    processLegacyQueue()
                } else {
                    p.cancelledByUser = true
                    p.legacyQueue.clear() // user declined: stop, leave remaining files untouched
                    finishDeletion()
                }
            } else {
                p.mediaBatch.forEach { verifyInto(p, it) }
                if (!accepted && p.deleted.isEmpty()) p.cancelledByUser = true
                p.mediaBatch = emptyList()
                finishDeletion()
            }
        }
    }

    private suspend fun processLegacyQueue() {
        val p = pending ?: return
        while (p.legacyQueue.isNotEmpty()) {
            val f = p.legacyQueue.removeFirst()
            when (val outcome = deletionRepository.tryDeleteLegacyMedia(f)) {
                LegacyDeleteOutcome.Deleted -> verifyInto(p, f)
                LegacyDeleteOutcome.AlreadyGone -> p.gone += f
                LegacyDeleteOutcome.Failed -> p.failed += f
                is LegacyDeleteOutcome.NeedsConsent -> {
                    p.legacyCurrent = f
                    _events.send(UiEvent.LaunchDeleteIntent(outcome.intentSender))
                    return
                }
            }
        }
        finishDeletion()
    }

    /** A file only counts as deleted once the provider confirms it is gone. */
    private suspend fun verifyInto(p: PendingDeletion, f: DuplicateFile) {
        if (deletionRepository.isPresent(f)) p.failed += f else p.deleted += f
    }

    private suspend fun finishDeletion() {
        val p = pending ?: return
        pending = null
        duplicateRepository.removeFiles((p.deleted + p.gone).map { it.id }.toSet())
        _ui.update {
            it.copy(deletion = DeletionUiState.Done(DeletionReport(p.deleted.toList(), p.gone.toList(), p.failed.toList(), p.cancelledByUser)))
        }
    }
}
