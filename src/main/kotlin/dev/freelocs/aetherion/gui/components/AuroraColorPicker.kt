package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.gui.animations.Spring
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Metrics
import dev.freelocs.aetherion.gui.theme.Opacity
import dev.freelocs.aetherion.gui.theme.Radius
import dev.freelocs.aetherion.gui.theme.Theme
import dev.freelocs.aetherion.platform.Keys

/** Color control: swatch button that opens an HSV picker popup (saturation/value square, hue bar, hex input, presets). */
class AuroraColorPicker(
    private val get: () -> Int,
    private val set: (Int) -> Unit,
    private val width: Int = 130
) : Widget() {
    init { w = width; h = Metrics.CONTROL_H }
    override val focusable get() = true

    override fun preferredHeight(width: Int) = Metrics.CONTROL_H

    val isOpen get() = Ui.popup.let { it is PickerPopup && it.owner === this }

    override fun render(mx: Int, my: Int) {
        val hv = hover.update()
        val prevAlpha = Draw.alpha
        if (!enabled) Draw.alpha = prevAlpha * Opacity.DISABLED
        Draw.round(x, y, w, h, Radius.MD, Colors.lerp(Theme.card, Theme.cardHover, hv))
        Draw.ring(x, y, w, h, Radius.MD, Colors.lerp(Theme.border, Theme.accent, if (isOpen) 1f else 0f))
        Draw.round(x + 4, y + 4, 22, h - 8, Radius.SM, get())
        Draw.ring(x + 4, y + 4, 22, h - 8, Radius.SM, Colors.alpha(Colors.TEXT, 0.25f))
        Draw.textMiddle(hex(get()), x + 33, y, h, Theme.text)
        Draw.alpha = prevAlpha
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!enabled || button != 0 || !contains(mx, my)) return false
        if (isOpen) Ui.closePopup() else Ui.openPopup(PickerPopup(this))
        return true
    }

    private companion object {
        fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)
    }

    private class PickerPopup(val owner: AuroraColorPicker) : Popup {
        private val pw = 152
        private val ph = 156
        private val reveal = Spring(0f, 420f, 34f)
        private val hsv = FloatArray(3)
        private var drag = 0 // 1 = SV square, 2 = hue bar
        private var closing = false
        private val field = AuroraTextField({ "#RRGGBB" }, 7, { Character.digit(it, 16) >= 0 || it == '#'.code }, onChange = { applyHex(it) }, onSubmit = { applyHex(it) })
        private val presets = intArrayOf(Colors.PINK, Colors.ICE, Colors.VIOLET, Colors.SUCCESS, Colors.WARNING, Colors.DANGER, Colors.TEXT, Colors.TEXT_SECONDARY)

        init {
            Colors.rgbToHsv(owner.get(), hsv)
            field.setText(hex(owner.get()))
        }

        private fun px(): Int = owner.x.coerceIn(4, (Ui.viewW - pw - 4).coerceAtLeast(4))
        private fun py(): Int = if (owner.y + owner.h + 4 + ph > Ui.viewH - 4) (owner.y - 4 - ph).coerceAtLeast(4) else owner.y + owner.h + 4

        private fun svX() = px() + 8
        private fun svY() = py() + 8
        private val svW = 136
        private val svH = 72
        private fun hueY() = svY() + svH + 8
        private fun fieldY() = hueY() + 18
        private fun presetY() = fieldY() + 26

        private fun current() = Colors.hsvToRgb(hsv[0], hsv[1], hsv[2])

        private fun applyHsv() {
            val c = current()
            owner.set(c)
            field.setText(hex(c))
        }

        private fun applyHex(text: String) {
            val s = text.removePrefix("#")
            if (s.length != 6) return
            val v = s.toIntOrNull(16) ?: return
            val c = v or (0xFF shl 24)
            Colors.rgbToHsv(c, hsv)
            owner.set(c)
        }

        override fun render(mx: Int, my: Int) {
            reveal.target = if (closing) 0f else 1f
            val r = reveal.update().coerceIn(0f, 1f)
            if (closing && r < 0.02f) { Ui.closePopup(); return }
            val prevAlpha = Draw.alpha
            Draw.alpha = prevAlpha * r
            val x = px(); val y = py()
            Draw.glow(x, y, pw, ph, Colors.alpha(0xFF000000.toInt(), 0.5f))
            Draw.round(x, y, pw, ph, Radius.LG, Theme.card)
            Draw.ring(x, y, pw, ph, Radius.LG, Theme.border)

            // saturation/value square: 34 columns, each a vertical gradient from full value to black
            val cols = 34
            for (i in 0 until cols) {
                val cx = svX() + svW * i / cols
                val cw = svX() + svW * (i + 1) / cols - cx
                Draw.vGradient(cx, svY(), cw, svH, Colors.hsvToRgb(hsv[0], (i + 0.5f) / cols, 1f), 0xFF000000.toInt())
            }
            Draw.ring(svX(), svY(), svW, svH, Radius.SM, Colors.alpha(Colors.TEXT, 0.2f))
            val kx = svX() + (hsv[1] * svW).toInt()
            val ky = svY() + ((1f - hsv[2]) * svH).toInt()
            Draw.ring(kx - 4, ky - 4, 8, 8, 4, 0xFFFFFFFF.toInt())
            Draw.ring(kx - 5, ky - 5, 10, 10, 5, 0x80000000.toInt())

            // hue bar
            val strips = 34
            for (i in 0 until strips) {
                val sx = svX() + svW * i / strips
                Draw.rect(sx, hueY(), svX() + svW * (i + 1) / strips - sx, 10, Colors.hsvToRgb((i + 0.5f) / strips, 1f, 1f))
            }
            val hx = svX() + (hsv[0] * svW).toInt()
            Draw.round(hx - 2, hueY() - 2, 4, 14, 2, 0xFFFFFFFF.toInt())
            Draw.ring(hx - 3, hueY() - 3, 6, 16, 3, 0x80000000.toInt())

            field.setBounds(svX(), fieldY(), svW, Metrics.CONTROL_H)
            field.hover.set(false)
            field.render(mx, my)

            for (i in presets.indices) {
                val sx = svX() + i * 17
                Draw.round(sx, presetY(), 14, 14, Radius.SM, presets[i])
                if ((presets[i] and 0xFFFFFF) == (owner.get() and 0xFFFFFF)) Draw.ring(sx - 1, presetY() - 1, 16, 16, 5, Colors.TEXT)
            }
            Draw.alpha = prevAlpha
        }

        override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
            if (field.contains(mx, my)) { field.mouseClicked(mx, my, button); return true }
            if (mx !in px() until px() + pw || my !in py() until py() + ph) {
                closing = true
                return owner.contains(mx, my)
            }
            if (button != 0) return true
            Ui.focus(null)
            when {
                mx in svX() until svX() + svW && my in svY() until svY() + svH -> { drag = 1; dragTo(mx, my) }
                mx in svX() until svX() + svW && my in hueY() - 2 until hueY() + 12 -> { drag = 2; dragTo(mx, my) }
                my in presetY() until presetY() + 14 -> {
                    val i = (mx - svX()) / 17
                    if (i in presets.indices) { Colors.rgbToHsv(presets[i], hsv); applyHsv() }
                }
            }
            return true
        }

        private fun dragTo(mx: Int, my: Int) {
            when (drag) {
                1 -> { hsv[1] = ((mx - svX()).toFloat() / svW).coerceIn(0f, 1f); hsv[2] = 1f - ((my - svY()).toFloat() / svH).coerceIn(0f, 1f); applyHsv() }
                2 -> { hsv[0] = ((mx - svX()).toFloat() / svW).coerceIn(0f, 0.999f); applyHsv() }
            }
        }

        override fun mouseDragged(mx: Int, my: Int, button: Int, dx: Int, dy: Int): Boolean {
            if (field.mouseDragged(mx, my, button, dx, dy)) return true
            if (drag == 0) return false
            dragTo(mx, my); return true
        }

        override fun mouseReleased(mx: Int, my: Int, button: Int): Boolean {
            field.mouseReleased(mx, my, button)
            val was = drag != 0; drag = 0; return was
        }

        override fun keyPressed(key: Int, ctrl: Boolean, shift: Boolean): Boolean {
            if (key == Keys.ESCAPE) { closing = true; return true }
            if (!field.focused) return true
            return field.keyPressed(key, ctrl, shift) || true
        }

        override fun charTyped(codepoint: Int) = field.charTyped(codepoint)
        override fun onClosed() { if (field.focused) Ui.focus(null) }
    }
}
