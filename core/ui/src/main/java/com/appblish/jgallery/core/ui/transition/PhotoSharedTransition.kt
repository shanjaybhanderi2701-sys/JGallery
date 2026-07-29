package com.appblish.jgallery.core.ui.transition

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Shared-element "popup" open plumbing for the premium photo viewer (APP-691 §1, motion-spec APP-692 §1).
 * Tapping a grid thumbnail must **grow the photo from its exact grid rect** into full-screen, and closing
 * reverses it — one continuous element, never a fade/cut.
 *
 * The photo grid and the viewer are sibling destinations in the app's single `NavHost`, so a
 * `SharedTransitionLayout` wrapping the host can morph a matched element across the navigation. Rather
 * than thread two scopes through four feature modules, the app publishes them as composition locals here;
 * the grid tile and the viewer image both tag themselves with [photoSharedElement] keyed on the media id.
 *
 * **No-op safe:** if either scope is absent (a destination that opted out, a preview, a test host), the
 * modifier is the identity — callers can tag unconditionally and the transition simply doesn't run.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The per-destination [AnimatedVisibilityScope] a `NavHost` `composable` lambda exposes as its receiver. */
val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

// Emphasized decelerate/accelerate (motion-spec §1.2). Duplicated from the viewer's ViewerMotion because
// :core:ui cannot depend on :feature:viewer; kept byte-identical to the authoritative motion contract.
private val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val EmphasizedAccel = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

/**
 * Bounds morph: **open** (target larger than source) runs 300 ms emphasized-decelerate — content rushes
 * in and settles softly; **close** runs 240 ms emphasized-accelerate — faster than open reads as
 * responsive (motion-spec APP-711 §1/§7, tightened from 250). Direction is inferred from which rect is
 * bigger. This is the shared-element close both the swipe-dismiss and the Back button route through
 * (APP-711 §3), so its close duration must stay byte-identical to `ViewerMotion.CloseDurationMs`.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
private val PhotoBoundsTransform = BoundsTransform { initial, target ->
    val opening = target.width * target.height >= initial.width * initial.height
    tween(
        durationMillis = if (opening) 300 else 240,
        easing = if (opening) EmphasizedDecel else EmphasizedAccel,
    )
}

/**
 * Tag this node as the photo [mediaId]'s shared element. The grid thumbnail (`ContentScale.Crop`) and the
 * viewer image (`ContentScale.Fit`) are *different* composables for the same photo, so this uses
 * `sharedBounds` (crossfade + bounds morph) rather than `sharedElement` (identical content) — the crop
 * relaxes into the fitted frame over the emphasized curve, one continuous element (motion-spec §1.1).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.photoSharedElement(mediaId: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@photoSharedElement.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "photo-$mediaId"),
            animatedVisibilityScope = visibilityScope,
            boundsTransform = PhotoBoundsTransform,
        )
    }
}
