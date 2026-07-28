package com.appblish.jgallery.feature.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * The swipe-down dismiss decision + 1:1 follow (motion-spec §2.2/§2.1). Pure JVM: snapshot-state
 * reads/writes work outside composition, and the tested paths never touch an `Animatable` frame clock —
 * the commit path returns after firing `onDismiss`; the reduced-motion snap-back resets instantly.
 */
class DismissStateTest {

    private val thresholdPx = 330f
    private val thresholdVelocityPx = 3300f // 1200dp/s × 2.75 density
    private val containerHeightPx = 2200f

    private fun state(reducedMotion: Boolean = false) =
        DismissState(thresholdPx, thresholdVelocityPx, containerHeightPx, reducedMotion)

    @Test
    fun `drag follows the finger 1 to 1 and clamps upward drag out of the fade`() {
        val s = state()
        s.onDrag(Offset(20f, 100f))
        s.onDrag(Offset(-5f, 50f))
        assertThat(s.translationY).isWithin(0.001f).of(150f) // 1:1 downward
        assertThat(s.translationX).isWithin(0.001f).of(15f) // x drifts freely with the thumb
        // An upward drag past the origin does not fade/shrink — y is floored at 0 (motion-spec §0).
        s.onDrag(Offset(0f, -400f))
        assertThat(s.translationY).isEqualTo(0f)
        assertThat(s.geometryProgress).isEqualTo(0f) // no fade/scale once back at/above the origin
    }

    @Test
    fun `release past the distance threshold commits the dismiss`() {
        val s = state()
        s.onDrag(Offset(0f, thresholdPx + 1f))
        var dismissed = false
        runBlocking { s.onRelease(Velocity(0f, 0f)) { dismissed = true } }
        assertThat(dismissed).isTrue()
    }

    @Test
    fun `a downward fling past the velocity threshold commits even below the distance`() {
        val s = state()
        s.onDrag(Offset(0f, 40f)) // well short of the 330px distance
        var dismissed = false
        runBlocking { s.onRelease(Velocity(0f, thresholdVelocityPx + 1f)) { dismissed = true } }
        assertThat(dismissed).isTrue()
    }

    @Test
    fun `an upward fling never dismisses even past the distance threshold`() {
        val s = state(reducedMotion = true) // instant snap-back, no frame clock needed
        s.onDrag(Offset(0f, thresholdPx + 500f))
        var dismissed = false
        runBlocking { s.onRelease(Velocity(0f, -9000f)) { dismissed = true } }
        assertThat(dismissed).isFalse()
        assertThat(s.active).isFalse() // snapped home
    }

    @Test
    fun `release before the threshold snaps back and does not dismiss`() {
        val s = state(reducedMotion = true)
        s.onDrag(Offset(10f, 100f)) // below 330px, slow release
        var dismissed = false
        runBlocking { s.onRelease(Velocity(0f, 200f)) { dismissed = true } }
        assertThat(dismissed).isFalse()
        assertThat(s.active).isFalse()
    }

    @Test
    fun `reduced motion pins scale and alpha while translation still follows`() {
        val s = state(reducedMotion = true)
        s.onDrag(Offset(0f, 1100f)) // g would be 1.0 → full travel
        assertThat(s.translationY).isEqualTo(1100f) // still 1:1
        assertThat(s.pageScale).isEqualTo(1f) // no scale morph under reduced motion (§5)
        assertThat(s.pageAlpha).isEqualTo(1f)
    }

    @Test
    fun `progress signals derive from vertical drag only`() {
        val s = state()
        s.onDrag(Offset(500f, 165f)) // big horizontal drift, half-threshold vertical
        assertThat(s.dismissProgress).isWithin(0.001f).of(0.5f) // p ignores x
        assertThat(s.geometryProgress).isWithin(0.001f).of(165f / 1100f)
    }
}
