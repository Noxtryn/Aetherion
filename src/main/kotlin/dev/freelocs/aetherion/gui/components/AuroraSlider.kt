package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.gui.animations.Spring
import dev.freelocs.aetherion.gui.animations.StateAnim
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Opacity
import dev.freelocs.aetherion.gui.theme.Theme
import dev.freelocs.aetherion.platform.Keys
import kotlin.math.roundToLong

/**
 * Horizontal slider. The drawn thumb follows the logical value through a spring, so jumps (click,
 * keyboard, reset) interpolate smoothly while dragging stays responsive. Right-click resets to the default.
 */
class AuroraSlider(
    private val min: Double,
    private val max: Double,
    private val step: Double,
    private val decimals: Int,
    private val suffix: String,
    private val default: Double,
    private val get: () -> Double,
    private val set: (Double) -> Unit,
    private val trackWidth: Int = 110
) : Widget() {
    private val frac = Spring(0f, stiffness = 380f, damping = 30f)
    private val drag = StateAnim(stiffness = 420f, damping = 30f)
    private val focusAnim = StateAnim()
    private var dragging = false
    private var initialised = false
    private var labelCache = ""
    private var labelValue = Double.NaN
    override val focusable get() = true

    init { w = trackWidth + 44; h = 16 }

    override fun preferredHeight(width: Int) = 16

    private fun fraction(v: Double) = ((v - min) / (max - min)).toFloat().coerceIn(0f, 1f)

    private fun snap(v: Double): Double = (min + ((v.coerceIn(min, max) - min) / step).roundToLong() * step).coerceIn(min, max)

    private fun commit(v: Double) {
        val s = snap(v)
        if (s != get()) set(s)
    }

    private fun label(v: Double): String {
        if (v != labelValue) {
            labelValue = v
            labelCache = (if (decimals == 0) v.roundToLong().toString() else String.format("%.${decimals}f", v)) + suffix
        }
        return labelCache
    }

    override fun render(mx: Int, my: Int) {
        val v = get()
        val target = fraction(v)
        if (!initialised) { frac.snap(target); initialised = true }
        frac.target = target
        val f = frac.update()
        val hv = hover.update()
        val dr = drag.apply { set(dragging) }.update()
        val fo = focusAnim.apply { set(focused) }.update()
        val prevAlpha = Draw.alpha
        if (!enabled) Draw.alpha = prevAlpha * Opacity.DISABLED

        val tw = w - 44
        val ty = y + h / 2 - 2
        Draw.round(x, ty, tw, 4, 2, Colors.lerp(Theme.border, Theme.cardHover, 0.4f))
        val fillW = (tw * f).toInt().coerceIn(0, tw)
        if (fillW > 0) Draw.round(x, ty, maxOf(fillW, 4), 4, 2, Theme.accent)
        val kx = x + (tw * f).toInt() - 5
        val ks = 10 + (dr * 2).toInt()
        if (hv > 0.01f || dr > 0.01f) Draw.glow(kx - 1, y + h / 2 - ks / 2, ks, ks, Colors.alpha(Theme.accent, 0.5f * maxOf(hv * 0.6f, dr) * Theme.glow), big = false)
        Draw.round(kx - (ks - 10) / 2, y + h / 2 - ks / 2, ks, ks, ks / 2, Colors.lerp(Colors.TEXT, Theme.accent, 0.25f * dr))
        if (fo > 0.01f) Draw.ring(kx - 2 - (ks - 10) / 2, y + h / 2 - ks / 2 - 2, ks + 4, ks + 4, ks / 2 + 2, Colors.alpha(Theme.accent, fo))
        Draw.textRight(label(v), x + w, y + (h - 8) / 2, Theme.textSecondary)
        Draw.alpha = prevAlpha
    }

    private fun setFromMouse(mx: Int) {
        val tw = w - 44
        commit(min + (max - min) * ((mx - x).toDouble() / tw).coerceIn(0.0, 1.0))
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!enabled || !contains(mx, my)) return false
        if (button == 1) { commit(default); return true }
        if (button != 0) return false
        Ui.focus(this)
        dragging = true
        setFromMouse(mx)
        return true
    }

    override fun mouseDragged(mx: Int, my: Int, button: Int, dx: Int, dy: Int): Boolean {
        if (!dragging) return false
        setFromMouse(mx); return true
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int): Boolean {
        if (!dragging) return false
        dragging = false; return true
    }

    override fun mouseScrolled(mx: Int, my: Int, amount: Double): Boolean {
        if (!enabled || !contains(mx, my) || !focused) return false
        commit(get() + step * (if (amount > 0) 1 else -1)); return true
    }

    override fun keyPressed(key: Int, ctrl: Boolean, shift: Boolean): Boolean {
        if (!focused || !enabled) return false
        val mult = if (shift) 5 else 1
        when (key) {
            Keys.LEFT, Keys.DOWN -> commit(get() - step * mult)
            Keys.RIGHT, Keys.UP -> commit(get() + step * mult)
            Keys.HOME -> commit(min)
            Keys.END -> commit(max)
            else -> return false
        }
        return true
    }
}
