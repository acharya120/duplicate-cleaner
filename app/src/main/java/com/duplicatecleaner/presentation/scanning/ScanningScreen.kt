package com.duplicatecleaner.presentation.scanning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duplicatecleaner.core.util.formatSize
import com.duplicatecleaner.domain.model.ScanState

@Composable
fun ScanningScreen(ui: UiState, onCancel: () -> Unit) {
    val p = ui.progress
    val label = when (ui.scanState) {
        ScanState.RequestingPermission -> "Waiting for permission"
        ScanState.Preparing -> "Preparing"
        ScanState.ScanningPhotos -> "Scanning photos"
        ScanState.ScanningVideos -> "Scanning videos"
        ScanState.ScanningDocuments -> "Scanning documents"
        ScanState.CalculatingHashes -> "Comparing file contents"
        ScanState.GroupingDuplicates -> "Grouping duplicates"
        else -> "Working"
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(Modifier.size(72.dp)) // indeterminate: animates while work runs
        Text(label, style = MaterialTheme.typography.headlineSmall)
        if (p.operation.isNotEmpty()) Text(p.operation, style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(progress = { p.percent / 100f }, modifier = Modifier.fillMaxWidth())
        Text("${p.percent}%", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Line("Files found", "${p.filesDiscovered}")
            Line("Files compared", "${p.filesProcessed}")
            Line("Duplicate groups so far", "${p.groupsFound}")
            Line("Estimated recovery", formatSize(p.recoverableBytes))
        }
        Button(onClick = onCancel) { Text("Cancel scan") }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value, style = MaterialTheme.typography.titleSmall) }
}
