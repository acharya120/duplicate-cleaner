package com.duplicatecleaner.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.duplicatecleaner.domain.model.DuplicateFile
import com.duplicatecleaner.domain.model.FileCategory
import com.duplicatecleaner.domain.model.FileSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** Lists photos/videos from MediaStore. Respects whatever access the user granted (full or partial). */
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Blocking query; call from Dispatchers.IO. Never throws for a revoked permission - returns what it could read. */
    suspend fun scan(category: FileCategory, onDiscovered: (Int) -> Unit = {}): List<DuplicateFile> {
        require(category != FileCategory.DOCUMENT) { "Documents are scanned through SAF" }
        val collection: Uri = when (category) {
            FileCategory.PHOTO -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.VOLUME_NAME,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
        )
        val out = ArrayList<DuplicateFile>()
        val seenIds = HashSet<Long>()
        try {
            context.contentResolver.query(
                collection, projection, "${MediaStore.MediaColumns.SIZE} > 0", null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val iMod = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val iPath = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val iVol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.VOLUME_NAME)
                val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                while (c.moveToNext()) {
                    if (out.size % 256 == 0) coroutineContext.ensureActive()
                    val id = c.getLong(iId)
                    if (!seenIds.add(id)) continue
                    val name = c.getString(iName) ?: continue
                    val relative = c.getString(iPath).orEmpty()
                    val volume = c.getString(iVol).orEmpty().ifEmpty { "external_primary" }
                    val volumeKey = if (volume == MediaStore.VOLUME_EXTERNAL_PRIMARY) "primary" else volume
                    out += DuplicateFile(
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        name = name,
                        mimeType = c.getString(iMime).orEmpty(),
                        sizeBytes = c.getLong(iSize),
                        lastModified = c.getLong(iMod) * 1000L,
                        location = relative,
                        category = category,
                        source = FileSource.MEDIASTORE,
                        physicalKey = "$volumeKey:$relative$name".lowercase(),
                        width = c.getInt(iW),
                        height = c.getInt(iH),
                    )
                    if (out.size % 100 == 0) onDiscovered(out.size)
                }
            }
        } catch (e: SecurityException) {
            // Permission revoked mid-scan: keep what we have.
        } catch (e: IllegalArgumentException) {
            // Provider rejected the query shape on this device: treat as nothing available.
        }
        onDiscovered(out.size)
        return out
    }

    companion object {
        fun openStream(resolver: ContentResolver, uri: String) = try {
            resolver.openInputStream(Uri.parse(uri))
        } catch (e: java.io.FileNotFoundException) {
            null
        }
    }
}
