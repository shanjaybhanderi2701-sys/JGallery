package com.appblish.jgallery.core.storage.internal

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.appblish.jgallery.core.storage.AccessRequest
import com.appblish.jgallery.core.storage.StorageBackend
import com.appblish.jgallery.core.storage.StoragePermissionController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * All Files Access implementation of [StoragePermissionController] — the ONLY place the app names
 * `MANAGE_EXTERNAL_STORAGE`, `Environment.isExternalStorageManager()`, and the All-Files settings
 * `Settings` action (spec §1.6, §9). Everything above the boundary just calls [hasAccess] and acts
 * on [accessRequest]'s result.
 *
 * On R+ this is `MANAGE_EXTERNAL_STORAGE`, granted from the system All-Files page (spec §9.3). On
 * pre-R devices the same "all files" capability is the legacy `READ_EXTERNAL_STORAGE` runtime grant,
 * so [accessRequest] returns a [AccessRequest.RuntimePermissions] there — the onboarding flow already
 * handles both arms, which is exactly the swap-safety §1.6 requires.
 */
internal class AllFilesAccessPermissionController(
    private val appContext: Context,
    private val io: CoroutineDispatcher,
) : StoragePermissionController {

    override val backend: StorageBackend = StorageBackend.ALL_FILES_ACCESS

    override suspend fun hasAccess(): Boolean = withContext(io) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            appContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    override fun accessRequest(): AccessRequest {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return AccessRequest.RuntimePermissions(listOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
        // Prefer the app-scoped All-Files page (deep-links straight to JGallery's toggle, spec §9.3);
        // fall back to the global list, then to app-details, so onboarding never dead-ends on OEM skins.
        val packageUri = Uri.fromParts("package", appContext.packageName, null)
        val intent = firstResolvable(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
        )
        return AccessRequest.SystemSettings(intent)
    }

    // These are all system Settings activities, which are always visible to package-visibility
    // queries — no <queries> manifest entry is needed, so QueryPermissionsNeeded is a false positive.
    @SuppressLint("QueryPermissionsNeeded")
    private fun firstResolvable(vararg candidates: Intent): Intent =
        candidates.firstOrNull { it.resolveActivity(appContext.packageManager) != null }
            ?: candidates.last()
}
