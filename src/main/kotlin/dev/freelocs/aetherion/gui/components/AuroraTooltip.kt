package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.gui.animations.Tween
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Radius
import dev.freelocs.aetherion.gui.theme.Theme

/** Hover tooltip with word wrapping; drawn in the overlay layer, flips to stay on screen, fades in. */
object AuroraTooltip {
    private const val MAX_W = 190
    private val fade = Tween(0f, 0.12f)
    private var lastText: String? = null
    private var lines: List<String> = emptyList()
    private var boxW = 0

    fun render(text: String?, mx: Int, my: Int) {
        if (text == null) {
            fade.animateTo(0f)
            if (fade.update() <= 0.01f) return
        } else {
            if (text != lastText) { lastText = text; wrap(text) }
            fade.animateTo(1f)
            fade.update()
        }
        if (lines.isEmpty()) return
        val h = lines.size * 10 + 10
        var tx = mx + 10
        var ty = my + 12
        if (tx + boxW > Ui.viewW - 4) tx = mx - boxW - 8
        if (ty + h > Ui.viewH - 4) ty = my - h - 8
        tx = tx.coerceAtLeast(4); ty = ty.coerceAtLeast(4)
        val prev = Draw.alpha
        Draw.alpha = prev * fade.value
        Draw.glow(tx, ty, boxW, h, Colors.alpha(0xFF000000.toInt(), 0.5f), big = false)
        Draw.round(tx, ty, boxW, h, Radius.MD, 0xFF0E1527.toInt())
        Draw.ring(tx, ty, boxW, h, Radius.MD, Theme.border)
        for ((i, l) in lines.withIndex()) Draw.text(l, tx + 7, ty + 5 + i * 10, Theme.text)
        Draw.alpha = prev
    }

    private fun wrap(text: String) {
        val out = ArrayList<String>()
        var line = StringBuilder()
        for (word in text.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (Draw.textWidth(candidate) > MAX_W && line.isNotEmpty()) { out += line.toString(); line = StringBuilder(word) } else line = StringBuilder(candidate)
        }
        if (line.isNotEmpty()) out += line.toString()
        lines = out
        boxW = (out.maxOfOrNull { Draw.textWidth(it) } ?: 0) + 14
    }
}

/** Shared word-wrapping with per-(text,width) caching for paragraphs. */
object TextWrap {
    private class Entry { var width = -1; var lines: List<String> = emptyList() }

    private val cache = HashMap<String, Entry>()

    /** Wraps [text] to [width]; the last result per text is cached, so steady-state frames do not allocate. */
    fun wrap(text: String, width: Int): List<String> {
        if (cache.size > 600) cache.clear()
        val e = cache.getOrPut(text) { Entry() }
        if (e.width != width) {
            e.width = width
            val out = ArrayList<String>()
            for (paragraph in text.split('\n')) {
                var line = ""
                for (word in paragraph.split(' ')) {
                    val candidate = if (line.isEmpty()) word else "$line $word"
                    if (Draw.textWidth(candidate) > width && line.isNotEmpty()) { out += line; line = word } else line = candidate
                }
                out += line
            }
            e.lines = out
        }
        return e.lines
    }

    fun clear() = cache.clear()
}
