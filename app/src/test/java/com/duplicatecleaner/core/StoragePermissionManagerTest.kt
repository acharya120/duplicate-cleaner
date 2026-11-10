package com.duplicatecleaner.core

import android.Manifest
import com.duplicatecleaner.core.permissions.StoragePermissionManager
import com.duplicatecleaner.domain.model.MediaAccess
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StoragePermissionManagerTest {
    private val visual = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    @Test fun `Android 10 to 12 request READ_EXTERNAL_STORAGE only`() {
        for (sdk in 29..32) assertArrayEquals(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), StoragePermissionManager.requiredFor(sdk))
    }

    @Test fun `Android 13 requests granular image and video permissions`() {
        assertArrayEquals(
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO),
            StoragePermissionManager.requiredFor(33),
        )
    }

    @Test fun `Android 14 also requests the user-selected permission`() {
        val req = StoragePermissionManager.requiredFor(34)
        assertEquals(3, req.size)
        assertEquals(visual, req.last())
    }

    @Test fun `never requests broad storage permissions`() {
        for (sdk in 29..35) {
            val req = StoragePermissionManager.requiredFor(sdk).toList()
            assertFalse("MANAGE_EXTERNAL_STORAGE" in req.map { it.substringAfterLast('.') })
            assertFalse("WRITE_EXTERNAL_STORAGE" in req.map { it.substringAfterLast('.') })
        }
    }

    @Test fun `Android 14 full, partial and denied`() {
        assertEquals(MediaAccess.FULL, StoragePermissionManager.accessFor(34) { it != visual })
        assertEquals(MediaAccess.FULL, StoragePermissionManager.accessFor(34) { true })
        assertEquals(MediaAccess.PARTIAL, StoragePermissionManager.accessFor(34) { it == visual })
        assertEquals(MediaAccess.DENIED, StoragePermissionManager.accessFor(34) { false })
    }

    @Test fun `Android 13 needs both images and video for full access`() {
        assertEquals(MediaAccess.FULL, StoragePermissionManager.accessFor(33) { true })
        assertEquals(MediaAccess.DENIED, StoragePermissionManager.accessFor(33) { it == Manifest.permission.READ_MEDIA_IMAGES })
    }

    @Test fun `Android 10 to 12 depend on READ_EXTERNAL_STORAGE`() {
        assertEquals(MediaAccess.FULL, StoragePermissionManager.accessFor(30) { it == Manifest.permission.READ_EXTERNAL_STORAGE })
        assertEquals(MediaAccess.DENIED, StoragePermissionManager.accessFor(30) { false })
    }

    @Test fun `revoked permission is detected because state is recomputed each time`() {
        var granted = true
        assertEquals(MediaAccess.FULL, StoragePermissionManager.accessFor(33) { granted })
        granted = false
        assertEquals(MediaAccess.DENIED, StoragePermissionManager.accessFor(33) { granted })
    }
}
