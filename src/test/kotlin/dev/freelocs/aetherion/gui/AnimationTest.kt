package dev.freelocs.aetherion.gui

import dev.freelocs.aetherion.gui.animations.AuroraAnimationEngine
import dev.freelocs.aetherion.gui.animations.Easing
import dev.freelocs.aetherion.gui.animations.Spring
import dev.freelocs.aetherion.gui.animations.Tween
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.abs

class AnimationTest {
    @BeforeEach fun setUp() { AuroraAnimationEngine.reducedMotion = false }
    @AfterEach fun tearDown() { AuroraAnimationEngine.reducedMotion = false }

    private fun frames(n: Int, dt: Float = 0.016f, each: () -> Unit) = repeat(n) { AuroraAnimationEngine.stepForTest(dt); each() }

    @Test
    fun `spring settles exactly on its target`() {
        val s = Spring(0f); s.target = 1f
        frames(120) { s.update() }
        assertEquals(1f, s.value)
        assertTrue(s.atRest)
    }

    @Test
    fun `spring converges at any frame time including long hitches`() {
        for (dt in listOf(0.001f, 0.008f, 0.016f, 0.033f, 0.05f)) {
            val s = Spring(0f); s.target = 10f
            frames((6f / dt).toInt()) { s.update() }
            assertTrue(abs(s.value - 10f) < 0.01f, "dt=$dt value=${s.value}")
        }
    }

    @Test
    fun `a spring can be interrupted and reversed without jumping`() {
        val s = Spring(0f); s.target = 1f
        frames(6) { s.update() }
        val mid = s.value; val v = s.velocity
        assertTrue(mid in 0.01f..0.99f && v > 0f, "should be mid flight: $mid $v")
        s.target = 0f
        AuroraAnimationEngine.stepForTest(0.016f)
        val after = s.update()
        assertTrue(abs(after - mid) < 0.15f, "no jump on retarget: $mid -> $after")
        frames(150) { s.update() }
        assertEquals(0f, s.value)
    }

    @Test
    fun `updating more than once in a frame does not advance twice`() {
        val s = Spring(0f); s.target = 1f
        AuroraAnimationEngine.stepForTest(0.016f)
        val a = s.update(); val b = s.update(); val c = s.update()
        assertEquals(a, b); assertEquals(b, c)
    }

    @Test
    fun `reduced motion snaps animators to their target`() {
        AuroraAnimationEngine.reducedMotion = true
        val s = Spring(0f); s.target = 1f
        AuroraAnimationEngine.stepForTest(0.016f)
        assertEquals(1f, s.update())
        val t = Tween(0f, 1f); t.animateTo(5f)
        AuroraAnimationEngine.stepForTest(0.016f)
        assertEquals(5f, t.update())
    }

    @Test
    fun `tween reaches its target and restarts from the current value when retargeted`() {
        val t = Tween(0f, 0.2f, Easing.LINEAR)
        t.animateTo(1f)
        frames(6, 0.016f) { t.update() }
        val mid = t.value
        assertTrue(mid in 0.3f..0.7f, "mid=$mid")
        t.animateTo(0f)
        AuroraAnimationEngine.stepForTest(0.016f)
        assertTrue(t.update() < mid, "reverses from the current value")
        frames(30) { t.update() }
        assertEquals(0f, t.value)
        assertFalse(t.running)
    }

    @Test
    fun `easing curves hit their endpoints`() {
        for (e in listOf(Easing.LINEAR, Easing.OUT_CUBIC, Easing.IN_OUT_CUBIC, Easing.OUT_QUART, Easing.OUT_BACK, Easing.OUT_ELASTIC)) {
            assertEquals(0f, e.at(0f), 1e-4f)
            assertEquals(1f, e.at(1f), 1e-4f)
        }
    }

    @Test
    fun `frame clock clamps hitches`() {
        AuroraAnimationEngine.beginFrame()
        Thread.sleep(80)
        AuroraAnimationEngine.beginFrame()
        assertTrue(AuroraAnimationEngine.dt <= 0.05f)
    }
}
