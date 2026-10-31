package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.common.features.FeatureRegistry
import dev.freelocs.aetherion.common.services.I18n
import dev.freelocs.aetherion.common.services.Notifications
import dev.freelocs.aetherion.common.ui.UiState
import dev.freelocs.aetherion.core.Aetherion
import dev.freelocs.aetherion.gui.animations.AuroraAnimationEngine
import dev.freelocs.aetherion.gui.animations.StateAnim
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.engine.Icons
import dev.freelocs.aetherion.gui.theme.AccentPreset
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Radius
import dev.freelocs.aetherion.gui.theme.Theme
import dev.freelocs.aetherion.platform.Platform

/**
 * Top bar: current category, search, profile/context display, notification bell and theme controls
 * (accent swatches and the Aurora/Midnight surface variant). Elements drop out as the width shrinks.
 */
class AuroraHeader(private val ui: UiState, private val onBell: () -> Unit) {
    var x = 0; var y = 0; var w = 0; var h = 0

    val search = AuroraSearchField({ I18n.tr("ui.search") }) { ui.setSearch(it) }
    private val profile = AuroraDropdown(
        { Aetherion.config.profileNames },
        { it },
        { Aetherion.config.activeProfile },
        { Aetherion.config.switchProfile(it); Ui.toast(dev.freelocs.aetherion.common.services.NoticeKind.INFO, I18n.tr("toast.profileSwitched", it)) },
        width = 118
    )
    private val bellHover = StateAnim()
    private val variantHover = StateAnim()
    private val dotAnim = Array(AccentPreset.entries.size) { StateAnim() }
    private var quotedFor = ""
    private var quoted = ""
    private var contextText = ""
    private var contextAt = -10f

    private val showTheme get() = w >= 560
    private val showContext get() = w >= 470
    private val showProfile get() = w >= 400

    private fun refreshContext() {
        if (AuroraAnimationEngine.time - contextAt < 0.5f) return
        contextAt = AuroraAnimationEngine.time
        contextText = I18n.tr(when {
            Platform.isOnHypixel() -> "context.hypixel"
            Platform.mc.level != null -> "context.world"
            else -> "context.menu"
        })
    }

    private var bellX = 0
    private var themeX = 0
    private var dotsX = 0

    fun layout() {
        var right = x + w - 16
        if (showTheme) { right -= 74; themeX = right; dotsX = themeX; right -= 8 }
        bellX = right - 22
        right = bellX - 8
        if (showProfile) { profile.setBounds(right - profile.w, y + (h - 22) / 2, profile.w, 22); right = profile.x - 8 }
        refreshContext()
        if (showContext) right -= Chip.width(contextText) + 8
        val sw = (right - (x + (w * 0.34f).toInt())).coerceIn(110, 220)
        search.setBounds(right - sw, y + (h - 22) / 2, sw, 22)
    }

    fun render(mx: Int, my: Int) {
        layout()
        val cat = FeatureRegistry.category(ui.categoryId)
        val titleW = search.x - x - 16 - 12
        val title = if (ui.searching) I18n.tr("ui.searchResults") else cat?.let { I18n.tr(it.titleKey) } ?: ""
        if (ui.search != quotedFor) { quotedFor = ui.search; quoted = "\"" + ui.search.trim() + "\"" }
        val sub = if (ui.searching) quoted else cat?.let { I18n.tr(it.descriptionKey) } ?: ""
        Draw.text(Draw.fit(title, titleW), x + 16, y + if (titleW > 150) 14 else 22, Theme.text)
        if (titleW > 150) Draw.text(Draw.fit(sub, titleW), x + 16, y + 28, Theme.textSecondary)

        search.draw(mx, my, y, y + h)
        if (showContext) {
            val cx = search.x + search.w + 8
            val color = if (Platform.isOnHypixel()) Colors.SUCCESS else Theme.textSecondary
            Chip.draw(contextText, cx, y + (h - 14) / 2, color)
        }
        if (showProfile) {
            profile.draw(mx, my, y, y + h)
        }

        // bell
        val bOver = mx >= bellX && mx < bellX + 22 && my >= y + (h - 22) / 2 && my < y + (h + 22) / 2
        bellHover.set(bOver); val bh = bellHover.update()
        Draw.round(bellX, y + (h - 22) / 2, 22, 22, Radius.MD, Colors.alpha(Theme.text, 0.05f + 0.07f * bh))
        val unread = Notifications.unread
        val shake = if (unread > 0 && !AuroraAnimationEngine.reducedMotion) (kotlin.math.sin(AuroraAnimationEngine.time * 9f) * 0.6f).toInt() else 0
        Draw.icon(Icons.BELL, bellX + 7 + shake, y + (h - 8) / 2, 1, if (unread > 0) Theme.accent else Colors.lerp(Theme.textSecondary, Theme.text, bh))
        if (unread > 0) {
            val t = if (unread > 9) "9+" else "$unread"
            val bw = Draw.textWidth(t) + 6
            Draw.round(bellX + 22 - bw + 2, y + (h - 22) / 2 - 2, bw, 11, 5, Theme.accent)
            Draw.text(t, bellX + 22 - bw + 5, y + (h - 22) / 2, 0xFF0B1020.toInt())
        }
        if (bOver) Ui.requestTooltip(I18n.tr("ui.notifications"))

        if (showTheme) renderTheme(mx, my)
        Draw.rect(x, y + h - 1, w, 1, Colors.alpha(Theme.border, 0.7f))
    }

    private fun renderTheme(mx: Int, my: Int) {
        val cfg = Aetherion.config
        val current = cfg.string("cosmetics.theme", "accent")
        val cy = y + (h - 12) / 2
        for ((i, preset) in AccentPreset.entries.withIndex()) {
            val dx = dotsX + i * 18
            val over = mx >= dx - 2 && mx < dx + 14 && my >= cy - 2 && my < cy + 14
            dotAnim[i].set(current == preset.id || over)
            val a = dotAnim[i].update()
            val size = 12
            if (current == preset.id) Draw.ring(dx - 3, cy - 3, size + 6, size + 6, 9, Colors.alpha(preset.primary, 0.8f))
            Draw.round(dx, cy, size, size, 6, Colors.lerp(Colors.alpha(preset.primary, 0.75f), preset.primary, a))
            if (over) Ui.requestTooltip(I18n.tr("option.accent.${preset.id}"))
        }
        val vx = dotsX + AccentPreset.entries.size * 18 + 4
        val vOver = mx >= vx - 2 && mx < vx + 14 && my >= cy - 4 && my < cy + 16
        variantHover.set(vOver); val vh = variantHover.update()
        Draw.icon(Icons.COSMETICS, vx, cy + 2, 1, Colors.lerp(Theme.textSecondary, Theme.text, vh))
        if (vOver) Ui.requestTooltip(I18n.tr("ui.themeVariant"))
    }

    fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false
        if (search.mouseClicked(mx, my, button)) return true
        if (showProfile && profile.mouseClicked(mx, my, button)) return true
        if (button != 0) return true
        if (mx >= bellX && mx < bellX + 22) { onBell(); return true }
        if (showTheme) {
            val cy = y + (h - 12) / 2
            for ((i, preset) in AccentPreset.entries.withIndex()) {
                val dx = dotsX + i * 18
                if (mx >= dx - 2 && mx < dx + 14 && my >= cy - 2 && my < cy + 14) {
                    val def = FeatureRegistry.feature("cosmetics.theme")!!
                    Aetherion.config.set(def, def.setting("accent")!!, preset.id)
                    return true
                }
            }
            val vx = dotsX + AccentPreset.entries.size * 18 + 4
            if (mx >= vx - 2 && mx < vx + 14) {
                val def = FeatureRegistry.feature("cosmetics.theme")!!
                val s = def.setting("variant")!!
                Aetherion.config.set(def, s, if (Aetherion.config.string("cosmetics.theme", "variant") == "aurora") "midnight" else "aurora")
                return true
            }
        }
        return true
    }

    val profileDropdown get() = profile

    internal fun debugBellCenter() = intArrayOf(bellX + 11, y + h / 2)
}
