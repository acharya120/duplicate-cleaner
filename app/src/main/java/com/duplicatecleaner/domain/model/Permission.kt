package com.duplicatecleaner.domain.model

enum class MediaAccess { FULL, PARTIAL, DENIED }

data class PermissionState(
    val access: MediaAccess = MediaAccess.DENIED,
    /** True once the system will no longer show the permission dialog; user must use Settings. */
    val permanentlyDenied: Boolean = false,
)
