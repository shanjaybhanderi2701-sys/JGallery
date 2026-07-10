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
        val grid = device.wait(Until.findObject(By.res("photos_grid")), GRID_WAIT_MS)
            ?: error("photos_grid not found — is PhotosBenchmarkActivity showing the 10k grid?")

        // Keep the fling gesture off the screen edges (system gesture-nav zones would swallow it).
        grid.setGestureMargin(device.displayWidth / 5)
        repeat(SCROLLS_PER_ITERATION) {
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
    }
}
