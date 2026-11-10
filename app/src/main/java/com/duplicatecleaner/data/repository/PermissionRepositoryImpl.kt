package com.duplicatecleaner.data.repository

import com.duplicatecleaner.core.permissions.StoragePermissionManager
import com.duplicatecleaner.domain.model.MediaAccess
import com.duplicatecleaner.domain.model.PermissionState
import com.duplicatecleaner.domain.repository.PermissionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class PermissionRepositoryImpl @Inject constructor(
    private val manager: StoragePermissionManager,
) : PermissionRepository {
    private val _state = MutableStateFlow(PermissionState(manager.currentAccess()))
    override val state: StateFlow<PermissionState> = _state.asStateFlow()

    override fun requiredPermissions(): Array<String> = manager.requiredPermissions()

    /** Never trusts earlier grants: re-reads the real state (called on every resume). */
    override fun refresh() {
        val access = manager.currentAccess()
        _state.update { it.copy(access = access, permanentlyDenied = if (access == MediaAccess.FULL) false else it.permanentlyDenied) }
    }

    override fun onPermissionResult(rationaleAvailable: Boolean) {
        val access = manager.currentAccess()
        _state.value = PermissionState(access, permanentlyDenied = access != MediaAccess.FULL && !rationaleAvailable)
    }
}
