package com.duplicatecleaner.presentation.scanning

import android.content.IntentSender
import com.duplicatecleaner.domain.model.DeletionPlan
import com.duplicatecleaner.domain.model.DeletionReport
import com.duplicatecleaner.domain.model.PermissionState
import com.duplicatecleaner.domain.model.ScanProgress
import com.duplicatecleaner.domain.model.ScanResults
import com.duplicatecleaner.domain.model.ScanSettings
import com.duplicatecleaner.domain.model.ScanState

sealed interface DeletionUiState {
    data object None : DeletionUiState
    data class Confirm(val plan: DeletionPlan) : DeletionUiState
    data object InProgress : DeletionUiState
    data class Done(val report: DeletionReport) : DeletionUiState
}

data class UiState(
    val scanState: ScanState = ScanState.Idle,
    val progress: ScanProgress = ScanProgress(),
    val results: ScanResults = ScanResults(),
    val permission: PermissionState = PermissionState(),
    val settings: ScanSettings = ScanSettings(),
    val selected: Set<String> = emptySet(),
    /** groupId -> file the user chose to keep. */
    val keepers: Map<String, String> = emptyMap(),
    val openGroupId: String? = null,
    val deletion: DeletionUiState = DeletionUiState.None,
    val skippedFiles: Int = 0,
    val message: String? = null,
)

sealed interface UiEvent {
    data class RequestMediaPermissions(val permissions: Array<String>) : UiEvent
    data class LaunchDeleteIntent(val intentSender: IntentSender) : UiEvent
    data object OpenAppSettings : UiEvent
}
