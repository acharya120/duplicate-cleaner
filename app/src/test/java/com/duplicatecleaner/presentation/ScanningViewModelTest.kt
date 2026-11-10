package com.duplicatecleaner.presentation

import android.content.IntentSender
import com.duplicatecleaner.core.permissions.SafPermissionManager
import com.duplicatecleaner.domain.model.DuplicateGroup
import com.duplicatecleaner.domain.model.GroupType
import com.duplicatecleaner.domain.model.MediaAccess
import com.duplicatecleaner.domain.model.PermissionState
import com.duplicatecleaner.domain.model.ScanEvent
import com.duplicatecleaner.domain.model.ScanProgress
import com.duplicatecleaner.domain.model.ScanState
import com.duplicatecleaner.domain.repository.LegacyDeleteOutcome
import com.duplicatecleaner.presentation.scanning.DeletionUiState
import com.duplicatecleaner.presentation.scanning.ScanningViewModel
import com.duplicatecleaner.presentation.scanning.UiEvent
import com.duplicatecleaner.testFile
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanningViewModelTest {
    private val a = testFile("a", size = 10)
    private val b = testFile("b", size = 10)
    private val c = testFile("c", size = 10)
    private val group = DuplicateGroup("g", GroupType.EXACT_PHOTO, listOf(a, b, c))

    private lateinit var dups: FakeDuplicateRepository
    private lateinit var deletion: FakeDeletionRepository
    private val saf: SafPermissionManager = mockk(relaxed = true) { every { isStillGranted(any()) } returns true }

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm(
        permission: PermissionState = PermissionState(MediaAccess.FULL),
        scan: FakeScanRepository = FakeScanRepository { flow { awaitCancellation() } },
        perm: FakePermissionRepository = FakePermissionRepository(permission),
        deleteRepo: FakeDeletionRepository = FakeDeletionRepository().also { it.present += listOf("a", "b", "c") },
    ): ScanningViewModel {
        dups = FakeDuplicateRepository(listOf(group))
        deletion = deleteRepo
        return ScanningViewModel(scan, dups, perm, deletion, FakeSettingsRepository(), saf)
    }

    @Test fun `cannot select every file of a group`() {
        val vm = vm()
        vm.toggleFile("a"); vm.toggleFile("b"); vm.toggleFile("c")
        assertEquals(setOf("a", "b"), vm.ui.value.selected)
        assertNotNull(vm.ui.value.message)
    }

    @Test fun `chosen original cannot be selected for removal`() {
        val vm = vm()
        vm.setKeeper("g", "b")
        vm.toggleFile("b")
        assertFalse("b" in vm.ui.value.selected)
    }

    @Test fun `select all except keeper leaves exactly the keeper`() {
        val vm = vm()
        vm.setKeeper("g", "c")
        vm.selectAllExceptKeeper("g")
        assertEquals(setOf("a", "b"), vm.ui.value.selected)
    }

    @Test fun `delete request without selection only shows a message`() {
        val vm = vm()
        vm.requestDelete()
        assertTrue(vm.ui.value.deletion is DeletionUiState.None)
        assertNotNull(vm.ui.value.message)
    }

    @Test fun `deletion needs confirmation then verifies and updates results`() = runTest {
        val vm = vm()
        vm.toggleFile("a"); vm.toggleFile("b")
        vm.requestDelete()
        val confirm = vm.ui.value.deletion as DeletionUiState.Confirm
        assertEquals(2, confirm.plan.files.size)
        assertEquals(20L, confirm.plan.estimatedBytes)
        assertTrue("c" in deletion.present && "a" in deletion.present) // nothing deleted before confirmation
        vm.confirmDelete()
        val done = vm.ui.value.deletion as DeletionUiState.Done
        assertEquals(setOf("a", "b"), done.report.deleted.map { it.uri }.toSet())
        assertEquals(20L, done.report.recoveredBytes)
        assertEquals(setOf("c"), deletion.present)
        assertEquals(setOf("a", "b"), dups.removed)
        assertTrue(vm.ui.value.results.groups.isEmpty()) // group dissolves when only one copy remains
    }

    @Test fun `file that still exists after delete is reported failed not deleted`() {
        val repo = FakeDeletionRepository().also { it.present += listOf("a", "b", "c"); it.silentlyKeepsFile = setOf("b") }
        val vm = vm(deleteRepo = repo)
        vm.toggleFile("a"); vm.toggleFile("b")
        vm.requestDelete(); vm.confirmDelete()
        val r = (vm.ui.value.deletion as DeletionUiState.Done).report
        assertEquals(listOf("a"), r.deleted.map { it.uri })
        assertEquals(listOf("b"), r.failed.map { it.uri })
        assertFalse(r.isFullSuccess)
        assertEquals(setOf("a"), dups.removed)
    }

    @Test fun `partial failure reported when provider refuses one file`() {
        val repo = FakeDeletionRepository().also { it.present += listOf("a", "b", "c"); it.script["a"] = ArrayDeque(listOf(LegacyDeleteOutcome.Failed)) }
        val vm = vm(deleteRepo = repo)
        vm.toggleFile("a"); vm.toggleFile("b")
        vm.requestDelete(); vm.confirmDelete()
        val r = (vm.ui.value.deletion as DeletionUiState.Done).report
        assertEquals(listOf("b"), r.deleted.map { it.uri })
        assertEquals(listOf("a"), r.failed.map { it.uri })
    }

    @Test fun `files that disappeared before deletion are reported as already gone`() {
        val repo = FakeDeletionRepository().also { it.present += listOf("b", "c") } // "a" vanished
        val vm = vm(deleteRepo = repo)
        vm.toggleFile("a"); vm.toggleFile("b")
        vm.requestDelete(); vm.confirmDelete()
        val r = (vm.ui.value.deletion as DeletionUiState.Done).report
        assertEquals(listOf("a"), r.alreadyGone.map { it.uri })
        assertEquals(listOf("b"), r.deleted.map { it.uri })
    }

    @Test fun `declining Android consent stops deletion and deletes nothing more`() = runTest {
        val sender = mockk<IntentSender>()
        val repo = FakeDeletionRepository(sender).also {
            it.present += listOf("a", "b", "c")
            it.script["a"] = ArrayDeque(listOf(LegacyDeleteOutcome.NeedsConsent(sender)))
        }
        val vm = vm(deleteRepo = repo)
        vm.toggleFile("a"); vm.toggleFile("b")
        vm.requestDelete(); vm.confirmDelete()
        val event = vm.events.first()
        assertTrue(event is UiEvent.LaunchDeleteIntent)
        vm.onDeleteIntentResult(accepted = false)
        val r = (vm.ui.value.deletion as DeletionUiState.Done).report
        assertTrue(r.cancelledByUser)
        assertTrue(r.deleted.isEmpty())
        assertTrue("a" in repo.present && "b" in repo.present && "c" in repo.present)
    }

    @Test fun `accepting Android consent retries and verifies the file`() = runTest {
        val sender = mockk<IntentSender>()
        val repo = FakeDeletionRepository(sender).also {
            it.present += listOf("a", "b", "c")
            it.script["a"] = ArrayDeque(listOf(LegacyDeleteOutcome.NeedsConsent(sender), LegacyDeleteOutcome.Deleted))
        }
        val vm = vm(deleteRepo = repo)
        vm.toggleFile("a")
        vm.requestDelete(); vm.confirmDelete()
        vm.events.first()
        vm.onDeleteIntentResult(accepted = true)
        val r = (vm.ui.value.deletion as DeletionUiState.Done).report
        assertEquals(listOf("a"), r.deleted.map { it.uri })
        assertFalse(r.cancelledByUser)
    }

    @Test fun `scan without media access asks for permission instead of scanning`() = runTest {
        val vm = vm(permission = PermissionState(MediaAccess.DENIED))
        vm.onScanClicked()
        assertEquals(ScanState.RequestingPermission, vm.ui.value.scanState)
        assertTrue(vm.events.first() is UiEvent.RequestMediaPermissions)
    }

    @Test fun `permanently denied permission sends user to settings`() = runTest {
        val vm = vm(permission = PermissionState(MediaAccess.DENIED, permanentlyDenied = true))
        vm.onScanClicked()
        assertTrue(vm.events.first() is UiEvent.OpenAppSettings)
    }

    @Test fun `granting permission starts the pending scan`() {
        val perm = FakePermissionRepository(PermissionState(MediaAccess.DENIED))
        var started = false
        val scan = FakeScanRepository { started = true; flow { awaitCancellation() } }
        val vm = vm(scan = scan, perm = perm)
        vm.onScanClicked()
        perm.nextStateOnResult = PermissionState(MediaAccess.FULL)
        vm.onMediaPermissionResult(rationaleAvailable = true)
        assertTrue(started)
    }

    @Test fun `scan can be cancelled`() {
        val vm = vm()
        vm.onScanClicked()
        assertTrue(vm.ui.value.scanState.isRunning)
        vm.cancelScan()
        assertEquals(ScanState.Cancelled, vm.ui.value.scanState)
    }

    @Test fun `scan events drive ui state and completion`() {
        val scan = FakeScanRepository {
            flow {
                emit(ScanEvent.StateChanged(ScanState.ScanningPhotos, ScanProgress(filesDiscovered = 5)))
                emit(ScanEvent.Progress(ScanProgress(filesDiscovered = 5, filesProcessed = 2, percent = 40)))
                emit(ScanEvent.Finished(dups.results.value, skippedFiles = 1))
            }
        }
        val vm = vm(scan = scan)
        vm.onScanClicked()
        val s = vm.ui.value
        assertEquals(ScanState.Completed, s.scanState)
        assertEquals(100, s.progress.percent)
        assertEquals(1, s.skippedFiles)
        assertEquals(2, s.progress.potentialDuplicates)
    }

    @Test fun `scan failure becomes an understandable error state`() {
        val vm = vm(scan = FakeScanRepository { flow { throw IllegalStateException("boom") } })
        vm.onScanClicked()
        val st = vm.ui.value.scanState
        assertTrue(st is ScanState.Error)
        assertFalse((st as ScanState.Error).message.contains("boom"))
    }
}
