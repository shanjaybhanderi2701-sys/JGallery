package com.appblish.jgallery.feature.viewer

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * Single source of truth for the premium-viewer motion contract (APP-711 `motion-spec`, which re-tunes
 * the numbers first defined in APP-692 — see the §7 delta table). Both the dismiss motion layer
 * ([DismissState]) **and** the gesture arbiter ([viewerZoomGestures]) read these numbers so
 * thresholds/velocities never drift between "what a dismiss looks like" and "when a drag commits to
 * dismiss" (arbiter design APP-693 §6, seam agreement 1; APP-711 §1/§4).
 *
 * The pure math ([dismissProgress]/[geometryProgress] and the visual mappings) takes **pixels** so it is
 * JVM-unit-testable with no Compose runtime; the dp/duration constants are converted at the call site.
 */
internal object ViewerMotion {

    // --- Open / close shared-element transform (motion-spec APP-711 §1) ---
    const val OpenDurationMs = 300
    const val CloseDurationMs = 240 // APP-711 §1/§7: tightened from 250 — a faster close reads as responsive

    /** Enter: content rushes in, settles softly. `CubicBezier(0.05, 0.7, 0.1, 1.0)`. */
    val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Exit: content accelerates back to the tile. `CubicBezier(0.3, 0.0, 0.8, 0.15)`. */
    val EmphasizedAccel = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** FastOutLinearIn — the dim leads the morph slightly so the incoming frame never flashes the grid. */
    val ScrimEasing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    // --- Swipe-down dismiss — Samsung One UI match (motion-spec APP-711 §1/§7) ---
    /** Fraction of the viewer container height that maps to the dismiss threshold distance (§7: 0.15→0.12). */
    const val ThresholdFraction = 0.12f
    val ThresholdDistanceMin = 80.dp // §7: 96→80 — ~100 dp threshold on a phone, dismisses on a shorter drag
    val ThresholdDistanceMax = 160.dp // §7: 200→160

    /** Downward fling (dp/s) that commits a dismiss regardless of distance (§7: 1200→1000 — a flick dismisses). */
    const val ThresholdVelocityDpPerSec = 1000f

    /** Container-height fraction that maps to full scale/fade travel (`g` reference; unchanged). */
    const val GeometryRefFraction = 0.5f

    /**
     * Claim dismiss iff `|dy| > VerticalLockRatio * |dx|` **and** `dy > 0` (down-only) — the arbiter's
     * axis-lock test (motion-spec §1/§4 rule 2). 1.0 = a drag that is even marginally more vertical than
     * horizontal, and downward, is a dismiss; anything horizontal-dominant or upward stays with the pager.
     */
    const val VerticalLockRatio = 1.0f

    /** Intent window before the drag axis locks (arbiter §2 axis lock; motion-spec §1/§4). */
    val DirectionLockSlop = 12.dp

    /** Dismiss exit (past threshold): the reverse of the open, seeded from the drag (motion-spec §2.3). */
    const val DismissExitDurationMs = 220

    /**
     * In-place fade-out fallback when the source grid-cell rect isn't available to morph back to
     * (motion-spec §2.3/§3.1 fallback): the photo shrinks to [MinDismissScale] and photo+scrim fade to 0
     * where they sit, then the viewer pops — continuous, never a hard cut.
     */
    const val DismissInPlaceFadeMs = 200

    // Chrome fade choreography (motion-spec §1.4 / §2.1 / §3.1) — kept here so callers share the numbers.
    const val ChromeFadeOutMs = 100

    // Scale/alpha travel endpoints (motion-spec §1/§2.1).
    const val MinDismissScale = 0.60f // §7: 0.5→0.60 — Samsung keeps the image larger while dragging (≈0.90 at threshold)
    const val MinPhotoAlpha = 0.70f

    /**
     * Snap-back spring (release before threshold): a *whisper* of overshoot, settles in ≈260 ms. The
     * single most premium-feel moment — must read elastic, not like a linear `tween` retract
     * (motion-spec §1/§2.4; §7: 0.80/380→0.82/420 for a snappier return). dampingRatio 0.82 stays within
     * the specced 0.78–0.86 band.
     */
    val SnapBackSpring = spring(dampingRatio = 0.82f, stiffness = 420f, visibilityThreshold = Offset.VisibilityThreshold)

    /** Scalar snap-back spring for the auxiliary progress animatables (same feel as [SnapBackSpring]). */
    val SnapBackSpringFloat = spring<Float>(dampingRatio = 0.82f, stiffness = 420f)

    /** Zoom double-tap / focal spring feel is owned by [ZoomState]; kept there (`StiffnessMediumLow`). */
    @Suppress("unused")
    const val ZoomSpringStiffness = Spring.StiffnessMediumLow

    // --- Pure, testable math (pixels in, unit-less progress / factors out) ---

    /** `thresholdDistance = clamp(0.12 × containerHeight, 80dp, 160dp)`, all in px (motion-spec §1/§7). */
    fun thresholdDistancePx(containerHeightPx: Float, minPx: Float, maxPx: Float): Float =
        (ThresholdFraction * containerHeightPx).coerceIn(minPx, maxPx)

    /**
     * `p` — dismiss progress: `clamp(dragY / thresholdDistance, 0, 1)`. Reaches 1.0 exactly at the
     * dismiss point; drives the threshold test and the corner-radius morph (motion-spec §0).
     */
    fun dismissProgress(dragY: Float, thresholdDistancePx: Float): Float =
        if (thresholdDistancePx <= 0f) 0f else (dragY / thresholdDistancePx).coerceIn(0f, 1f)

    /**
     * `g` — geometry progress: `clamp(dragY / (0.5 × containerHeight), 0, 1)`. Keeps evolving past the
     * threshold so continued dragging keeps shrinking/revealing; drives scale, photo alpha, scrim alpha.
     */
    fun geometryProgress(dragY: Float, containerHeightPx: Float): Float =
        if (containerHeightPx <= 0f) 0f
        else (dragY / (GeometryRefFraction * containerHeightPx)).coerceIn(0f, 1f)

    /** Page scale from `g`: `lerp(1.0, 0.60, g)` (motion-spec §1/§2.1). */
    fun pageScale(g: Float): Float = lerp(1f, MinDismissScale, g)

    /** Photo alpha from `g`: `lerp(1.0, 0.7, g)` — stays crisp, only a hint of fade (motion-spec §2.1). */
    fun photoAlpha(g: Float): Float = lerp(1f, MinPhotoAlpha, g)

    /** Scrim (black backdrop) alpha from `g`: `lerp(1.0, 0.0, g)` — grid peeks through (motion-spec §2.1). */
    fun scrimAlpha(g: Float): Float = lerp(1f, 0f, g)
}
