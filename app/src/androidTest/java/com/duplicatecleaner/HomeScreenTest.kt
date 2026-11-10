package com.duplicatecleaner

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duplicatecleaner.domain.model.MediaAccess
import com.duplicatecleaner.domain.model.PermissionState
import com.duplicatecleaner.domain.model.ScanState
import com.duplicatecleaner.presentation.home.HomeScreen
import com.duplicatecleaner.presentation.scanning.UiState
import com.duplicatecleaner.presentation.theme.DuplicateCleanerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun show(ui: UiState) = rule.setContent {
        DuplicateCleanerTheme { HomeScreen(ui, {}, {}, {}, {}, {}) }
    }

    @Test fun deniedAccessShowsExplanationAndAllowButton() {
        show(UiState(permission = PermissionState(MediaAccess.DENIED)))
        rule.onNodeWithText("Allow access").assertIsEnabled()
        rule.onNodeWithText("Last scan: never").assertIsEnabled()
    }

    @Test fun permanentlyDeniedOffersAppSettings() {
        show(UiState(permission = PermissionState(MediaAccess.DENIED, permanentlyDenied = true)))
        rule.onNodeWithText("Open app settings").assertIsEnabled()
    }

    @Test fun partialAccessOffersReselection() {
        show(UiState(permission = PermissionState(MediaAccess.PARTIAL)))
        rule.onNodeWithText("Choose more photos").assertIsEnabled()
    }

    @Test fun scanButtonDisabledWhileScanning() {
        show(UiState(scanState = ScanState.ScanningPhotos))
        rule.onNodeWithText("  Scan for duplicates").assertIsNotEnabled()
    }
}
