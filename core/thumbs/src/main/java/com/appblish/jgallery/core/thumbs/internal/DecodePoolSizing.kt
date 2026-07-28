package com.appblish.jgallery.core.thumbs.internal

/**
 * Device-tier-aware sizing for the bounded thumbnail decode pool (APP-701, APP-697 finding #5).
 *
 * The old sizing was purely `availableProcessors().coerceIn(2, 4)` — it ignored how much heap the
 * device actually has. Every concurrent bitmap decode holds a full-resolution intermediate on the
 * Java heap, so on a low-RAM / small-heap phone too many decodes in parallel cause GC thrash (and
 * OOM risk) during a fling — the exact device class where scroll must stay smooth. A high-end device
 * with a large heap and many cores can afford more slots, which cuts decode latency and lets landing
 * tiles fill faster. We therefore pick the pool from BOTH the core count and the memory tier
 * (`ActivityManager.isLowRamDevice` + `memoryClass`), not cores alone.
 *
 * Kept pure (no Android types) so the tiering is JVM-unit-testable; the caller supplies the device
 * signals. Constant tuning is confirmed against the APP-699 decode-count harness on real hardware.
 */
internal object DecodePoolSizing {
    /** Floor — even a 1-core / low-heap device keeps two slots so a fling is never single-threaded. */
    const val MIN_PARALLELISM = 2
    const val LOW_TIER = 2
    const val MID_TIER = 4
    const val HIGH_TIER = 6

    /** `memoryClass` (MB) at/under which the device is treated as small-heap regardless of cores. */
    const val LOW_HEAP_MB = 96
    /** `memoryClass` (MB) at/over which the widest pool is unlocked (given enough cores). */
    const val HIGH_HEAP_MB = 256

    /**
     * Parallelism for the decode dispatcher.
     *
     * @param cores `Runtime.availableProcessors()`.
     * @param isLowRam `ActivityManager.isLowRamDevice` — the OS's own low-memory classification.
     * @param memoryClassMb `ActivityManager.memoryClass` — the app's nominal heap budget in MB.
     */
    fun parallelism(cores: Int, isLowRam: Boolean, memoryClassMb: Int): Int {
        val tier = when {
            isLowRam || memoryClassMb <= LOW_HEAP_MB -> LOW_TIER
            memoryClassMb >= HIGH_HEAP_MB -> HIGH_TIER
            else -> MID_TIER
        }
        // Never ask for more slots than there are cores, never fall below the fling floor.
        return tier.coerceAtMost(cores.coerceAtLeast(1)).coerceAtLeast(MIN_PARALLELISM)
    }
}
