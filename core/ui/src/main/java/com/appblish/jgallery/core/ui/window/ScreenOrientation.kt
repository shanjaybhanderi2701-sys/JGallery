package com.appblish.jgallery.core.ui.window

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Smallest-width breakpoint (dp) separating compact-width phones from tablets/foldables. Matches the
 * `sw600dp` resource qualifier and Material 3's lower bound for the Medium width class.
 *
 * The orientation-lock decision keys off the device's *smallest* width (`smallestScreenWidthDp`, an
 * `sw`-qualifier value that is invariant across rotation), **not** the live window width class — a
 * phone rotated to landscape momentarily reports an Expanded live width, but is still a compact-width
 * device that must re-lock to portrait when it leaves the viewer (APP-676).
 */
const val COMPACT_WIDTH_THRESHOLD_DP = 600

/**
 * The `Activity.requestedOrientation` the shell should apply for the current device and destination
 * (APP-651 — adaptive foundation; APP-676 — orientation-stable device signal).
 *
 * - **Compact-width device + viewer** → [ActivityInfo.SCREEN_ORIENTATION_FULL_USER]: the full-screen
 *   viewer may rotate to landscape so photos/videos fill the screen, while still honoring the system
 *   auto-rotate lock (unlike `SENSOR`, `FULL_USER` respects the user's rotation-lock toggle).
 * - **Compact-width device + non-viewer** → [ActivityInfo.SCREEN_ORIENTATION_PORTRAIT]: phones stay
 *   portrait-locked on lists/grids/detail so the vertical gallery layout is never stretched sideways.
 *   Because the input is the rotation-invariant device width, this re-locks portrait even when
 *   entered *from* landscape (e.g. Back out of a rotated viewer, or cold-launch while held landscape).
 * - **Non-compact device** (tablets, unfolded foldables, desktop/free-form windows) →
 *   [ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED]: never lock; the OS and the adaptive layouts drive
 *   orientation.
 *
 * [isCompactWidthDevice] must be derived from `smallestScreenWidthDp` (see [COMPACT_WIDTH_THRESHOLD_DP]),
 * not from the live [androidx.compose.material3.windowsizeclass.WindowWidthSizeClass]. Layout branching
 * (nav rail, adaptive grids) still keys off the live width class — only this orientation-lock policy
 * needs the stable device signal.
 *
 * Pure and side-effect-free so it is trivially unit-testable; the actual write happens in the central
 * shell writer (`JGalleryApp`) and in [LockScreenOrientation].
 */
fun desiredOrientation(
    isCompactWidthDevice: Boolean,
    isViewer: Boolean,
): Int = when {
    !isCompactWidthDevice -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
