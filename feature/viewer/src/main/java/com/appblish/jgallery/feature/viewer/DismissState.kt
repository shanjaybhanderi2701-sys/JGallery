package com.appblish.jgallery.feature.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Swipe-down-to-dismiss motion state, hoisted to `ViewerPager` (arbiter design APP-693 §6: dismiss
 * translates/fades the **whole** viewer and reveals a scrim, so it lives above the pages, not per-page).
 * The per-page gesture arbiter ([viewerZoomGestures]) is the pure producer of the raw vertical drag +
 * release; this class owns the designer's visual mapping (motion-spec APP-692 §2) and the threshold /
 * snap-back decision. The two never duplicate a number — both read [ViewerMotion].
 *
 * `dragOffset` is downward-positive on `.y` (clamped ≥ 0 — an upward drag only drifts `.x`, never fades
 * or shrinks) and free on `.x` (the photo drifts under the thumb, flagship feel, without affecting the
 * progress signals). All visual factors derive from `g`/`p` which are **vertical-only** (motion-spec §3
 * seam agreement 2).
 */
@Stable
internal class DismissState(
    private val thresholdPx: Float,
    private val thresholdVelocityPx: Float,
    private val containerHeightPx: Float,
    /** `ANIMATOR_DURATION_SCALE == 0`: no scale morph, translation still 1:1, instant fade on commit (§5). */
    private val reducedMotion: Boolean,
) {
    var dragOffset by mutableStateOf(Offset.Zero)
        private set

    // Commit exit (motion-spec §2.3): once a dismiss commits we run a short in-place fade-out — the photo
    // shrinks to the tile scale and photo+scrim fade to 0 where they sit — then the viewer pops. Seeded
    // from the live drag values so there is no discontinuity between "dragging" and "leaving" (§8 DoD).
    // While it runs, these three animatables OVERRIDE the `g`-derived visuals below.
    private var committing by mutableStateOf(false)
    private val commitScale = Animatable(1f)
    private val commitPhotoAlpha = Animatable(1f)
    private val commitScrimAlpha = Animatable(1f)

    /** True while a dismiss drag/animation is in flight — the pager applies the dismiss layer only then. */
    val active: Boolean get() = committing || dragOffset != Offset.Zero

    private val dragY: Float get() = dragOffset.y.coerceAtLeast(0f)

    /** `g` — geometry progress (scale/alpha/scrim). */
    val geometryProgress: Float get() = ViewerMotion.geometryProgress(dragY, containerHeightPx)

    /** `p` — dismiss progress (threshold / corner-radius). Exposed for tests + corner morph. */
    val dismissProgress: Float get() = ViewerMotion.dismissProgress(dragY, thresholdPx)

    val translationX: Float get() = dragOffset.x
    val translationY: Float get() = dragY
    val pageScale: Float get() = when {
        committing -> commitScale.value
        reducedMotion -> 1f
        else -> ViewerMotion.pageScale(geometryProgress)
    }
    val pageAlpha: Float get() = when {
        committing -> commitPhotoAlpha.value
        reducedMotion -> 1f
        else -> ViewerMotion.photoAlpha(geometryProgress)
    }

    /** Black backdrop alpha: 1.0 at rest (grid hidden) → 0.0 as the drag reveals what's behind. */
    val scrimAlpha: Float get() =
        if (committing) commitScrimAlpha.value else ViewerMotion.scrimAlpha(geometryProgress)

    /** 1:1 drag-follow (motion-spec §2.1): no smoothing/lag on translation. */
    fun onDrag(delta: Offset) {
        val next = dragOffset + delta
        dragOffset = Offset(next.x, next.y.coerceAtLeast(0f))
    }

    /**
     * Pointer-up decision (motion-spec §2.2). DISMISS if dragged past the threshold distance OR a
     * downward fling ≥ threshold velocity; an upward fling always cancels. Otherwise spring snap-back.
     * On dismiss we do **not** reset the drag — we hand off to the caller's shared-element close, which
     * animates the photo home to its grid tile from wherever it currently sits (seamless, motion-spec §2.3).
     */
    suspend fun onRelease(velocity: Velocity, onDismiss: () -> Unit) {
        // An upward fling (vy < 0) ALWAYS cancels → snap back, even past the distance threshold
        // (motion-spec §2.2). Otherwise commit on distance OR a downward fling past the velocity threshold.
        val shouldDismiss = velocity.y >= 0f && (dragY >= thresholdPx || velocity.y >= thresholdVelocityPx)
        if (shouldDismiss) {
            commitExit(onDismiss)
        } else {
            snapBack(velocity)
        }
    }

    /**
     * Seamless commit (motion-spec §2.3): rather than pop straight from the dragged position — which would
     * hard-cut, since the drag lives in a draw-phase `graphicsLayer` the shared-element close can't see —
     * continue the motion in place. The photo shrinks from its current drag scale to the tile scale and
     * photo+scrim fade to 0 over [ViewerMotion.DismissInPlaceFadeMs], **seeded from the live `g` values** so
     * there's no jump, then [onDismiss] pops (the now-invisible shared element morphs home imperceptibly).
     * Under reduced motion we skip the fade and pop immediately (§6).
     */
    private suspend fun commitExit(onDismiss: () -> Unit) {
        if (reducedMotion) {
            onDismiss()
            return
        }
        commitScale.snapTo(ViewerMotion.pageScale(geometryProgress))
        commitPhotoAlpha.snapTo(ViewerMotion.photoAlpha(geometryProgress))
        commitScrimAlpha.snapTo(ViewerMotion.scrimAlpha(geometryProgress))
        committing = true
        val spec = tween<Float>(ViewerMotion.DismissInPlaceFadeMs, easing = ViewerMotion.EmphasizedAccel)
        coroutineScope {
            launch { commitScale.animateTo(ViewerMotion.MinDismissScale, spec) }
            launch { commitPhotoAlpha.animateTo(0f, spec) }
            launch { commitScrimAlpha.animateTo(0f, spec) }
        }
        onDismiss()
    }

    /** Spring back to full-screen centre, velocity-seeded so the photo feels like it has weight (§2.4). */
    private suspend fun snapBack(velocity: Velocity) {
        if (reducedMotion) {
            dragOffset = Offset.Zero
            return
        }
        val anim = Animatable(dragOffset, Offset.VectorConverter)
        anim.animateTo(
            targetValue = Offset.Zero,
            animationSpec = ViewerMotion.SnapBackSpring,
            initialVelocity = Offset(velocity.x, velocity.y),
        ) { dragOffset = value }
    }

    /**
     * Cancel an in-flight dismiss back to centre with no residual velocity — used when a 2nd finger
     * arrives and the arbiter escalates DISMISSING → pinch, so the page doesn't teleport (arbiter §2 rule 3).
     */
    suspend fun cancelToSnapBack() {
        if (reducedMotion || dragOffset == Offset.Zero) {
            dragOffset = Offset.Zero
            return
        }
        val anim = Animatable(dragOffset, Offset.VectorConverter)
        anim.animateTo(Offset.Zero, ViewerMotion.SnapBackSpring) { dragOffset = value }
    }
}
