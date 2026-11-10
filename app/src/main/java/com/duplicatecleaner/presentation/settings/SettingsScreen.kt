package com.duplicatecleaner.presentation.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duplicatecleaner.domain.model.MediaAccess
import com.duplicatecleaner.domain.model.ScanSettings
import com.duplicatecleaner.presentation.scanning.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    ui: UiState,
    onBack: () -> Unit,
    onUpdate: ((ScanSettings) -> ScanSettings) -> Unit,
    onAddFolder: (Uri) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onGrantAccess: () -> Unit,
    onClearData: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) onAddFolder(uri) }
    val s = ui.settings
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Media access", style = MaterialTheme.typography.titleMedium)
            Text(
                when (ui.permission.access) {
                    MediaAccess.FULL -> "Full access"
                    MediaAccess.PARTIAL -> "Limited to the photos and videos you picked"
                    MediaAccess.DENIED -> "Not granted"
                },
            )
            if (ui.permission.access != MediaAccess.FULL) {
                OutlinedButton(onClick = onGrantAccess) { Text(if (ui.permission.access == MediaAccess.PARTIAL) "Choose more photos" else if (ui.permission.permanentlyDenied) "Open app settings" else "Allow access") }
            }
            HorizontalDivider()
            Text("What to scan", style = MaterialTheme.typography.titleMedium)
            ToggleRow("Photos", s.scanPhotos) { v -> onUpdate { it.copy(scanPhotos = v) } }
            ToggleRow("Videos", s.scanVideos) { v -> onUpdate { it.copy(scanVideos = v) } }
            ToggleRow("Documents in chosen folders", s.scanDocuments) { v -> onUpdate { it.copy(scanDocuments = v) } }
            ToggleRow("Find visually similar photos", s.findSimilarPhotos) { v -> onUpdate { it.copy(findSimilarPhotos = v) } }
            if (s.findSimilarPhotos) {
                Text("Similarity strictness: ${s.similarityThreshold} (lower = stricter, fewer matches)", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = s.similarityThreshold.toFloat(), onValueChange = { v -> onUpdate { it.copy(similarityThreshold = v.toInt().coerceIn(0, 10)) } },
                    valueRange = 0f..10f, steps = 9,
                )
            }
            HorizontalDivider()
            Text("Document folders", style = MaterialTheme.typography.titleMedium)
            Text("Android only lets this app read folders you choose. Protected system folders can't be scanned.", style = MaterialTheme.typography.bodySmall)
            if (s.documentFolderUris.isEmpty()) Text("No folders chosen yet.")
            s.documentFolderUris.forEach { uri ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null)
                    Text(
                        Uri.decode(uri.substringAfterLast("/tree/")).substringAfter(':').ifEmpty { "Storage root" },
                        Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 2,
                    )
                    IconButton(onClick = { onRemoveFolder(uri) }) { Icon(Icons.Default.Delete, "Remove folder") }
                }
            }
            OutlinedButton(onClick = { picker.launch(null) }) { Text("Add folder") }
            HorizontalDivider()
            Text("Temporary data", style = MaterialTheme.typography.titleMedium)
            Text("Clears the saved scan results and hash cache. Your files are not touched.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onClearData) { Text("Clear scan data") }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label); Switch(checked = checked, onCheckedChange = onChange)
    }
}
