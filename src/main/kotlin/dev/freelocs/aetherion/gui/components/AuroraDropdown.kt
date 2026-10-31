package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.gui.animations.Spring
import dev.freelocs.aetherion.gui.animations.StateAnim
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.engine.Icons
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Metrics
import dev.freelocs.aetherion.gui.theme.Opacity
import dev.freelocs.aetherion.gui.theme.Radius
import dev.freelocs.aetherion.gui.theme.Theme
import dev.freelocs.aetherion.platform.Keys

/** Select control. The option list opens as a [Popup] that reveals with a spring and flips upward near the screen bottom. */
class AuroraDropdown(
    private val ids: () -> List<String>,
    private val labelOf: (String) -> String,
    private val get: () -> String,
    private val set: (String) -> Unit,
    private val width: Int = 130
) : Widget() {
    private val focusAnim = StateAnim()
    private val openAnim = StateAnim()
    override val focusable get() = true

    init { w = width; h = Metrics.CONTROL_H }

    override fun preferredHeight(width: Int) = Metrics.CONTROL_H

    val isOpen get() = Ui.popup.let { it is DropdownPopup && it.owner === this }

    private fun open() {
        Ui.focus(this)
        Ui.openPopup(DropdownPopup(this))
    }

    override fun render(mx: Int, my: Int) {
        val hv = hover.update()
        val fo = focusAnim.apply { set(focused || isOpen) }.update()
        val op = openAnim.apply { set(isOpen) }.update()
        val prevAlpha = Draw.alpha
        if (!enabled) Draw.alpha = prevAlpha * Opacity.DISABLED
        Draw.round(x, y, w, h, Radius.MD, Colors.lerp(Theme.card, Theme.cardHover, hv))
        Draw.ring(x, y, w, h, Radius.MD, Colors.lerp(Theme.border, Theme.accent, maxOf(fo, op)))
        Draw.textMiddle(Draw.fit(labelOf(get()), w - 28), x + 8, y, h, Theme.text)
        Draw.icon(if (op > 0.5f) Icons.CHEVRON_DOWN.flippedCached else Icons.CHEVRON_DOWN, x + w - 15, y + (h - 8) / 2, 1, Theme.textSecondary)
        Draw.alpha = prevAlpha
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!enabled || button != 0 || !contains(mx, my)) return false
        if (isOpen) Ui.closePopup() else open()
        return true
    }

    override fun keyPressed(key: Int, ctrl: Boolean, shift: Boolean): Boolean {
        if (!focused || !enabled) return false
        when (key) {
            Keys.ENTER, Keys.ENTER_NUMPAD, Keys.DOWN -> if (!isOpen) open() else return false
            else -> return false
        }
        return true
    }

    private class DropdownPopup(val owner: AuroraDropdown) : Popup {
        private val items = owner.ids()
        private val rowH = 20
        private val maxRows = 8
        private val reveal = Spring(0f, 420f, 34f)
        private var scroll = 0
        private var highlighted = items.indexOf(owner.get()).coerceAtLeast(0)
        private val rows get() = minOf(items.size, maxRows)
        private val fullH get() = rows * rowH + 8
        private var closing = false

        private fun placeAbove() = owner.y + owner.h + 4 + fullH > Ui.viewH - 4 && owner.y - 4 - fullH >= 4

        private fun top(): Int = if (placeAbove()) owner.y - 4 - fullH else owner.y + owner.h + 4

        override fun render(mx: Int, my: Int) {
            reveal.target = if (closing) 0f else 1f
            val r = reveal.update().coerceIn(0f, 1f)
            if (closing && r < 0.02f) { Ui.closePopup(); return }
            val above = placeAbove()
            val full = fullH
            val vis = (full * r).toInt().coerceAtLeast(1)
            val py = if (above) top() + full - vis else top()
            Draw.glow(owner.x, py, owner.w, vis, Colors.alpha(0xFF000000.toInt(), 0.5f))
            Draw.round(owner.x, py, owner.w, vis, Radius.MD, Theme.card)
            Draw.ring(owner.x, py, owner.w, vis, Radius.MD, Theme.border)
            Draw.pushClip(owner.x, py, owner.w, vis)
            val base = top()
            for (i in 0 until rows) {
                val idx = i + scroll
                if (idx >= items.size) break
                val ry = base + 4 + i * rowH
                val over = mx >= owner.x && mx < owner.x + owner.w && my >= ry && my < ry + rowH
                if (over) highlighted = idx
                val selected = items[idx] == owner.get()
                if (idx == highlighted) Draw.round(owner.x + 3, ry, owner.w - 6, rowH, Radius.SM, Colors.alpha(Theme.accent, 0.14f))
                if (selected) Draw.round(owner.x + 3, ry + 4, 2, rowH - 8, 1, Theme.accent)
                Draw.textMiddle(Draw.fit(owner.labelOf(items[idx]), owner.w - 24), owner.x + 11, ry, rowH, if (selected) Theme.text else Theme.textSecondary)
                if (selected) Draw.icon(Icons.CHECK, owner.x + owner.w - 17, ry + (rowH - 8) / 2, 1, Theme.accent)
            }
            Draw.popClip()
        }

        private fun pick(i: Int) {
            if (i in items.indices) owner.set(items[i])
            closing = true
        }

        override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
            val base = top()
            if (mx >= owner.x && mx < owner.x + owner.w && my >= base && my < base + fullH) {
                if (button == 0) pick(scroll + (my - base - 4).coerceAtLeast(0) / rowH)
                return true
            }
            if (owner.contains(mx, my)) { closing = true; return true }
            closing = true
            return false // let the click fall through to whatever is underneath
        }

        override fun mouseScrolled(mx: Int, my: Int, amount: Double): Boolean {
            scroll = (scroll - amount.toInt().coerceIn(-1, 1)).coerceIn(0, (items.size - maxRows).coerceAtLeast(0))
            return true
        }

        override fun keyPressed(key: Int, ctrl: Boolean, shift: Boolean): Boolean {
            when (key) {
                Keys.DOWN -> highlighted = (highlighted + 1).coerceAtMost(items.size - 1)
                Keys.UP -> highlighted = (highlighted - 1).coerceAtLeast(0)
                Keys.ENTER, Keys.ENTER_NUMPAD -> pick(highlighted)
                Keys.ESCAPE -> closing = true
                else -> return true
            }
            if (highlighted < scroll) scroll = highlighted
            if (highlighted >= scroll + maxRows) scroll = highlighted - maxRows + 1
            return true
        }
    }
}
