package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.core.Aetherion
import dev.freelocs.aetherion.gui.animations.Spring
import dev.freelocs.aetherion.gui.animations.StateAnim
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Theme
import kotlin.math.roundToInt

/**
 * Vertical scroll state for a viewport: smooth, spring-followed scrolling (wheel input moves a target
 * that the visible offset chases, so a flick coasts to rest instead of stepping) plus a draggable thumb.
 */
class AuroraScrollbar {
    var x = 0; var y = 0; var w = 0; var h = 0
    var contentH = 0
        private set

    private val follow = Spring(0f, stiffness = 260f, damping = 30f)
    private val thumbHover = StateAnim()
    private var target = 0f
    private var dragging = false
    private var dragGrab = 0

    val maxScroll get() = (contentH - h).coerceAtLeast(0)
    val offset: Int get() = follow.value.roundToInt().coerceIn(0, maxScroll)
    val scrollable get() = contentH > h

    fun setViewport(vx: Int, vy: Int, vw: Int, vh: Int, content: Int) {
        x = vx; y = vy; w = vw; h = vh; contentH = content
        target = target.coerceIn(0f, maxScroll.toFloat())
        if (follow.target > maxScroll) { follow.target = target }
    }

    fun reset() { target = 0f; follow.snap(0f) }

    fun scrollTo(value: Float, animate: Boolean = true) {
        target = value.coerceIn(0f, maxScroll.toFloat())
        if (!animate) follow.snap(target)
    }

    /** Keeps [top]..[bottom] (content coordinates) inside the viewport. */
    fun ensureVisible(top: Int, bottom: Int) {
        if (top < target) scrollTo(top.toFloat() - 8)
        else if (bottom > target + h) scrollTo(bottom - h + 8f)
    }

    fun wheel(amount: Double) {
        if (!scrollable) return
        val smooth = Aetherion.config.bool("performance.render", "smoothScrolling")
        target = (target - amount.toFloat() * 34f).coerceIn(0f, maxScroll.toFloat())
        if (!smooth) follow.snap(target)
    }

    fun update() {
        follow.target = target
        follow.update()
    }

    private fun thumbH() = (h * h.toFloat() / contentH).roundToInt().coerceIn(24, h)
    private fun thumbY(): Int = if (maxScroll == 0) y else y + ((h - thumbH()) * (follow.value / maxScroll)).roundToInt()
    private fun inTrack(mx: Int, my: Int) = mx >= x + w - 10 && mx < x + w && my >= y && my < y + h

    fun render(mx: Int, my: Int) {
        if (!scrollable) return
        thumbHover.set(dragging || inTrack(mx, my))
        val hv = thumbHover.update()
        val tw = 3 + (hv * 2).roundToInt()
        val tx = x + w - tw - 2
        Draw.round(tx, thumbY(), tw, thumbH(), tw / 2, Colors.alpha(Colors.lerp(Theme.textSecondary, Theme.accent, hv), 0.35f + 0.5f * hv))
    }

    fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!scrollable || button != 0 || !inTrack(mx, my)) return false
        val ty = thumbY()
        if (my in ty until ty + thumbH()) dragGrab = my - ty
        else { dragGrab = thumbH() / 2; dragTo(my) }
        dragging = true
        return true
    }

    private fun dragTo(my: Int) {
        val track = (h - thumbH()).coerceAtLeast(1)
        scrollTo(((my - y - dragGrab).toFloat() / track).coerceIn(0f, 1f) * maxScroll, animate = false)
    }

    fun mouseDragged(my: Int): Boolean { if (!dragging) return false; dragTo(my); return true }
    fun mouseReleased(): Boolean { val was = dragging; dragging = false; return was }
}
