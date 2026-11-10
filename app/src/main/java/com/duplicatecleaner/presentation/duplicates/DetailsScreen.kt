package com.duplicatecleaner.presentation.duplicates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duplicatecleaner.core.util.formatDate
import com.duplicatecleaner.core.util.formatSize
import com.duplicatecleaner.domain.model.DuplicateFile
import com.duplicatecleaner.domain.usecase.SelectionPolicy
import com.duplicatecleaner.presentation.results.FileThumbnail
import com.duplicatecleaner.presentation.results.SelectionBar
import com.duplicatecleaner.presentation.results.explanation
import com.duplicatecleaner.presentation.results.title
import com.duplicatecleaner.presentation.scanning.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    ui: UiState,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onKeep: (groupId: String, fileId: String) -> Unit,
    onSelectOthers: (String) -> Unit,
    onClearGroup: (String) -> Unit,
    onPreview: (DuplicateFile) -> Unit,
    onRequestDelete: () -> Unit,
    onClearSelection: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); onDismissMessage() } }
    val group = ui.results.groups.firstOrNull { it.id == ui.openGroupId }
    LaunchedEffect(group == null) { if (group == null) onBack() }
    if (group == null) return
    val suggested = SelectionPolicy.suggestedKeeper(group).id
    val keeper = ui.keepers[group.id]
    val selectedBytes = ui.results.groups.flatMap { it.files }.distinctBy { it.id }.filter { it.id in ui.selected }.sumOf { it.sizeBytes }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group.type.title()) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = { SelectionBar(ui.selected.size, selectedBytes, onRequestDelete, onClearSelection) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(group.type.explanation(), style = MaterialTheme.typography.bodyMedium)
                    Text("Choose the copy to keep, then select the others. Suggestion (not applied automatically): the file marked “Suggested”.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSelectOthers(group.id) }) { Text("Select all except keeper") }
                        OutlinedButton(onClick = { onClearGroup(group.id) }) { Text("Clear") }
                    }
                }
            }
            items(group.files, key = { it.id }) { f ->
                val isKeeper = (keeper ?: "") == f.id
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        FileThumbnail(f, 72.dp, Modifier.clickable { onPreview(f) })
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(f.name, style = MaterialTheme.typography.titleSmall)
                            Text("${formatSize(f.sizeBytes)} · ${formatDate(f.lastModified)}", style = MaterialTheme.typography.bodySmall)
                            Text(f.location.ifEmpty { "Selected folder" }, style = MaterialTheme.typography.bodySmall)
                            val dims = if (f.width > 0 && f.height > 0) " · ${f.width}×${f.height}" else ""
                            Text("${f.mimeType}$dims", style = MaterialTheme.typography.bodySmall)
                            if (f.id == suggested && keeper == null) Text("Suggested to keep", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text("Tap the preview to open it", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RadioButton(selected = isKeeper, onClick = { onKeep(group.id, f.id) })
                            Text("Keep", style = MaterialTheme.typography.labelSmall)
                            Checkbox(checked = f.id in ui.selected, onCheckedChange = { onToggle(f.id) })
                            Text("Remove", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
