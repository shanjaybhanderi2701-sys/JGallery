package com.appblish.jgallery.core.ui.window

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * The `Activity.requestedOrientation` the shell should apply for the current window width and
 * destination (APP-651 — adaptive foundation).
 *
 * - **Compact + viewer** → [ActivityInfo.SCREEN_ORIENTATION_FULL_USER]: the full-screen viewer may
 *   rotate to landscape so photos/videos fill the screen, while still honoring the system
 *   auto-rotate lock (unlike `SENSOR`, `FULL_USER` respects the user's rotation-lock toggle).
 * - **Compact + non-viewer** → [ActivityInfo.SCREEN_ORIENTATION_PORTRAIT]: phones stay
 *   portrait-locked on lists/grids/detail so the vertical gallery layout is never stretched sideways.
 * - **Medium / Expanded** (tablets, unfolded foldables, desktop/free-form windows) →
 *   [ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED]: never lock; the OS and the adaptive layouts drive
 *   orientation.
 *
 * Pure and side-effect-free so it is trivially unit-testable; the actual write happens in the central
 * shell writer (`JGalleryApp`) and in [LockScreenOrientation].
 */
fun desiredOrientation(
    widthSizeClass: WindowWidthSizeClass,
    isViewer: Boolean,
): Int = when {
    widthSizeClass != WindowWidthSizeClass.Compact -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    isViewer -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}

/**
 * Unwrap a Compose [LocalContext] to its hosting [Activity], walking the [ContextWrapper] chain.
 * Returns `null` when there is no activity (e.g. `@Preview`/inspection contexts) so callers can no-op.
 */
fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * Lock the hosting activity to [orientation] for as long as this composable is in composition,
 * restoring the previous `requestedOrientation` on dispose.
 *
 * A reusable primitive: the central shell writer in `JGalleryApp` drives orientation for the whole
 * app, but individual screens can use this for a one-off local lock. No-ops (safely) when there is no
 * hosting activity.
 */
@Composable
fun LockScreenOrientation(orientation: Int) {
    val context = LocalContext.current
    DisposableEffect(orientation) {
        val activity = context.findActivity() ?: return@DisposableEffect onDispose { }
        val original = activity.requestedOrientation
        activity.requestedOrientation = orientation
        onDispose { activity.requestedOrientation = original }
    }
}
