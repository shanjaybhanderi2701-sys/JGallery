package com.appblish.jgallery.feature.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression gate for APP-667: the viewer's immersive tap-to-toggle must flip the chrome BOTH ways,
 * indefinitely — tap hides, tap again restores, repeat.
 *
 * [viewerZoomGestures] installs its tap detector inside a `pointerInput` keyed only on the per-item
 * [ZoomState] (which never changes across recompositions), so that block runs exactly once. Before the
 * fix it permanently captured the FIRST `onTap` lambda — captured while `chromeVisible == true` — so
 * every subsequent tap evaluated `!true = false`: the chrome could be hidden but never brought back,
 * leaving the user stuck full-screen. The fix routes the live callback through `rememberUpdatedState`.
 *
 * This test reproduces the exact stale-lambda wiring from `ViewerScreen` (a *fresh*
 * `{ onChromeVisibleChange(!chromeVisible) }` lambda per recomposition, capturing the plain Boolean by
 * value) and asserts taps alternate the state. It exercises the gesture modifier directly rather than
 * the whole [ViewerScreen] because the image page swaps in the §8 unsupported card when no Coil fetcher
 * is registered in the test JVM (see [ViewerScreenTest]), which would remove the tap layer entirely.
 */
@RunWith(AndroidJUnit4::class)
class ViewerZoomGesturesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun singleTap_togglesChromeBothWays_repeatably() {
        var chromeVisible by mutableStateOf(true)
        composeRule.setContent {
            // Mirror the real hoist: chrome state lives up top and is handed down as a plain Boolean.
            ImmersiveTapHost(
                chromeVisible = chromeVisible,
                onChromeVisibleChange = { chromeVisible = it },
            )
        }

        assertTrue("chrome starts visible on viewer entry", chromeVisible)

        awaitTap(before = chromeVisible) { chromeVisible }
        assertFalse("first tap hides the chrome (goes immersive)", chromeVisible)

        // The stale-closure bug made this stay false forever — this is the crux of APP-667.
        awaitTap(before = chromeVisible) { chromeVisible }
        assertTrue("second tap must restore the chrome (toggle is bidirectional)", chromeVisible)

        awaitTap(before = chromeVisible) { chromeVisible }
        assertFalse("toggle repeats indefinitely — third tap hides again", chromeVisible)
    }

    /**
     * Regression gate for the arbiter's **down-only** dismiss claim (motion-spec APP-711 §4 rule 2, §5
     * diagonal-drag guard): a downward-dominant drag past the 12 dp direction-lock slop is claimed as a
     * dismiss (reports drag up), but an upward drag of the same magnitude is NOT — it stays inert with the
     * pager. Guards against a vertical-up drag being wrongly swallowed as a dismiss.
     */
    @Test
    fun verticalDrag_claimsDismissDownwardOnly() {
        val downDrags = mutableListOf<Offset>()
        val upDrags = mutableListOf<Offset>()
        var record = downDrags
        composeRule.setContent {
            val state = remember { ZoomState() }
            val scope = rememberCoroutineScope()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(TAP_TARGET)
                    .viewerZoomGestures(
                        state = state,
                        scope = scope,
                        onTap = {},
                        dismissEnabled = true,
                        onDismissDrag = { record.add(it) },
                    ),
            )
        }

        // Downward past the 12 dp slop → claimed as a dismiss, drag reported.
        composeRule.onNodeWithTag(TAP_TARGET).performTouchInput {
            down(center)
            moveBy(Offset(0f, 300f))
            up()
        }
        composeRule.waitForIdle()
        assertTrue("a downward-dominant drag must be claimed as a dismiss", downDrags.isNotEmpty())

        // Upward of the same magnitude → NOT a dismiss (down-only), nothing reported.
        record = upDrags
        composeRule.onNodeWithTag(TAP_TARGET).performTouchInput {
            down(center)
            moveBy(Offset(0f, -300f))
            up()
        }
        composeRule.waitForIdle()
        assertTrue("an upward drag must NOT be claimed as a dismiss (down-only)", upDrags.isEmpty())
    }

    /**
     * Taps the canvas and waits for the toggle to actually register. [detectTapGestures] here carries an
     * `onDoubleTap`, so it defers the single-tap `onTap` until the double-tap window (~doubleTapTimeout,
     * *real* time) closes. On a connected-device instrumented run `waitForIdle()` returns before that real
     * delay elapses, so a bare `click()` + `waitForIdle()` asserts too early and reads a stale value (the
     * failure is environmental, not a product regression — it reproduces on the pre-change arbiter too).
     * We poll real time until the hoisted state flips; if it never does (a genuine APP-667 regression) the
     * poll simply lapses and the following assertion reports it with its descriptive message.
     */
    private fun awaitTap(before: Boolean, current: () -> Boolean) {
        composeRule.onNodeWithTag(TAP_TARGET).performTouchInput { click() }
        runCatching { composeRule.waitUntil(timeoutMillis = 2_000) { current() != before } }
        composeRule.waitForIdle()
    }

    @Composable
    private fun ImmersiveTapHost(
        chromeVisible: Boolean,
        onChromeVisibleChange: (Boolean) -> Unit,
    ) {
        val state = remember { ZoomState() }
        val scope = rememberCoroutineScope()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(TAP_TARGET)
                .viewerZoomGestures(
                    state = state,
                    scope = scope,
                    // EXACTLY ViewerScreen:410 — a new lambda each recomposition that reads the plain
                    // `chromeVisible` Boolean by value. Pre-fix, the detector froze the first capture.
                    onTap = { onChromeVisibleChange(!chromeVisible) },
                ),
        )
    }

    private companion object {
        const val TAP_TARGET = "immersive_tap_target"
    }
}
