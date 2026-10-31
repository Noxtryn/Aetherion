package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.common.services.I18n
import dev.freelocs.aetherion.gui.animations.AuroraAnimationEngine
import dev.freelocs.aetherion.gui.animations.StateAnim
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Metrics
import dev.freelocs.aetherion.gui.theme.Opacity
import dev.freelocs.aetherion.gui.theme.Radius
import dev.freelocs.aetherion.gui.theme.Theme
import dev.freelocs.aetherion.platform.Keys
import dev.freelocs.aetherion.platform.Platform
import net.minecraft.client.input.KeyEvent

/**
 * Click, then press any key to bind it. Escape cancels, Backspace/Delete unbinds. The binding is stored
 * as a Minecraft key name (e.g. `key.keyboard.k`), which is stable across 26.2 and 26.3 even though their
 * numeric key codes differ.
 */
class AuroraKeybindField(
    private val get: () -> String,
    private val set: (String) -> Unit,
    private val width: Int = 130
) : Widget() {
    private val focusAnim = StateAnim()
    var listening = false
        private set
    private var labelFor = ""
    private var labelText = ""
    override val focusable get() = true

    init { w = width; h = Metrics.CONTROL_H }

    override fun preferredHeight(width: Int) = Metrics.CONTROL_H

    private fun label(): String {
        val name = get()
        if (name != labelFor) { labelFor = name; labelText = Platform.keyLabel(name) }
        return labelText
    }

    /** Called by the screen with the raw key event so the stored name is derived from the real key. */
    fun capture(event: KeyEvent): Boolean {
        if (!listening) return false
        when (event.key()) {
            Keys.ESCAPE -> {}
            Keys.BACKSPACE, Keys.DELETE -> set("")
            else -> set(Platform.keyName(event))
        }
        listening = false
        Ui.focus(null)
        return true
    }

    override fun onBlur() { listening = false }

    override fun render(mx: Int, my: Int) {
        val hv = hover.update()
        val fo = focusAnim.apply { set(listening) }.update()
        val prevAlpha = Draw.alpha
        if (!enabled) Draw.alpha = prevAlpha * Opacity.DISABLED
        Draw.round(x, y, w, h, Radius.MD, Colors.lerp(Theme.card, Theme.cardHover, hv))
        if (fo > 0.01f && Theme.glow > 0f) Draw.glow(x, y, w, h, Colors.alpha(Theme.accent, 0.3f * fo * Theme.glow), big = false)
        Draw.ring(x, y, w, h, Radius.MD, Colors.lerp(Theme.border, Theme.accent, fo))
        if (listening) {
            val pulse = if (AuroraAnimationEngine.reducedMotion) 1f else 0.6f + 0.4f * (0.5f + 0.5f * kotlin.math.sin(AuroraAnimationEngine.time * 6f))
            Draw.textCentered(I18n.tr("ui.pressKey"), x + w / 2, y + (h - 8) / 2, Colors.alpha(Theme.accent, pulse))
        } else {
            val text = label()
            if (text.isEmpty()) Draw.textCentered(I18n.tr("ui.unbound"), x + w / 2, y + (h - 8) / 2, Colors.alpha(Theme.textSecondary, 0.7f))
            else Draw.textCentered(Draw.fit(text, w - 12), x + w / 2, y + (h - 8) / 2, Theme.text)
        }
        Draw.alpha = prevAlpha
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!enabled || !contains(mx, my)) return false
        if (button == 1) { set(""); return true }
        if (button != 0) return false
        Ui.focus(this); listening = true
        return true
    }
}
