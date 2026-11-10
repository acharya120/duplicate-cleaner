package com.duplicatecleaner.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duplicatecleaner.core.util.formatDate
import com.duplicatecleaner.core.util.formatSize
import com.duplicatecleaner.domain.model.GroupType
import com.duplicatecleaner.domain.model.MediaAccess
import com.duplicatecleaner.domain.model.ScanState
import com.duplicatecleaner.presentation.scanning.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    ui: UiState,
    onScan: () -> Unit,
    onReview: () -> Unit,
    onSettings: () -> Unit,
    onGrantAccess: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); onDismissMessage() } }
    val r = ui.results
    val exactTypes = arrayOf(GroupType.EXACT_PHOTO, GroupType.EXACT_VIDEO, GroupType.EXACT_DOCUMENT)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Duplicate Cleaner") },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Scan settings") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Media access", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (ui.permission.access) {
                            MediaAccess.FULL -> "Full access to photos and videos."
                            MediaAccess.PARTIAL -> "Limited access: only the photos and videos you selected can be scanned."
                            MediaAccess.DENIED -> "No access to photos and videos yet. The app only reads them to find duplicates and never uploads anything."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (ui.permission.access != MediaAccess.FULL) {
                        OutlinedButton(onClick = onGrantAccess) {
                            Text(
                                when {
                                    ui.permission.access == MediaAccess.PARTIAL -> "Choose more photos"
                                    ui.permission.permanentlyDenied -> "Open app settings"
                                    else -> "Allow access"
                                },
                            )
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Last scan: ${r.completedAt?.let { formatDate(it) } ?: "never"}", style = MaterialTheme.typography.labelLarge)
                    StatRow("Duplicate photos", "${r.extraCopies(GroupType.EXACT_PHOTO)}")
                    StatRow("Similar photo groups", "${r.groupsOf(GroupType.SIMILAR_PHOTO).size + r.groupsOf(GroupType.BURST_PHOTO).size}")
                    StatRow("Duplicate videos", "${r.extraCopies(GroupType.EXACT_VIDEO)}")
                    StatRow("Duplicate documents", "${r.extraCopies(GroupType.EXACT_DOCUMENT)}")
                    StatRow("Potential recovery", formatSize(r.recoverable(*exactTypes)))
                }
            }
            (ui.scanState as? ScanState.Error)?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth(), enabled = !ui.scanState.isRunning) {
                Icon(Icons.Default.Search, contentDescription = null)
                Text("  Scan for duplicates")
            }
            if (r.groups.isNotEmpty()) {
                OutlinedButton(onClick = onReview, modifier = Modifier.fillMaxWidth()) { Text("Review ${r.groups.size} groups") }
            }
            Text(
                "Nothing is deleted without your selection and Android's confirmation. At least one copy of every group is always kept.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label); Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
