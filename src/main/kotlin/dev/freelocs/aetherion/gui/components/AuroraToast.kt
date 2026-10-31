package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.common.services.Notice
import dev.freelocs.aetherion.common.services.NoticeKind
import dev.freelocs.aetherion.common.services.Notifications
import dev.freelocs.aetherion.core.Aetherion
import dev.freelocs.aetherion.gui.animations.AuroraAnimationEngine
import dev.freelocs.aetherion.gui.animations.Spring
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.engine.Icons
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Radius
import dev.freelocs.aetherion.gui.theme.Theme

/** Slide-in notification stack in the bottom-right corner. Consumes [Notifications] while the screen is open. */
object AuroraToast {
    private class Entry(val notice: Notice) {
        var age = 0f
        var leaving = false
        val slide = Spring(0f, 360f, 28f)
    }

    private val active = ArrayList<Entry>()
    private const val LIFETIME = 3.6f
    private const val MAX_VISIBLE = 3

    fun reset() = active.clear()

    fun render(rightEdge: Int, bottomEdge: Int) {
        val show = Aetherion.config.bool("general.interface", "toasts")
        while (active.size < MAX_VISIBLE) {
            val n = Notifications.pollToast() ?: break
            if (show) active += Entry(n)
        }
        val dt = AuroraAnimationEngine.dt
        var y = bottomEdge
        val it = active.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.age += dt
            if (e.age > LIFETIME) e.leaving = true
            e.slide.target = if (e.leaving) 0f else 1f
            val s = e.slide.update()
            if (e.leaving && s < 0.02f) { it.remove(); continue }
            val text = Draw.fit(e.notice.message, 220)
            val w = Draw.textWidth(text) + 40
            val h = 26
            y -= h
            val sp = s.coerceIn(0f, 1f)
            val x = rightEdge - w + ((1f - s) * (w + 16)).toInt()
            val prev = Draw.alpha
            Draw.alpha = prev * sp
            val color = when (e.notice.kind) {
                NoticeKind.SUCCESS -> Colors.SUCCESS
                NoticeKind.WARNING -> Colors.WARNING
                NoticeKind.ERROR -> Colors.DANGER
                NoticeKind.INFO -> Theme.accent
            }
            Draw.glow(x, y, w, h, Colors.alpha(0xFF000000.toInt(), 0.5f), big = false)
            Draw.round(x, y, w, h, Radius.LG, 0xFF0E1527.toInt())
            Draw.ring(x, y, w, h, Radius.LG, Colors.alpha(color, 0.45f))
            Draw.icon(if (e.notice.kind == NoticeKind.SUCCESS) Icons.CHECK else if (e.notice.kind == NoticeKind.INFO) Icons.INFO else Icons.WARNING, x + 9, y + 9, 1, color)
            Draw.text(text, x + 24, y + 9, Theme.text)
            Draw.alpha = prev
            y -= 6
        }
    }
}
