package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.gui.animations.StateAnim
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.engine.Icon
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Metrics
import dev.freelocs.aetherion.gui.theme.Opacity
import dev.freelocs.aetherion.gui.theme.Radius
import dev.freelocs.aetherion.gui.theme.Theme
import dev.freelocs.aetherion.platform.Keys

enum class ButtonStyle { PRIMARY, SECONDARY, GHOST, DANGER }

/** Text and/or icon button. [label] is a lambda so it follows language changes without rebuilding. */
class AuroraButton(
    private val label: () -> String,
    private val style: ButtonStyle = ButtonStyle.SECONDARY,
    private val icon: Icon? = null,
    private val onClick: () -> Unit
) : Widget() {
    private val press = StateAnim(stiffness = 500f, damping = 32f)
    private val focusAnim = StateAnim()
    private var down = false
    override val focusable get() = true

    init { h = Metrics.CONTROL_H }

    /** Width the button needs for its content. */
    fun naturalWidth(): Int = 14 + (if (icon != null) 12 else 0) + Draw.textWidth(label()).let { if (it > 0) it else 0 }

    override fun preferredHeight(width: Int) = Metrics.CONTROL_H

    override fun render(mx: Int, my: Int) {
        val hv = hover.update()
        val pr = press.update()
        val fo = focusAnim.apply { set(focused) }.update()
        val dis = if (enabled) 1f else Opacity.DISABLED
        val prevAlpha = Draw.alpha
        Draw.alpha = prevAlpha * dis

        val bg: Int
        val fg: Int
        val border: Int
        when (style) {
            ButtonStyle.PRIMARY -> { bg = Colors.lerp(Theme.accent, Colors.TEXT, 0.12f * hv); fg = 0xFF0B1020.toInt(); border = 0 }
            ButtonStyle.SECONDARY -> { bg = Colors.lerp(Theme.card, Theme.cardHover, hv); fg = Theme.text; border = Theme.border }
            ButtonStyle.GHOST -> { bg = Colors.alpha(Theme.accent, 0.10f * hv); fg = Colors.lerp(Theme.textSecondary, Theme.text, hv); border = 0 }
            ButtonStyle.DANGER -> { bg = Colors.lerp(Colors.alpha(Colors.DANGER, 0.14f), Colors.alpha(Colors.DANGER, 0.28f), hv); fg = Colors.DANGER; border = Colors.alpha(Colors.DANGER, 0.4f) }
        }
        val inset = if (pr > 0.01f) 1 else 0
        if (style == ButtonStyle.PRIMARY && Theme.glow > 0f && hv > 0.01f) Draw.glow(x, y, w, h, Colors.alpha(Theme.accent, 0.35f * hv * Theme.glow), big = false)
        Draw.round(x + inset, y + inset, w - inset * 2, h - inset * 2, Radius.MD, bg)
        if (border != 0) Draw.ring(x, y, w, h, Radius.MD, Colors.lerp(border, Theme.accent, 0.5f * fo))
        else if (fo > 0.01f) Draw.ring(x, y, w, h, Radius.MD, Colors.alpha(Theme.accent, fo))

        val text = Draw.fit(label(), w - 12 - (if (icon != null) 12 else 0))
        val tw = Draw.textWidth(text) + (if (icon != null) 12 else 0)
        var cx = x + (w - tw) / 2
        if (icon != null) { Draw.icon(icon, cx, y + (h - 8) / 2, 1, fg); cx += 12 }
        Draw.text(text, cx, y + (h - 8) / 2, fg)
        Draw.alpha = prevAlpha
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!enabled || button != 0 || !contains(mx, my)) return false
        down = true; press.set(true); Ui.focus(null)
        return true
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int): Boolean {
        if (!down) return false
        down = false; press.set(false)
        if (contains(mx, my)) onClick()
        return true
    }

    override fun keyPressed(key: Int, ctrl: Boolean, shift: Boolean): Boolean {
        if (!focused || !enabled || (key != Keys.ENTER && key != Keys.ENTER_NUMPAD)) return false
        onClick(); return true
    }
}
