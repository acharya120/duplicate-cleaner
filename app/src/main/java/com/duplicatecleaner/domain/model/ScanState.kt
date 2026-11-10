package com.duplicatecleaner.domain.model

sealed interface ScanState {
    data object Idle : ScanState
    data object RequestingPermission : ScanState
    data object Preparing : ScanState
    data object ScanningPhotos : ScanState
    data object ScanningVideos : ScanState
    data object ScanningDocuments : ScanState
    data object CalculatingHashes : ScanState
    data object GroupingDuplicates : ScanState
    data object Completed : ScanState
    data object Cancelled : ScanState
    data class Error(val message: String) : ScanState

    val isRunning: Boolean
        get() = this is Preparing || this is ScanningPhotos || this is ScanningVideos ||
            this is ScanningDocuments || this is CalculatingHashes || this is GroupingDuplicates
}

data class ScanProgress(
    val category: FileCategory? = null,
    val operation: String = "",
    val filesDiscovered: Int = 0,
    val filesProcessed: Int = 0,
    val groupsFound: Int = 0,
    val potentialDuplicates: Int = 0,
    val recoverableBytes: Long = 0,
    val percent: Int = 0,
)

/** Events emitted by a running scan. */
sealed interface ScanEvent {
    data class StateChanged(val state: ScanState, val progress: ScanProgress) : ScanEvent
    data class Progress(val progress: ScanProgress) : ScanEvent
    data class Finished(val results: ScanResults, val skippedFiles: Int) : ScanEvent
}
