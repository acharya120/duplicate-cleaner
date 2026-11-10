package com.duplicatecleaner.presentation.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.duplicatecleaner.core.util.formatSize
import com.duplicatecleaner.domain.model.DuplicateFile
import com.duplicatecleaner.domain.model.FileCategory
import com.duplicatecleaner.domain.model.GroupType
import com.duplicatecleaner.presentation.scanning.DeletionUiState

fun GroupType.title() = when (this) {
    GroupType.EXACT_PHOTO -> "Exact duplicate photos"
    GroupType.SIMILAR_PHOTO -> "Similar photos"
    GroupType.BURST_PHOTO -> "Potential burst-photo groups"
    GroupType.EXACT_VIDEO -> "Exact duplicate videos"
    GroupType.EXACT_DOCUMENT -> "Exact duplicate documents"
}

fun GroupType.explanation() = when (this) {
    GroupType.EXACT_PHOTO, GroupType.EXACT_VIDEO, GroupType.EXACT_DOCUMENT -> "Byte-for-byte identical (SHA-256 match)."
    GroupType.SIMILAR_PHOTO -> "Look alike but are NOT identical. Review each one before removing anything."
    GroupType.BURST_PHOTO -> "Similar photos taken within seconds of each other. Often you want to keep several."
}

@Composable
fun FileThumbnail(file: DuplicateFile, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.size(size).clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (file.category == FileCategory.DOCUMENT) {
            Icon(Icons.Default.Description, contentDescription = "Document", modifier = Modifier.size(size / 2))
        } else {
            val ctx = LocalContext.current
            val request = ImageRequest.Builder(ctx).data(android.net.Uri.parse(file.uri)).size(160)
                .apply { if (file.category == FileCategory.VIDEO) videoFrameMillis(1000) }
                .crossfade(true).build()
            AsyncImage(
                model = request, contentDescription = file.name, contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
                error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

/** Confirmation, progress and result dialogs for deletion. Shown above every screen. */
@Composable
fun DeletionDialogs(
    state: DeletionUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDismissReport: () -> Unit,
) {
    when (state) {
        DeletionUiState.None -> Unit
        is DeletionUiState.Confirm -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Remove ${state.plan.files.size} file(s)?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This frees about ${formatSize(state.plan.estimatedBytes)}.")
                    Text("At least one copy in every group is kept. Android will ask you to confirm the deletion next.")
                    state.plan.files.take(5).forEach { Text("• ${it.name}", style = MaterialTheme.typography.bodySmall) }
                    if (state.plan.files.size > 5) Text("…and ${state.plan.files.size - 5} more", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(onClick = onConfirm) { Text("Continue") } },
            dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        )
        DeletionUiState.InProgress -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Removing files…") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(24.dp)); Text("  Waiting for Android and verifying results") } },
        )
        is DeletionUiState.Done -> {
            val r = state.report
            AlertDialog(
                onDismissRequest = onDismissReport,
                title = { Text(if (r.cancelledByUser && r.deleted.isEmpty()) "Nothing was removed" else if (r.isFullSuccess) "Done" else "Finished with issues") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Removed and verified: ${r.deleted.size} (${formatSize(r.recoveredBytes)} recovered)")
                        if (r.alreadyGone.isNotEmpty()) Text("Already gone: ${r.alreadyGone.size}")
                        if (r.failed.isNotEmpty()) Text("Could not be removed: ${r.failed.size}. They are still on your device.")
                        if (r.cancelledByUser) Text("You declined the Android confirmation, so remaining files were left untouched.")
                    }
                },
                confirmButton = { Button(onClick = onDismissReport) { Text("OK") } },
            )
        }
    }
}

@Composable
fun SelectionBar(selectedCount: Int, bytes: Long, onDelete: () -> Unit, onClear: () -> Unit) {
    if (selectedCount == 0) return
    androidx.compose.material3.Surface(tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("$selectedCount selected", style = MaterialTheme.typography.titleMedium)
                Text("≈ ${formatSize(bytes)} to recover", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onClear) { Text("Clear") }
            Button(onClick = onDelete) { Text("Remove") }
        }
    }
}
