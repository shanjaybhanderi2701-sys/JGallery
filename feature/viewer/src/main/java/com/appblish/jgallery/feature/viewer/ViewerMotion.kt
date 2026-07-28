package com.appblish.jgallery.feature.viewer

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * Single source of truth for the premium-viewer motion contract (APP-692 `motion-spec`, §0). Both the
 * dismiss motion layer ([DismissState]) **and** the gesture arbiter ([viewerZoomGestures]) read these
 * numbers so thresholds/velocities never drift between "what a dismiss looks like" and "when a drag
 * commits to dismiss" (arbiter design APP-693 §6, seam agreement 1).
 *
 * The pure math ([dismissProgress]/[geometryProgress] and the visual mappings) takes **pixels** so it is
 * JVM-unit-testable with no Compose runtime; the dp/duration constants are converted at the call site.
 */
internal object ViewerMotion {

    // --- Open / close shared-element transform (motion-spec §1.2) ---
    const val OpenDurationMs = 300
    const val CloseDurationMs = 250

    /** Enter: content rushes in, settles softly. `CubicBezier(0.05, 0.7, 0.1, 1.0)`. */
    val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Exit: content accelerates back to the tile. `CubicBezier(0.3, 0.0, 0.8, 0.15)`. */
    val EmphasizedAccel = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** FastOutLinearIn — the dim leads the morph slightly so the incoming frame never flashes the grid. */
    val ScrimEasing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    // --- Swipe-down dismiss (motion-spec §0) ---
    /** Fraction of the viewer container height that maps to the dismiss threshold distance. */
    const val ThresholdFraction = 0.15f
    val ThresholdDistanceMin = 96.dp
    val ThresholdDistanceMax = 200.dp

    /** Downward fling (dp/s) that commits a dismiss regardless of distance. */
    const val ThresholdVelocityDpPerSec = 1200f

    /** Container-height fraction that maps to full scale/fade travel (`g` reference). */
    const val GeometryRefFraction = 0.5f

    /** Intent window before the drag axis locks (arbiter §2 axis lock; motion-spec §0). */
    val DirectionLockSlop = 12.dp

    /** Dismiss exit (past threshold): the reverse of the open, seeded from the drag (motion-spec §2.3). */
    const val DismissExitDurationMs = 220

    // Chrome fade choreography (motion-spec §1.4 / §2.1) — kept here so callers share the numbers.
    const val ChromeFadeOutMs = 100

    // Scale/alpha travel endpoints (motion-spec §2.1).
    const val MinDismissScale = 0.5f
    const val MinPhotoAlpha = 0.7f

    /**
     * Snap-back spring (release before threshold): a *whisper* of overshoot, settles in ≈300 ms. The
     * single most premium-feel moment — must read elastic, not like a linear `tween` retract
     * (motion-spec §2.4). dampingRatio 0.8 stays within the specced 0.75–0.85 band.
     */
    val SnapBackSpring = spring(dampingRatio = 0.8f, stiffness = 380f, visibilityThreshold = Offset.VisibilityThreshold)

    /** Scalar snap-back spring for the auxiliary progress animatables (same feel as [SnapBackSpring]). */
    val SnapBackSpringFloat = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)

    /** Zoom double-tap / focal spring feel is owned by [ZoomState]; kept there (`StiffnessMediumLow`). */
    @Suppress("unused")
    const val ZoomSpringStiffness = Spring.StiffnessMediumLow

    // --- Pure, testable math (pixels in, unit-less progress / factors out) ---

    /** `thresholdDistance = clamp(0.15 × containerHeight, 96dp, 200dp)`, all in px. */
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

    /** Page scale from `g`: `lerp(1.0, 0.5, g)` (motion-spec §2.1). */
    fun pageScale(g: Float): Float = lerp(1f, MinDismissScale, g)

    /** Photo alpha from `g`: `lerp(1.0, 0.7, g)` — stays crisp, only a hint of fade (motion-spec §2.1). */
    fun photoAlpha(g: Float): Float = lerp(1f, MinPhotoAlpha, g)

    /** Scrim (black backdrop) alpha from `g`: `lerp(1.0, 0.0, g)` — grid peeks through (motion-spec §2.1). */
    fun scrimAlpha(g: Float): Float = lerp(1f, 0f, g)
}
