package com.duplicatecleaner.data.repository

import android.content.IntentSender
import android.net.Uri
import com.duplicatecleaner.core.security.SafeDeleteManager
import com.duplicatecleaner.domain.model.DuplicateFile
import com.duplicatecleaner.domain.repository.DeletionRepository
import com.duplicatecleaner.domain.repository.LegacyDeleteOutcome
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeletionRepositoryImpl @Inject constructor(
    private val manager: SafeDeleteManager,
) : DeletionRepository {
    override fun buildMediaDeleteRequest(files: List<DuplicateFile>): IntentSender =
        manager.createMediaDeleteRequest(files.map { Uri.parse(it.uri) })

    override fun tryDeleteLegacyMedia(file: DuplicateFile): LegacyDeleteOutcome =
        manager.deleteMediaLegacy(Uri.parse(file.uri))

    override suspend fun deleteDocument(file: DuplicateFile): Boolean =
        withContext(Dispatchers.IO) { manager.deleteDocument(Uri.parse(file.uri)) }

    override suspend fun isPresent(file: DuplicateFile): Boolean =
        withContext(Dispatchers.IO) { manager.isPresent(Uri.parse(file.uri)) }
}
