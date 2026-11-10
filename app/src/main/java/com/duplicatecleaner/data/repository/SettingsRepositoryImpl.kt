package com.duplicatecleaner.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.duplicatecleaner.domain.model.ScanSettings
import com.duplicatecleaner.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.scanSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "scan_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    private object K {
        val photos = booleanPreferencesKey("scan_photos")
        val videos = booleanPreferencesKey("scan_videos")
        val documents = booleanPreferencesKey("scan_documents")
        val similar = booleanPreferencesKey("find_similar")
        val threshold = intPreferencesKey("similarity_threshold")
        val folders = stringSetPreferencesKey("document_folders")
        val lastScan = longPreferencesKey("last_scan_at")
    }

    private val data: Flow<Preferences> = context.scanSettingsStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    override val settings: Flow<ScanSettings> = data.map { p ->
        ScanSettings(
            scanPhotos = p[K.photos] ?: true,
            scanVideos = p[K.videos] ?: true,
            scanDocuments = p[K.documents] ?: true,
            findSimilarPhotos = p[K.similar] ?: true,
            similarityThreshold = (p[K.threshold] ?: 10).coerceIn(0, 10),
            documentFolderUris = p[K.folders] ?: emptySet(),
        )
    }

    override val lastScanAt: Flow<Long?> = data.map { it[K.lastScan] }

    override suspend fun update(transform: (ScanSettings) -> ScanSettings) {
        context.scanSettingsStore.edit { p ->
            val current = ScanSettings(
                p[K.photos] ?: true, p[K.videos] ?: true, p[K.documents] ?: true, p[K.similar] ?: true,
                (p[K.threshold] ?: 10).coerceIn(0, 10), p[K.folders] ?: emptySet(),
            )
            val n = transform(current)
            p[K.photos] = n.scanPhotos
            p[K.videos] = n.scanVideos
            p[K.documents] = n.scanDocuments
            p[K.similar] = n.findSimilarPhotos
            p[K.threshold] = n.similarityThreshold.coerceIn(0, 10)
            p[K.folders] = n.documentFolderUris
        }
    }

    override suspend fun setLastScanAt(timestamp: Long) {
        context.scanSettingsStore.edit { it[K.lastScan] = timestamp }
    }
}
