package com.appblish.jgallery.macrobenchmark

import android.content.Intent
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Wave-2 headline gate (spec §11 DoD, APP-342): measure frame timing while scrolling a
 * 10 000+ item Photos grid. This is the harness the DoD's "operator-assisted physical-device
 * 10k+ frame-time pass" was always meant to run; with no physical device it runs on the CI
 * emulator and the physical pass is flagged operator-assisted (see macrobenchmark/README.md).
 *
 * [FrameTimingMetric] reports frameDurationCpuMs P50/P90/P99 — the numbers a reviewer reads to
 * confirm the 10k stream scrolls without jank. The benchmark launches [TARGET_ACTIVITY] (the
 * benchmark-variant-only PhotosBenchmarkActivity, which renders the real PhotosScreen over a fixed
 * 10.5k fixture), then flings the `photos_grid` list repeatedly.
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
        // COLD so each iteration measures a fresh render of the full 10k timeline, not a warm cache.
        startupMode = StartupMode.COLD,
        setupBlock = {
            val intent = Intent().apply {
                setClassName(TARGET_PACKAGE, TARGET_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivityAndWait(intent)
        },
    ) {
        // The grid exposes testTag "photos_grid"; PhotosBenchmarkActivity sets
        // testTagsAsResourceId=true so UiAutomator can find it out-of-process. Compose exposes the
        // tag as a BARE resource-id ("photos_grid"), not "<pkg>:id/photos_grid" — so this must use
        // the single-arg By.res(id) matcher, NOT By.res(pkg, id) (which looks for the pkg-prefixed
        // id and never matches). See Google's macrobenchmark samples.
        //
        // GRID_WAIT_MS is generous (not the default 5s): a COLD launch builds the 10.5k timeline on
        // the main thread before first composition, and startActivityAndWait can return on an early
        // frame before the LazyVerticalGrid's semantics are queryable by UiAutomator — that race is
        // what reddened the slower API-30 CI emulator (it passes locally on API 35). This wait is
        // BEFORE the measured flings, so it never inflates the reported frame timing.
        // Prefer the tagged grid, but fall back to ANY scrollable node: the LazyVerticalGrid is the
        // only scrollable on this screen, so this sidesteps any API-level quirk in how Compose
        // exposes testTagsAsResourceId to out-of-process UiAutomator (the API-30 CI emulator failed
        // By.res even at 30s, while API-35 resolves it fine). Flinging the scrollable container is
        // exactly the measurement we want either way.
        // Wait for the grid to be on screen and ready (long wait covers the COLD first-frame race).
        device.wait(Until.findObject(By.res("photos_grid")), GRID_WAIT_MS)
            ?: device.wait(Until.findObject(By.scrollable(true)), SCROLLABLE_FALLBACK_MS)
            ?: run {
                // Neither the tag nor a scrollable node appeared → the grid isn't on screen. Capture
                // the actual window hierarchy into the failure message so the uploaded test report
                // shows exactly what API-30 rendered, instead of guessing across CI rounds.
                val dump = java.io.ByteArrayOutputStream().use { os ->
                    runCatching { device.dumpWindowHierarchy(os) }
                    os.toString()
                }
                error(
                    "Neither photos_grid (${GRID_WAIT_MS}ms) nor any scrollable node " +
                        "(${SCROLLABLE_FALLBACK_MS}ms) found — PhotosBenchmarkActivity is not showing " +
                        "the 10k grid. Window hierarchy follows:\n" + dump.take(4000),
                )
            }

        // Re-acquire a FRESH UiObject2 before EVERY fling. Holding one handle across flings throws
        // StaleObjectException once the LazyVerticalGrid recomposes/recycles nodes mid-scroll (seen on
        // the API-30 emulator). Re-finding is a cheap accessibility query (no app frame), so it does
        // not distort FrameTimingMetric; the fling itself is the measured scroll.
        repeat(SCROLLS_PER_ITERATION) {
            val grid = device.findObject(By.res("photos_grid"))
                ?: device.findObject(By.scrollable(true))
                ?: return@repeat
            // Keep the fling gesture off the screen edges (system gesture-nav zones would swallow it).
            grid.setGestureMargin(device.displayWidth / 5)
            grid.fling(Direction.DOWN)
            device.waitForIdle()
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.appblish.jgallery"
        const val TARGET_ACTIVITY = "com.appblish.jgallery.benchmark.PhotosBenchmarkActivity"
        const val ITERATIONS = 5
        const val SCROLLS_PER_ITERATION = 10

        /** Grid-ready wait — generous for the slow CI emulator's COLD 10.5k first-frame race. */
        const val GRID_WAIT_MS = 30_000L

        /** Extra wait for any scrollable node once the tagged lookup misses (grid may still render). */
        const val SCROLLABLE_FALLBACK_MS = 10_000L
    }
}
