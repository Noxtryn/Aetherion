package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.common.features.FeatureDefinition
import dev.freelocs.aetherion.common.features.SettingDefinition
import dev.freelocs.aetherion.common.services.I18n
import dev.freelocs.aetherion.core.Aetherion
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.engine.Icons
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Theme

/** Builds the right control for a [SettingDefinition], bound to the live configuration. */
object SettingControls {
    fun create(def: FeatureDefinition, s: SettingDefinition): Widget {
        val cfg = Aetherion.config
        return when (s) {
            is SettingDefinition.Toggle -> AuroraToggle({ cfg.get(def, s) as Boolean }, { cfg.set(def, s, it) })
            is SettingDefinition.Slider -> AuroraSlider(s.min, s.max, s.step, s.decimals, s.suffix, s.default as Double, { cfg.get(def, s) as Double }, { cfg.set(def, s, it) })
            is SettingDefinition.Choice -> AuroraDropdown({ s.options }, { I18n.tr(s.optionKey(it)) }, { cfg.get(def, s) as String }, { cfg.set(def, s, it) })
            is SettingDefinition.Color -> AuroraColorPicker({ cfg.get(def, s) as Int }, { cfg.set(def, s, it) })
            is SettingDefinition.Keybind -> AuroraKeybindField({ cfg.get(def, s) as String }, { cfg.set(def, s, it) })
        }
    }
}

/** One labelled setting: title and optional description on the left, control and per-setting reset on the right. */
class SettingRow(private val def: FeatureDefinition, private val setting: SettingDefinition) : Widget() {
    val control: Widget = SettingControls.create(def, setting)
    private val hasDesc = setting.descriptionKey != null

    init {
        h = if (hasDesc) 36 else 30
        tooltip = { setting.descriptionKey?.let { I18n.tr(it) } }
    }

    override fun preferredHeight(width: Int) = h

    private fun modified() = Aetherion.config.get(def, setting) != setting.default
    private fun resetX() = x + w - 14
    private fun controlRight() = x + w - 22

    private fun place() {
        control.setBounds(controlRight() - control.w, y + (h - control.h) / 2, control.w, control.h)
        control.visible = visible
        control.enabled = enabled
    }

    override fun render(mx: Int, my: Int) {
        place()
        val labelW = control.x - x - 12
        Draw.text(Draw.fit(I18n.tr(setting.titleKey), labelW), x, y + (if (hasDesc) 7 else (h - 8) / 2), Theme.text)
        if (hasDesc) Draw.text(Draw.fit(I18n.tr(setting.descriptionKey!!), labelW), x, y + 19, Colors.alpha(Theme.textSecondary, 0.85f))
        control.draw(mx, my, 0, Int.MAX_VALUE)
        if (modified()) {
            val over = mx >= resetX() - 2 && mx < resetX() + 12 && my >= y + h / 2 - 7 && my < y + h / 2 + 7
            Draw.icon(Icons.RESET, resetX(), y + h / 2 - 4, 1, if (over) Theme.accent else Colors.alpha(Theme.textSecondary, 0.8f))
            if (over) Ui.requestTooltip(I18n.tr("ui.resetSetting"))
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        place()
        if (modified() && button == 0 && mx >= resetX() - 2 && mx < resetX() + 12 && my >= y && my < y + h) {
            Aetherion.config.set(def, setting, setting.default); return true
        }
        return control.mouseClicked(mx, my, button)
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int) = control.mouseReleased(mx, my, button)
    override fun mouseDragged(mx: Int, my: Int, button: Int, dx: Int, dy: Int) = control.mouseDragged(mx, my, button, dx, dy)
    override fun mouseScrolled(mx: Int, my: Int, amount: Double) = control.mouseScrolled(mx, my, amount)
    override fun keyPressed(key: Int, ctrl: Boolean, shift: Boolean) = control.keyPressed(key, ctrl, shift)
    override fun charTyped(codepoint: Int) = control.charTyped(codepoint)

    /** Keybind rows need the raw event; exposes the keybind control if this row has one. */
    val keybind: AuroraKeybindField? get() = control as? AuroraKeybindField
}
