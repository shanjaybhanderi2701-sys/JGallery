package com.appblish.jgallery.core.ui.grid

import kotlin.math.abs

/**
 * Pure fling-velocity gate for the directional thumbnail lookahead (APP-701, APP-697 finding #5).
 *
 * The decode pool is small and has NO per-request priority (ThumbnailModule "Fix 5"). During a fast
 * fling the directional prefetch ([PrefetchPlanner.ahead]) keeps enqueuing tiles that fly past the
 * viewport before their decode finishes, so those decodes occupy the scarce pool slots that the
 * tiles actually LANDING need — the prefetch competes with the visible tiles it exists to help, and
 * the report measured this as wasted decode work during fling. Above a velocity threshold we
 * therefore suspend the lookahead and cancel any batch already dispatched, handing the whole pool to
 * the visible tiles; the idle warm ([PrefetchPlanner.idleWarm]) refills the caches the instant the
 * fling settles. Below the threshold — a steady browse — the lookahead runs unchanged.
 *
 * Kept free of Compose/Coil/Android types so the threshold, hysteresis and smoothing are
 * JVM-unit-testable; device tuning of the constants is confirmed against the APP-699 decode-count
 * harness on real hardware.
 *
 * Moved verbatim from `feature/photos` into `:core:ui` (APP-722 P2) so every grid's shared prefetch
 * ([GridThumbnailPrefetch]) gates its lookahead the same way Photos always has — one implementation,
 * no fork.
 */
internal object FlingDecodeGate {

    /**
     * Items/sec the leading viewport edge must exceed to be treated as a fast fling and suspend the
     * lookahead. A steady drag scans well under this; only a hard fling crosses it. (Item index, not
     * rows — column-agnostic, which suits the adaptive grid.)
     */
    const val SUPPRESS_ITEMS_PER_SEC = 900f

    /**
     * Resume the lookahead once velocity falls back below this. The gap to [SUPPRESS_ITEMS_PER_SEC]
     * is hysteresis: it stops the gate flapping (and thrashing dispatch/cancel) around one boundary
     * as a fling decays.
     */
    const val RESUME_ITEMS_PER_SEC = 500f

    /** EMA weight on the newest velocity sample; lower = smoother, slower to react. */
    const val DEFAULT_ALPHA = 0.5f

    /**
     * Exponential-moving-average velocity in items/sec: blends the instantaneous sample
     * (`|indexDelta|` over [elapsedNanos]) into [prevVelocity]. Smoothing keeps a single jumpy frame
     * from tripping the gate. A non-positive interval is treated as no new information (keeps the
     * previous value) so a duplicate/zero-dt emission can't divide by zero or spike velocity.
     */
    fun smoothVelocity(
        prevVelocity: Float,
        indexDelta: Int,
        elapsedNanos: Long,
        alpha: Float = DEFAULT_ALPHA,
    ): Float {
        if (elapsedNanos <= 0L) return prevVelocity
        val instant = abs(indexDelta) * NANOS_PER_SECOND / elapsedNanos
        return prevVelocity + alpha * (instant - prevVelocity)
    }

    /**
     * Whether the directional lookahead should run at [velocity], given whether it was running last
     * tick ([currentlyPrefetching]) — the hysteresis state:
     * - while prefetching, keep going until velocity crosses the upper [SUPPRESS_ITEMS_PER_SEC];
     * - while suppressed, stay suppressed until velocity drops below the lower [RESUME_ITEMS_PER_SEC].
     */
    fun shouldPrefetchAhead(velocity: Float, currentlyPrefetching: Boolean): Boolean =
        if (currentlyPrefetching) velocity < SUPPRESS_ITEMS_PER_SEC
        else velocity < RESUME_ITEMS_PER_SEC

    private const val NANOS_PER_SECOND = 1_000_000_000f
}
