package com.duplicatecleaner

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duplicatecleaner.core.security.SafeDeleteManager
import com.duplicatecleaner.data.hashing.DuplicateGroupingEngine
import com.duplicatecleaner.data.hashing.FileHashCalculator
import com.duplicatecleaner.data.media.MediaStoreScanner
import com.duplicatecleaner.domain.model.FileCategory
import com.duplicatecleaner.domain.model.GroupType
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the real MediaStore. Files are created by this app, so it owns them and may scan/delete
 * them without extra consent on every supported Android version.
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dir = "${Environment.DIRECTORY_PICTURES}/DuplicateCleanerTest/"
    private val created = mutableListOf<Uri>()

    private fun png(color: Int): ByteArray {
        val b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun insert(name: String, bytes: ByteArray): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, dir)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        created += uri
        return uri
    }

    @After fun cleanup() { created.forEach { runCatching { context.contentResolver.delete(it, null, null) } } }

    @Test fun scanFindsExactDuplicatesAndIgnoresSameNameDifferentContent() = runBlocking {
        val red = png(Color.RED)
        insert("dup_a.png", red); insert("dup_b.png", red)
        insert("same_name.png", png(Color.BLUE)) // unique content
        val scanned = MediaStoreScanner(context).scan(FileCategory.PHOTO).filter { it.location == dir }
        assertEquals(3, scanned.size)
        val engine = DuplicateGroupingEngine()
        val hasher = FileHashCalculator()
        val hashed = engine.sizeCandidates(scanned).map { f ->
            f.copy(sha256 = hasher.sha256 { context.contentResolver.openInputStream(Uri.parse(f.uri)) })
        }
        val groups = engine.groupExact(hashed)
        assertEquals(1, groups.size)
        assertEquals(GroupType.EXACT_PHOTO, groups[0].type)
        assertEquals(setOf("dup_a.png", "dup_b.png"), groups[0].files.map { it.name }.toSet())
    }

    @Test fun safeDeleteRemovesOwnedMediaAndVerifiesAbsence() {
        val uri = insert("to_delete.png", png(Color.GREEN))
        val mgr = SafeDeleteManager(context)
        assertTrue(mgr.isPresent(uri))
        // API 30+: owned media can be deleted directly; protected media would use createDeleteRequest (needs UI).
        mgr.deleteMediaLegacy(uri)
        assertFalse(mgr.isPresent(uri))
        created.remove(uri)
    }

    @Test fun vanishedFileIsReportedAbsentWithoutCrashing() {
        val uri = insert("vanish.png", png(Color.CYAN))
        context.contentResolver.delete(uri, null, null)
        created.remove(uri)
        assertFalse(SafeDeleteManager(context).isPresent(uri))
    }
}
