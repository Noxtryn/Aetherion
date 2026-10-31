package dev.freelocs.aetherion.gui.animations

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sin

/**
 * Aurora's animation framework: a shared frame clock plus allocation-free, interruptible animators.
 *
 * All animators advance by the frame time ([dt], clamped so a hitch never explodes a simulation),
 * advance at most once per frame no matter how often they are read, and keep their current value and
 * velocity when retargeted - so any animation can be interrupted or reversed mid-flight without a jump.
 * In [reducedMotion] mode every animator snaps straight to its target.
 */
object AuroraAnimationEngine {
    /** Seconds since the previous frame (clamped to 50 ms). */
    var dt = 0.016f
        private set

    /** Monotonic seconds, advanced by [dt]; drives decorative ambient motion. */
    var time = 0f
        private set

    /** Incremented once per rendered frame; animators use it to avoid double-stepping. */
    var frame = 0
        private set

    var reducedMotion = false
    var decorative = true

    private var last = 0L

    fun beginFrame() {
        val now = System.nanoTime()
        dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0.0005f, 0.05f)
        last = now
        time += dt
        frame++
    }

    /** Test hook: advance the clock by a fixed step. */
    internal fun stepForTest(seconds: Float) {
        dt = seconds
        time += seconds
        frame++
    }
}

fun interface Easing {
    fun at(t: Float): Float

    companion object {
        val LINEAR = Easing { it }
        val OUT_CUBIC = Easing { 1f - (1f - it).pow(3) }
        val IN_OUT_CUBIC = Easing { if (it < 0.5f) 4f * it * it * it else 1f - (-2f * it + 2f).pow(3) / 2f }
        val OUT_QUART = Easing { 1f - (1f - it).pow(4) }
        val OUT_BACK = Easing { val c1 = 1.70158f; val c3 = c1 + 1f; 1f + c3 * (it - 1f).pow(3) + c1 * (it - 1f).pow(2) }
        val OUT_ELASTIC = Easing {
            if (it == 0f || it == 1f) it else (2.0.pow(-10.0 * it) * sin((it * 10.0 - 0.75) * (2.0 * Math.PI / 3.0)) + 1.0).toFloat()
        }
    }
}

/**
 * Critically/under-damped spring. Integrated with fixed 1/240 s sub-steps (semi-implicit Euler), which
 * is stable for the stiffness range used by the UI even at low frame rates.
 */
class Spring(initial: Float = 0f, private val stiffness: Float = 240f, private val damping: Float = 24f) {
    var value = initial
        private set
    var velocity = 0f
        private set
    var target = initial
    private var stamp = -1

    val atRest get() = value == target && velocity == 0f

    /** Jump to [v] without animating. */
    fun snap(v: Float) { value = v; target = v; velocity = 0f }

    fun update(): Float {
        if (stamp == AuroraAnimationEngine.frame) return value
        stamp = AuroraAnimationEngine.frame
        if (AuroraAnimationEngine.reducedMotion) { value = target; velocity = 0f; return value }
        if (atRest) return value
        val dt = AuroraAnimationEngine.dt
        val steps = ceil(dt / STEP).toInt().coerceIn(1, 12)
        val h = dt / steps
        repeat(steps) {
            velocity += (stiffness * (target - value) - damping * velocity) * h
            value += velocity * h
        }
        if (abs(target - value) < 0.0006f && abs(velocity) < 0.01f) { value = target; velocity = 0f }
        return value
    }

    private companion object { const val STEP = 1f / 240f }
}

/** Time based animator from the current value to a target with an easing curve; retargeting restarts from the current value. */
class Tween(initial: Float = 0f, var duration: Float = 0.2f, var easing: Easing = Easing.OUT_CUBIC) {
    var value = initial
        private set
    var target = initial
        private set
    private var from = initial
    private var elapsed = 0f
    private var stamp = -1

    val running get() = value != target

    fun animateTo(newTarget: Float, seconds: Float = duration) {
        if (newTarget == target) return
        from = value
        target = newTarget
        elapsed = 0f
        duration = seconds
        if (seconds <= 0f || AuroraAnimationEngine.reducedMotion) value = newTarget
    }

    fun snap(v: Float) { value = v; target = v; from = v; elapsed = 0f }

    fun update(): Float {
        if (stamp == AuroraAnimationEngine.frame) return value
        stamp = AuroraAnimationEngine.frame
        if (value == target) return value
        if (AuroraAnimationEngine.reducedMotion || duration <= 0f) { value = target; return value }
        elapsed += AuroraAnimationEngine.dt
        val t = (elapsed / duration).coerceIn(0f, 1f)
        value = if (t >= 1f) target else from + (target - from) * easing.at(t)
        return value
    }
}

/** 0..1 hover/press/focus style progress that eases towards a boolean state with a spring. */
class StateAnim(initial: Boolean = false, stiffness: Float = 300f, damping: Float = 28f) {
    private val spring = Spring(if (initial) 1f else 0f, stiffness, damping)
    fun set(on: Boolean) { spring.target = if (on) 1f else 0f }
    fun snap(on: Boolean) = spring.snap(if (on) 1f else 0f)
    fun update(): Float = spring.update()
    val target get() = spring.target
}
