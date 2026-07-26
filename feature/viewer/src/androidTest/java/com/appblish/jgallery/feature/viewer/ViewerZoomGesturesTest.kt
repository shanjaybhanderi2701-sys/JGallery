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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
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

        tapCanvas()
        assertFalse("first tap hides the chrome (goes immersive)", chromeVisible)

        // The stale-closure bug made this stay false forever — this is the crux of APP-667.
        tapCanvas()
        assertTrue("second tap must restore the chrome (toggle is bidirectional)", chromeVisible)

        tapCanvas()
        assertFalse("toggle repeats indefinitely — third tap hides again", chromeVisible)
    }

    private fun tapCanvas() {
        composeRule.onNodeWithTag(TAP_TARGET).performTouchInput { click() }
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
