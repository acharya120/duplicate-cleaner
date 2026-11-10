package com.duplicatecleaner.core.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persists / releases / validates SAF tree grants so selected folders survive reboots. */
@Singleton
class SafPermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun persist(uri: Uri): Boolean = try {
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, rw)
        } catch (e: SecurityException) {
            // Provider only offered read access: still useful for scanning, deletion will report failure.
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        true
    } catch (e: SecurityException) {
        false
    }

    fun release(uri: Uri) {
        try {
            val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.releasePersistableUriPermission(uri, rw)
        } catch (e: SecurityException) { /* grant already gone */ }
    }

    fun isStillGranted(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
}
