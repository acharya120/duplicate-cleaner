package com.duplicatecleaner.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.duplicatecleaner.domain.model.MediaAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Version-aware media permission logic. The pure functions in the companion are unit-tested. */
@Singleton
class StoragePermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun requiredPermissions(): Array<String> = requiredFor(Build.VERSION.SDK_INT)

    fun currentAccess(): MediaAccess = accessFor(Build.VERSION.SDK_INT) { perm ->
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val API_13 = 33
        private const val API_14 = 34

        fun requiredFor(sdk: Int): Array<String> = when {
            sdk >= API_14 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            )
            sdk >= API_13 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        fun accessFor(sdk: Int, isGranted: (String) -> Boolean): MediaAccess = when {
            sdk >= API_14 -> when {
                isGranted(Manifest.permission.READ_MEDIA_IMAGES) && isGranted(Manifest.permission.READ_MEDIA_VIDEO) -> MediaAccess.FULL
                isGranted("android.permission.READ_MEDIA_VISUAL_USER_SELECTED") -> MediaAccess.PARTIAL
                else -> MediaAccess.DENIED
            }
            sdk >= API_13 ->
                if (isGranted(Manifest.permission.READ_MEDIA_IMAGES) && isGranted(Manifest.permission.READ_MEDIA_VIDEO)) MediaAccess.FULL
                else MediaAccess.DENIED
            else -> if (isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) MediaAccess.FULL else MediaAccess.DENIED
        }
    }
}
