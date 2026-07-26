package com.appblish.jgallery.core.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntRect
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/**
 * The physical fold/hinge posture of the device, mapped from `androidx.window`'s [FoldingFeature]
 * into a small, dependency-contained sealed type (APP-655 §7.2, architect note APP-648: keep the
 * `androidx.window` surface inside `:core:ui`, do not leak it up to features).
 *
 * The only hard requirement this exists to serve is "never render tappable targets under the hinge
 * occlusion" (APP-655 DoD). The half-open tabletop/book *split* layout is an explicit
 * nice-to-have-deferred, so this type carries just enough to (a) know a device is folded and
 * (b) know the occluding band's bounds when the hinge physically separates the display.
 */
sealed interface DevicePosture {

    /**
     * No actionable fold: either a plain phone/tablet, or a foldable that is fully flat (unfolded), or
     * an inspection/preview context with no hosting activity. Layouts treat this as an ordinary window.
     */
    data object Flat : DevicePosture

    /**
     * The device is half-opened around its hinge. [orientation] says whether the hinge runs
     * [HingeOrientation.VERTICAL] (book — two side-by-side panes) or [HingeOrientation.HORIZONTAL]
     * (tabletop — top/bottom panes).
     *
     * [occludingBoundsPx] is the hinge band in **window pixel** coordinates, and is non-null **only**
     * when the hinge physically occludes content (`FoldingFeature.OcclusionType.FULL`, e.g. a
     * dual-screen device with a real gap). Seamless foldables report a separating-but-not-occluding
     * hinge, so this stays null there — there is nothing to route tappable targets around.
     */
    data class HalfOpen(
        val orientation: HingeOrientation,
        val isSeparating: Boolean,
        val occludingBoundsPx: IntRect?,
    ) : DevicePosture

    /** The occluding hinge band, in window px, or null when nothing occludes content. */
    val occludingHingePx: IntRect?
        get() = (this as? HalfOpen)?.occludingBoundsPx
}

/** Which way the hinge runs across the window. */
enum class HingeOrientation { VERTICAL, HORIZONTAL }

/**
 * Pure mapping from the primitive facts of a [FoldingFeature] to a [DevicePosture], split out so it is
 * unit-testable without an Android window (FoldingFeature is an un-mockable framework interface).
 */
internal fun devicePostureFrom(
    isHalfOpen: Boolean,
    isVertical: Boolean,
    occludes: Boolean,
    boundsPx: IntRect,
): DevicePosture =
    if (!isHalfOpen) {
        DevicePosture.Flat
    } else {
        DevicePosture.HalfOpen(
            orientation = if (isVertical) HingeOrientation.VERTICAL else HingeOrientation.HORIZONTAL,
            isSeparating = true,
            occludingBoundsPx = boundsPx.takeIf { occludes },
        )
    }

/**
 * Start/end padding (in **window px**) that keeps a full-width horizontal bar (the viewer's top action
 * bar / bottom controls) entirely on ONE panel, clear of a **vertical occluding hinge** — the
 * "never a tappable target under the hinge occlusion" guard (APP-655 DoD). The bar is pushed onto the
 * wider panel so its controls stay as roomy as possible.
 *
 * Returns `0 to 0` for [DevicePosture.Flat], a non-occluding half-open hinge, or a horizontal hinge
 * (a center horizontal crease never crosses bars already pinned to the very top/bottom of the window).
 */
fun chromeHingeInsetPx(windowWidthPx: Int, posture: DevicePosture): Pair<Int, Int> {
    val hinge = (posture as? DevicePosture.HalfOpen)
        ?.takeIf { it.orientation == HingeOrientation.VERTICAL }
        ?.occludingBoundsPx
        ?: return 0 to 0
    val leftPanel = hinge.left.coerceAtLeast(0)
    val rightPanel = (windowWidthPx - hinge.right).coerceAtLeast(0)
    return if (leftPanel >= rightPanel) {
        // Keep the bar on the left panel: pad the end past the hinge's left edge.
        0 to (windowWidthPx - hinge.left).coerceIn(0, windowWidthPx)
    } else {
        // Keep the bar on the right panel: pad the start up to the hinge's right edge.
        hinge.right.coerceIn(0, windowWidthPx) to 0
    }
}

/**
 * Observe the live [DevicePosture] for the hosting activity via [WindowInfoTracker]. Emits [Flat]
 * until the first layout event arrives, and stays [Flat] with no hosting activity (previews,
 * inspection). Contained in `:core:ui` so no feature module ever touches `androidx.window` directly.
 */
@Composable
fun rememberDevicePosture(): DevicePosture {
    val context = LocalContext.current
    val activity = context.findActivity()
    if (activity == null || LocalView.current.isInEditMode) return DevicePosture.Flat

    val posture by produceState<DevicePosture>(initialValue = DevicePosture.Flat, activity) {
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .collect { layoutInfo ->
                val fold = layoutInfo.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()
                value = if (fold == null) {
                    DevicePosture.Flat
                } else {
                    val b = fold.bounds
                    devicePostureFrom(
                        isHalfOpen = fold.state == FoldingFeature.State.HALF_OPENED,
                        isVertical = fold.orientation == FoldingFeature.Orientation.VERTICAL,
                        occludes = fold.occlusionType == FoldingFeature.OcclusionType.FULL,
                        boundsPx = IntRect(b.left, b.top, b.right, b.bottom),
                    )
                }
            }
    }
    return posture
}
