package com.appblish.jgallery.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The headline scroll-jank gate (spec §11 DoD; measurement fix APP-390, parent APP-386).
 *
 * ## Real decode, not synthetic tiles (APP-390 / R0-1)
 * The original APP-342 harness rendered tiles whose ids resolved to nothing, so every tile MISSED
 * the thumbnail fetcher and drew a placeholder — it measured pure list/layout and produced a false
 * ~1.9% jank pass. [PhotosBenchmarkActivity] now seeds ≥10,000 REAL image files into MediaStore and
 * renders tiles carrying REAL row-ids, so a fling drives the true `loadThumbnail` → decode → Coil
 * path. This lane therefore measures the decode-bound scroll that actually janks on device.
 *
 * ## Metrics
 * [FrameTimingMetric] reports frameDurationCpuMs P50/P90/P99 (the frame-cost percentiles a reviewer
 * reads). A jank PERCENT — frames over the ~16.67ms budget — is captured separately from
 * `dumpsys gfxinfo` per iteration and logged as `JGALLERY_BENCH_JANK …`, because FrameTimingMetric
 * does not emit a jank ratio directly. Both feed the PRE-FIX baseline this ticket posts.
 *
 * Physical-device EXECUTION (authoritative numbers) is R0-2 (Firebase Test Lab); on the CI emulator
 * the numbers are directional (host-GPU stalls), which is the established convention for this lane.
 */
@RunWith(AndroidJUnit4::class)
class PhotosScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollPhotosGrid10k() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        // WARM, not COLD (APP-382): on the CI emulator the COLD process-kill BETWEEN iterations is
        // what breaks the gate — a relaunched process lands on a display/window UiAutomation (display
        // 0) can't see. WARM keeps the process alive across iterations so the grid stays queryable.
        // The authoritative COLD 10k+ headline is the physical/FTL pass (R0-2), not this lane.
        startupMode = StartupMode.WARM,
        setupBlock = {
            // Bring PhotosBenchmarkActivity to the foreground on display 0, passing the corpus size so
            // it seeds ≥10,000 REAL files into MediaStore (idempotent — only iteration 0 pays for it,
            // and this whole block is UNMEASURED). `--display 0` is used because benchmark 1.3.3
            // exposes no supported launch-display id through the Intent API.
            for (attempt in 0 until LAUNCH_ATTEMPTS) {
                val status = device.executeShellCommand(
                    "am start-activity -W -n $TARGET_PACKAGE/$TARGET_ACTIVITY " +
                        "--ei corpus_size $corpusSize --display 0 --activity-clear-task",
                )
                check(!status.contains("Error:", ignoreCase = true)) {
                    "am start-activity of PhotosBenchmarkActivity on display 0 failed " +
                        "(attempt $attempt):\n$status"
                }
                // Grid queryable on display 0 → seed finished and the real-photo grid is up. The wait
                // is long because the FIRST iteration seeds thousands of files before first render;
                // `Until.hasObject` returns the instant the grid appears, so warm iterations clear it
                // immediately.
                if (device.wait(Until.hasObject(By.res("photos_grid")), SEED_AWARE_PROBE_MS) == true ||
                    device.wait(Until.hasObject(By.scrollable(true)), LAUNCH_PROBE_MS) == true
                ) {
                    break
                }
            }
        },
    ) {
        // Reset frame stats so the gfxinfo jank% below covers only THIS iteration's flings.
        device.executeShellCommand("dumpsys gfxinfo $TARGET_PACKAGE reset")

        // The grid exposes testTag "photos_grid" (bare resource-id via testTagsAsResourceId), so use
        // the single-arg By.res(id) matcher. Long wait covers the first-iteration seed+build race; it
        // is BEFORE the measured flings, so it never inflates frame timing.
        device.wait(Until.findObject(By.res("photos_grid")), GRID_WAIT_MS)
            ?: device.wait(Until.findObject(By.scrollable(true)), SCROLLABLE_FALLBACK_MS)
            ?: run {
                val dump = java.io.ByteArrayOutputStream().use { os ->
                    runCatching { device.dumpWindowHierarchy(os) }
                    os.toString()
                }
                error(
                    "Neither photos_grid (${GRID_WAIT_MS}ms) nor any scrollable node " +
                        "(${SCROLLABLE_FALLBACK_MS}ms) found after $LAUNCH_ATTEMPTS launch attempts — " +
                        "PhotosBenchmarkActivity is not showing the real-photo grid (seed may have " +
                        "failed). Window hierarchy follows:\n" + dump.take(12000),
                )
            }

        // Re-acquire a FRESH UiObject2 before EVERY fling — holding one handle across flings throws
        // StaleObjectException once the LazyVerticalGrid recycles nodes mid-scroll. Re-finding is a
        // cheap accessibility query (no app frame), so it does not distort FrameTimingMetric.
        repeat(SCROLLS_PER_ITERATION) {
            val grid = device.findObject(By.res("photos_grid"))
                ?: device.findObject(By.scrollable(true))
                ?: return@repeat
            grid.setGestureMargin(device.displayWidth / 5)
            grid.fling(Direction.DOWN)
            device.waitForIdle()
        }

        // Capture jank% for this iteration from gfxinfo and log it grep-ably. FrameTimingMetric owns
        // the frameDurationCpuMs percentiles; this adds the over-budget frame ratio the DoD asks for.
        val gfxinfo = device.executeShellCommand("dumpsys gfxinfo $TARGET_PACKAGE")
        android.util.Log.i("JGalleryBench", "JGALLERY_BENCH_JANK ${parseJank(gfxinfo)}")
    }

    private companion object {
        const val TARGET_PACKAGE = "com.appblish.jgallery"
        const val TARGET_ACTIVITY = "com.appblish.jgallery.benchmark.PhotosBenchmarkActivity"
        const val ITERATIONS = 5
        const val SCROLLS_PER_ITERATION = 10

        /** DoD floor: seed at least 10,000 real files. Override via `-P…androidx… jgallery.bench.corpusSize`. */
        const val DEFAULT_CORPUS_SIZE = 10_000

        /**
         * Corpus size for this run: the instrumentation arg `jgallery.bench.corpusSize` if present
         * (lets a CI smoke run seed fewer files without a code change), else the DoD floor of 10,000.
         */
        val corpusSize: Int
            get() = InstrumentationRegistry.getArguments()
                .getString("jgallery.bench.corpusSize")
                ?.toIntOrNull()
                ?: DEFAULT_CORPUS_SIZE

        const val LAUNCH_ATTEMPTS = 4

        /** Per-attempt grid-ready probe sized for the one-time first-iteration seed of thousands of files. */
        const val SEED_AWARE_PROBE_MS = 120_000L

        /** Warm-iteration probe once the corpus already exists (grid appears near-instantly). */
        const val LAUNCH_PROBE_MS = 12_000L

        /** Grid-ready wait inside the measured block (already covered by setupBlock; last-resort gate). */
        const val GRID_WAIT_MS = 120_000L

        /** Extra wait for any scrollable node once the tagged lookup misses. */
        const val SCROLLABLE_FALLBACK_MS = 10_000L

        /**
         * Parse `dumpsys gfxinfo` into a compact `jankyFramePercent=… totalFrames=…` string. The
         * relevant lines look like `Total frames rendered: 812` and `Janky frames: 96 (11.82%)`.
         */
        fun parseJank(gfxinfo: String): String {
            val total = Regex("Total frames rendered: (\\d+)").find(gfxinfo)?.groupValues?.get(1) ?: "?"
            val janky = Regex("Janky frames: (\\d+) \\(([\\d.]+)%\\)").find(gfxinfo)
            val jankPct = janky?.groupValues?.get(2) ?: "?"
            val jankCount = janky?.groupValues?.get(1) ?: "?"
            return "jankyFramePercent=$jankPct jankyFrames=$jankCount totalFrames=$total"
        }
    }
}
