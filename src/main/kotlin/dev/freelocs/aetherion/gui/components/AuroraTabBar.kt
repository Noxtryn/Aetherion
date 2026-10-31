package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.gui.animations.Spring
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Metrics
import dev.freelocs.aetherion.gui.theme.Radius
import dev.freelocs.aetherion.gui.theme.Theme

/** Segmented tabs with an indicator that glides (spring) between tabs and can be retargeted mid-flight. */
class AuroraTabBar(
    private val labels: () -> List<String>,
    private val selected: () -> Int,
    private val onSelect: (Int) -> Unit
) : Widget() {
    private val indX = Spring(0f, 380f, 30f)
    private val indW = Spring(0f, 380f, 30f)
    private var initialised = false

    init { h = 26 }

    override fun preferredHeight(width: Int) = 26

    private fun tabWidth(label: String) = Draw.textWidth(label) + 24

    fun naturalWidth(): Int = labels().sumOf { tabWidth(it) } + 8

    override fun render(mx: Int, my: Int) {
        val l = labels()
        Draw.round(x, y, naturalWidth(), h, Radius.LG, Colors.lerp(Theme.surface, Theme.card, 0.5f))
        var tx = x + 4
        val sel = selected()
        for ((i, label) in l.withIndex()) {
            val tw = tabWidth(label)
            if (i == sel) {
                indX.target = tx.toFloat(); indW.target = tw.toFloat()
                if (!initialised) { indX.snap(tx.toFloat()); indW.snap(tw.toFloat()); initialised = true }
            }
            tx += tw
        }
        indX.update(); indW.update()
        Draw.round(indX.value.toInt(), y + 3, indW.value.toInt().coerceAtLeast(8), h - 6, Radius.MD, Colors.alpha(Theme.accent, 0.20f))
        Draw.ring(indX.value.toInt(), y + 3, indW.value.toInt().coerceAtLeast(8), h - 6, Radius.MD, Colors.alpha(Theme.accent, 0.5f))
        tx = x + 4
        for ((i, label) in l.withIndex()) {
            val tw = tabWidth(label)
            val over = mx >= tx && mx < tx + tw && my >= y && my < y + h
            Draw.textCentered(label, tx + tw / 2, y + (h - 8) / 2, if (i == sel) Theme.text else if (over) Colors.lerp(Theme.textSecondary, Theme.text, 0.6f) else Theme.textSecondary)
            tx += tw
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (button != 0 || !contains(mx, my)) return false
        var tx = x + 4
        for ((i, label) in labels().withIndex()) {
            val tw = tabWidth(label)
            if (mx >= tx && mx < tx + tw) { onSelect(i); return true }
            tx += tw
        }
        return false
    }
}
