package com.duplicatecleaner.presentation.results

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duplicatecleaner.core.util.formatDate
import com.duplicatecleaner.core.util.formatSize
import com.duplicatecleaner.domain.model.DuplicateGroup
import com.duplicatecleaner.domain.model.GroupType
import com.duplicatecleaner.presentation.scanning.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    ui: UiState,
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onRequestDelete: () -> Unit,
    onClearSelection: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); onDismissMessage() } }
    val expanded = remember { mutableStateMapOf<GroupType, Boolean>() }
    val order = listOf(GroupType.EXACT_PHOTO, GroupType.SIMILAR_PHOTO, GroupType.BURST_PHOTO, GroupType.EXACT_VIDEO, GroupType.EXACT_DOCUMENT)
    val selectedBytes = ui.results.groups.flatMap { it.files }.distinctBy { it.id }.filter { it.id in ui.selected }.sumOf { it.sizeBytes }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Results") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = { SelectionBar(ui.selected.size, selectedBytes, onRequestDelete, onClearSelection) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        if (ui.results.groups.isEmpty()) {
            Column(Modifier.padding(pad).fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No duplicates found", style = MaterialTheme.typography.titleLarge)
                Text("Your scanned photos, videos and documents have no duplicate copies.", style = MaterialTheme.typography.bodyMedium)
                if (ui.skippedFiles > 0) Text("${ui.skippedFiles} file(s) could not be read and were skipped.", style = MaterialTheme.typography.bodySmall)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            if (ui.skippedFiles > 0) item { Text("${ui.skippedFiles} file(s) could not be read and were skipped.", style = MaterialTheme.typography.bodySmall) }
            for (type in order) {
                val groups = ui.results.groupsOf(type)
                if (groups.isEmpty()) continue
                val open = expanded[type] ?: true
                item(key = "h-$type") {
                    Row(Modifier.fillMaxWidth().clickable { expanded[type] = !open }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${type.title()} (${groups.size})", style = MaterialTheme.typography.titleMedium)
                            Text(type.explanation(), style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (open) "Collapse" else "Expand")
                    }
                }
                if (open) items(groups, key = { "${it.type}-${it.id}" }) { g -> GroupCard(g, ui, onOpenGroup) }
            }
        }
    }
}

@Composable
private fun GroupCard(group: DuplicateGroup, ui: UiState, onOpen: (String) -> Unit) {
    val first = group.files.first()
    val selectedHere = group.files.count { it.id in ui.selected }
    Card(Modifier.fillMaxWidth().clickable { onOpen(group.id) }) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FileThumbnail(first, 64.dp)
            Column(Modifier.weight(1f)) {
                Text(first.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text("${group.files.size} copies · ${formatSize(first.sizeBytes)} each", style = MaterialTheme.typography.bodySmall)
                Text(first.location.ifEmpty { "Selected folder" }, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(formatDate(first.lastModified), style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$selectedHere/${group.files.size}", style = MaterialTheme.typography.labelLarge)
                Text("selected", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
