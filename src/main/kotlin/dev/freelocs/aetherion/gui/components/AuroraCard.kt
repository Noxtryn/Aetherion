package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Radius
import dev.freelocs.aetherion.gui.theme.Theme

/**
 * Raised surface used by every card-like block. Hover "elevation" is a one-unit lift plus a growing soft
 * shadow and an accent-tinted hairline; text is never scaled, so it stays pixel-crisp.
 */
open class AuroraCard : Widget() {
    /** Draws the card body and returns the vertical lift (0 or 1) content should be offset by. */
    protected fun surface(selected: Boolean = false, interactive: Boolean = true): Int {
        val hv = if (interactive) hover.update() else 0f
        val lift = if (hv > 0.5f) 1 else 0
        val ty = y - lift
        if (hv > 0.02f || selected) {
            Draw.glow(x, ty, w, h, Colors.alpha(0xFF000000.toInt(), 0.35f + 0.25f * hv))
            if (Theme.glow > 0f && (selected || hv > 0.3f)) Draw.glow(x, ty, w, h, Colors.alpha(Theme.accent, (if (selected) 0.18f else 0.10f * hv) * Theme.glow), big = false)
        }
        Draw.round(x, ty, w, h, Radius.LG, Colors.lerp(Theme.card, Theme.cardHover, hv))
        Draw.ring(x, ty, w, h, Radius.LG, Colors.lerp(Theme.border, Theme.accent, if (selected) 0.85f else 0.45f * hv))
        return lift
    }

    override fun render(mx: Int, my: Int) { surface() }
}

/** Small rounded label, e.g. a status. Returns its width so callers can lay chips out right-to-left. */
object Chip {
    fun width(text: String) = Draw.textWidth(text) + 12

    fun draw(text: String, x: Int, y: Int, color: Int): Int {
        val w = width(text)
        Draw.round(x, y, w, 14, 7, Colors.alpha(color, 0.16f))
        Draw.text(text, x + 6, y + 3, color)
        return w
    }

    /** Draws right-aligned ending at [right]; returns the left edge. */
    fun drawRight(text: String, right: Int, y: Int, color: Int): Int {
        val w = width(text)
        draw(text, right - w, y, color)
        return right - w
    }
}
