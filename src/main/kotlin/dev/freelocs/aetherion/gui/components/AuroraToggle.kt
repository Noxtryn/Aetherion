package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.gui.animations.Spring
import dev.freelocs.aetherion.gui.animations.StateAnim
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Opacity
import dev.freelocs.aetherion.gui.theme.Theme
import dev.freelocs.aetherion.platform.Keys

/**
 * Pill toggle whose knob rides an under-damped spring (small overshoot). Interrupting mid-flight just
 * retargets the spring, so rapid clicks reverse smoothly.
 */
class AuroraToggle(
    private val get: () -> Boolean,
    private val set: (Boolean) -> Unit
) : Widget() {
    companion object { const val W = 32; const val H = 16 }

    private val knob = Spring(0f, stiffness = 520f, damping = 22f)
    private val focusAnim = StateAnim()
    private var initialised = false
    override val focusable get() = true

    init { w = W; h = H }

    override fun preferredHeight(width: Int) = H

    fun toggle() { if (enabled) set(!get()) }

    override fun render(mx: Int, my: Int) {
        val on = get()
        if (!initialised) { knob.snap(if (on) 1f else 0f); initialised = true }
        knob.target = if (on) 1f else 0f
        val k = knob.update()
        val hv = hover.update()
        val fo = focusAnim.apply { set(focused) }.update()
        val prevAlpha = Draw.alpha
        if (!enabled) Draw.alpha = prevAlpha * Opacity.DISABLED

        val p = k.coerceIn(0f, 1f)
        val track = Colors.lerp(Colors.lerp(Theme.border, Theme.cardHover, 0.5f), Theme.accent, p)
        if (p > 0.05f) Draw.glow(x, y, w, h, Colors.alpha(Theme.accent, 0.45f * p * Theme.glow), big = false)
        Draw.round(x, y, w, h, h / 2, Colors.lerp(track, Colors.TEXT, 0.06f * hv))
        if (fo > 0.01f) Draw.ring(x - 1, y - 1, w + 2, h + 2, h / 2 + 1, Colors.alpha(Theme.accent, fo))
        val knobSize = h - 6
        val kx = x + 3 + ((w - 6 - knobSize) * k).toInt()
        Draw.round(kx, y + 3, knobSize, knobSize, knobSize / 2, Colors.lerp(Colors.TEXT_SECONDARY, 0xFFFFFFFF.toInt(), p))
        Draw.alpha = prevAlpha
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!enabled || button != 0 || !contains(mx, my)) return false
        Ui.focus(null); toggle(); return true
    }

    override fun keyPressed(key: Int, ctrl: Boolean, shift: Boolean): Boolean {
        if (!focused || (key != Keys.ENTER && key != Keys.ENTER_NUMPAD)) return false
        toggle(); return true
    }
}
