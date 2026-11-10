package com.duplicatecleaner

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.duplicatecleaner.domain.model.DuplicateFile
import com.duplicatecleaner.domain.model.ScanState
import com.duplicatecleaner.presentation.duplicates.DetailsScreen
import com.duplicatecleaner.presentation.home.HomeScreen
import com.duplicatecleaner.presentation.results.DeletionDialogs
import com.duplicatecleaner.presentation.results.ResultsScreen
import com.duplicatecleaner.presentation.scanning.ScanningScreen
import com.duplicatecleaner.presentation.scanning.ScanningViewModel
import com.duplicatecleaner.presentation.scanning.UiEvent
import com.duplicatecleaner.presentation.settings.SettingsScreen
import com.duplicatecleaner.presentation.theme.DuplicateCleanerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: ScanningViewModel by viewModels()
    private var requestedPermissions: Array<String> = emptyArray()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val rationale = requestedPermissions.any { shouldShowRequestPermissionRationale(it) }
        vm.onMediaPermissionResult(rationale)
    }

    // System delete confirmation lives here (UI layer), never in the ViewModel.
    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        vm.onDeleteIntentResult(result.resultCode == Activity.RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onResume() // never trust earlier grants
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.events.collect { e ->
                    when (e) {
                        is UiEvent.RequestMediaPermissions -> { requestedPermissions = e.permissions; permissionLauncher.launch(e.permissions) }
                        is UiEvent.LaunchDeleteIntent -> deleteLauncher.launch(IntentSenderRequest.Builder(e.intentSender).build())
                        UiEvent.OpenAppSettings -> startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                        )
                    }
                }
            }
        }

        setContent {
            DuplicateCleanerTheme {
                val ui by vm.ui.collectAsStateWithLifecycle()
                val nav = rememberNavController()

                LaunchedEffect(ui.scanState) {
                    when (ui.scanState) {
                        ScanState.Completed -> { nav.navigate("results") { popUpTo("home") }; vm.acknowledgeScanEnd() }
                        ScanState.Cancelled, is ScanState.Error -> { nav.popBackStack("home", false); if (ui.scanState == ScanState.Cancelled) vm.acknowledgeScanEnd() }
                        ScanState.Preparing, ScanState.ScanningPhotos, ScanState.ScanningVideos, ScanState.ScanningDocuments,
                        ScanState.CalculatingHashes, ScanState.GroupingDuplicates ->
                            if (nav.currentDestination?.route != "scanning") nav.navigate("scanning")
                        else -> Unit
                    }
                }

                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(ui, onScan = vm::onScanClicked, onReview = { nav.navigate("results") }, onSettings = { nav.navigate("settings") },
                            onGrantAccess = vm::requestMediaAccess, onDismissMessage = vm::dismissMessage)
                    }
                    composable("scanning") { ScanningScreen(ui, onCancel = { vm.cancelScan() }) }
                    composable("results") {
                        ResultsScreen(ui, onBack = { nav.popBackStack() }, onOpenGroup = { vm.openGroup(it); nav.navigate("details") },
                            onRequestDelete = vm::requestDelete, onClearSelection = vm::clearSelection, onDismissMessage = vm::dismissMessage)
                    }
                    composable("details") {
                        DetailsScreen(ui, onBack = { nav.popBackStack() }, onToggle = vm::toggleFile, onKeep = vm::setKeeper,
                            onSelectOthers = vm::selectAllExceptKeeper, onClearGroup = vm::clearGroupSelection, onPreview = { preview(it) },
                            onRequestDelete = vm::requestDelete, onClearSelection = vm::clearSelection, onDismissMessage = vm::dismissMessage)
                    }
                    composable("settings") {
                        SettingsScreen(ui, onBack = { nav.popBackStack() }, onUpdate = vm::updateSettings, onAddFolder = vm::addFolder,
                            onRemoveFolder = vm::removeFolder, onGrantAccess = vm::requestMediaAccess, onClearData = vm::clearScanData)
                    }
                }
                DeletionDialogs(ui.deletion, onConfirm = vm::confirmDelete, onCancel = vm::cancelDeleteDialog, onDismissReport = vm::dismissReport)
            }
        }
    }

    /** Opens the file in whatever app can show it; read-only grant for that single URI. */
    private fun preview(file: DuplicateFile) {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(file.uri), file.mimeType.ifEmpty { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(view) } catch (e: ActivityNotFoundException) { /* no viewer installed: nothing else to do */ }
    }
}
