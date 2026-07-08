package com.appblish.jgallery.core.storage

import android.content.Intent

/**
 * The PERMISSION half of the §1.6 storage boundary. Where [StorageAccess] reads and writes media,
 * this owns "does the app even have access, and how do we ask for it". Onboarding and the app shell
 * drive access exclusively through this interface — they never reference `MANAGE_EXTERNAL_STORAGE`,
 * `Environment.isExternalStorageManager()`, a `Settings` action, or a runtime-permission string.
 *
 * Why it is separate from [StorageAccess]: the request *mechanism* is the part that differs most
 * between backends. All Files Access sends the user to a system settings page; media permissions ask
 * for a runtime grant; SAF opens a document-tree picker. Modelling the request as [AccessRequest]
 * lets the onboarding flow stay identical when Play forces a swap (spec §1.6, §9.4) — only the
 * implementation bound in [di.StorageModule] changes, and it returns a different [AccessRequest] arm.
 *
 * Contract: [hasAccess] is `suspend` (may touch the system) and safe to call on any dispatcher.
 */
interface StoragePermissionController {

    /** Which permission strategy is in force — the swappable dimension (mirrors [StorageAccess.backend]). */
    val backend: StorageBackend

    /** True when the app currently holds enough access to enumerate and operate on media. */
    suspend fun hasAccess(): Boolean

    /**
     * How to obtain access with the current [backend]. The onboarding "Allow" button acts on the
     * returned [AccessRequest] without knowing which backend produced it.
     */
    fun accessRequest(): AccessRequest
}

/**
 * A backend-agnostic description of how to acquire storage access. Onboarding handles every arm, so
 * swapping [StorageBackend] changes which arm is returned — not the onboarding code (spec §1.6).
 */
sealed interface AccessRequest {

    /**
     * Send the user to a system settings screen and re-check [StoragePermissionController.hasAccess]
     * on return. Used by All Files Access (the system All-Files page, spec §9.3).
     */
    data class SystemSettings(val intent: Intent) : AccessRequest

    /**
     * Request runtime permissions via the Activity Result API. Used by the media-permissions backend
     * (the Play-migration fallback) and by All Files Access on pre-R devices (`READ_EXTERNAL_STORAGE`).
     */
    data class RuntimePermissions(val permissions: List<String>) : AccessRequest
}
