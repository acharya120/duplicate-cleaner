package com.duplicatecleaner.core.security

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.duplicatecleaner.domain.repository.LegacyDeleteOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes deletions. It never decides WHAT to delete - callers pass files the user explicitly selected
 * after SelectionPolicy approved the selection. Media deletions always go through system consent.
 */
@Singleton
class SafeDeleteManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    /** Android 11+: system dialog listing every file; nothing is deleted until the user accepts. */
    fun createMediaDeleteRequest(uris: List<Uri>): IntentSender {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { "createDeleteRequest needs API 30" }
        return MediaStore.createDeleteRequest(resolver, uris).intentSender
    }

    /** Android 10: direct delete; protected files raise RecoverableSecurityException with a consent intent. */
    fun deleteMediaLegacy(uri: Uri): LegacyDeleteOutcome = try {
        if (resolver.delete(uri, null, null) > 0) LegacyDeleteOutcome.Deleted else LegacyDeleteOutcome.AlreadyGone
    } catch (e: RecoverableSecurityException) {
        LegacyDeleteOutcome.NeedsConsent(e.userAction.actionIntent.intentSender)
    } catch (e: SecurityException) {
        LegacyDeleteOutcome.Failed
    } catch (e: IllegalArgumentException) {
        LegacyDeleteOutcome.AlreadyGone
    }

    /** SAF document: DocumentsContract first, DocumentFile as fallback for providers lacking direct delete. */
    fun deleteDocument(uri: Uri): Boolean = try {
        DocumentsContract.deleteDocument(resolver, uri) || DocumentFile.fromSingleUri(context, uri)?.delete() == true
    } catch (e: FileNotFoundException) {
        true
    } catch (e: SecurityException) {
        false
    } catch (e: UnsupportedOperationException) {
        DocumentFile.fromSingleUri(context, uri)?.delete() == true
    } catch (e: IllegalArgumentException) {
        false
    }

    /**
     * True if the provider still lists the item. Returns true when unsure (permission errors), so a deletion is
     * never reported as successful without proof.
     */
    fun isPresent(uri: Uri): Boolean = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { it.moveToFirst() } ?: false
    } catch (e: FileNotFoundException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    } catch (e: SecurityException) {
        true
    }
}
