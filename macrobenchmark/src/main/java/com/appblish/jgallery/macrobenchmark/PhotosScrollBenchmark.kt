package com.appblish.jgallery.macrobenchmark

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
        // WARM, not COLD (APP-382): on the CI emulator the COLD process-kill BETWEEN iterations is
        // what breaks the gate. Evidence from two failed COLD runs: iteration 0 always rendered +
        // scrolled fine (produced a trace), but after the between-iteration process kill, iteration 1
        // relaunched onto a display/window UiAutomation (display 0) could not see — the dumped
        // hierarchy showed ONLY com.android.systemui, no app window — and warm re-launches within
        // that iteration never recovered it. The ticket's own diagnosis is decisive: a WARM `am
        // start` (process still alive) reliably exposes the grid on display 0. StartupMode.WARM keeps
        // the process warm across iterations, so the display-0 window association from the first
        // launch persists and every iteration stays queryable.
        //
        // Trade-off: WARM measures a warm-process scroll rather than a cold first render. That is the
        // right signal for THIS lane — a directional scroll-jank regression gate — and isolates
        // scroll cost from cold-start IO noise. The authoritative COLD 10k+ frame-time headline is
        // covered by the board-confirmed operator-assisted physical pass (see README / APP-369), not
        // by this emulator lane.
        startupMode = StartupMode.WARM,
        setupBlock = {
            // Bring PhotosBenchmarkActivity to the foreground on display 0. With WARM the process is
            // kept alive between iterations, so this is a warm `am start` from iteration 1 on — the
            // exact case the ticket confirmed exposes the grid. `am ... --display 0` is used because
            // benchmark 1.3.3 exposes no supported launch-display id through the Intent /
            // startActivityAndWait API. This runs in setupBlock (UNMEASURED); only the fling loop
            // below is measured. The retry/re-probe is belt-and-suspenders for the first launch.
            for (attempt in 0 until LAUNCH_ATTEMPTS) {
                val status = device.executeShellCommand(
                    "am start-activity -W -n $TARGET_PACKAGE/$TARGET_ACTIVITY " +
                        "--display 0 --activity-clear-task",
                )
                check(!status.contains("Error:", ignoreCase = true)) {
                    "am start-activity of PhotosBenchmarkActivity on display 0 failed " +
                        "(attempt $attempt):\n$status"
                }
                // Grid queryable on display 0 → ready to measure. `Until.hasObject` returns as soon as
                // it appears, so a successful launch clears this in well under LAUNCH_PROBE_MS.
                if (device.wait(Until.hasObject(By.res("photos_grid")), LAUNCH_PROBE_MS) == true ||
                    device.wait(Until.hasObject(By.scrollable(true)), LAUNCH_PROBE_MS) == true
                ) {
                    break
                }
                // Not ready → re-issue the warm `am start`. If every attempt misses, the measureBlock's
                // finder below does the final wait and captures the window hierarchy for diagnosis.
            }
        },
    ) {
        // The grid exposes testTag "photos_grid"; PhotosBenchmarkActivity sets
        // testTagsAsResourceId=true so UiAutomator can find it out-of-process. Compose exposes the
        // tag as a BARE resource-id ("photos_grid"), not "<pkg>:id/photos_grid" — so this must use
        // the single-arg By.res(id) matcher, NOT By.res(pkg, id) (which looks for the pkg-prefixed
        // id and never matches). See Google's macrobenchmark samples.
        //
        // GRID_WAIT_MS is generous (not the default 5s): a COLD launch builds the 10.5k timeline on
        // the main thread before first composition, and the launch can return on an early frame
        // before the LazyVerticalGrid's semantics are queryable by UiAutomator. setupBlock (APP-382)
        // already retries/relaunches until the grid is queryable on display 0, so this wait normally
        // resolves instantly; it is retained only as a last-resort gate that captures the window
        // hierarchy if the grid somehow still isn't present. It is BEFORE the measured flings, so it
        // never inflates the reported frame timing.
        // Prefer the tagged grid, but fall back to ANY scrollable node: the LazyVerticalGrid is the
        // only scrollable on this screen, so this sidesteps any API-level quirk in how Compose
        // exposes testTagsAsResourceId to out-of-process UiAutomator. Flinging the scrollable
        // container is exactly the measurement we want either way.
        // Wait for the grid to be on screen and ready (long wait covers the first-frame build race).
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
                        "(${SCROLLABLE_FALLBACK_MS}ms) found after ${LAUNCH_ATTEMPTS} launch attempts " +
                        "— PhotosBenchmarkActivity is not showing the 10k grid. Window hierarchy " +
                        "follows:\n" + dump.take(12000),
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

        /**
         * Launch attempts per iteration (APP-382): attempt 0 is the genuine COLD start; the rest are
         * WARM re-launches that recover the intermittent CI-emulator focus/display race. In the
         * common non-racing case attempt 0 succeeds and no re-launch happens.
         */
        const val LAUNCH_ATTEMPTS = 4

        /** Per-attempt grid-ready probe; `Until.hasObject` returns early once the grid appears. */
        const val LAUNCH_PROBE_MS = 12_000L

        /** Grid-ready wait — generous for the slow CI emulator's COLD 10.5k first-frame race. */
        const val GRID_WAIT_MS = 30_000L

        /** Extra wait for any scrollable node once the tagged lookup misses (grid may still render). */
        const val SCROLLABLE_FALLBACK_MS = 10_000L
    }
}
