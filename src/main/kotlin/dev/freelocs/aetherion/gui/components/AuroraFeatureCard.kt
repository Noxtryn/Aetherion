package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.common.data.Roadmap
import dev.freelocs.aetherion.common.features.FeatureDefinition
import dev.freelocs.aetherion.common.features.FeatureRegistry
import dev.freelocs.aetherion.common.features.FeatureStatus
import dev.freelocs.aetherion.common.features.RuntimeStatus
import dev.freelocs.aetherion.common.services.I18n
import dev.freelocs.aetherion.common.ui.UiState
import dev.freelocs.aetherion.core.Aetherion
import dev.freelocs.aetherion.gui.animations.Spring
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.engine.Icons
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Radius
import dev.freelocs.aetherion.gui.theme.Theme
import kotlin.math.roundToInt

/** Display helpers shared by the card and the detail panel. */
object FeatureText {
    private val statusKeys = RuntimeStatus.entries.map { "status." + it.name.lowercase() }.toTypedArray()

    fun statusLabel(s: RuntimeStatus) = I18n.tr(statusKeys[s.ordinal])

    fun statusColor(s: RuntimeStatus): Int = when (s) {
        RuntimeStatus.ACTIVE -> Colors.SUCCESS
        RuntimeStatus.DISABLED -> Theme.textSecondary
        RuntimeStatus.BLOCKED_BY_DEPENDENCY, RuntimeStatus.AWAITING_CAPABILITY, RuntimeStatus.REVIEW_REQUIRED -> Colors.WARNING
        RuntimeStatus.PLANNED -> Colors.ICE
        RuntimeStatus.FAILED -> Colors.DANGER
    }
}

/**
 * Overview card for one feature: icon tile, title/description, status chip, favourite star, enable toggle
 * and an expand arrow that animates open to reveal the feature's settings (or, for planned entries, the
 * roadmap facts). Clicking the card selects it for the detail panel.
 */
class AuroraFeatureCard(private val def: FeatureDefinition, private val ui: UiState) : AuroraCard() {
    private companion object { const val HEADER_H = 52 }

    private val expand = Spring(if (def.id in ui.expanded) 1f else 0f, 320f, 30f)
    private val rows: List<SettingRow> = def.settings.map { SettingRow(def, it) }
    private val toggle: AuroraToggle? = if (def.toggleable && def.status == FeatureStatus.IMPLEMENTED) {
        AuroraToggle({ Aetherion.config.isEnabled(def) }, { Aetherion.features.setEnabled(def, it) })
    } else null
    private val resetButton = AuroraButton({ I18n.tr("ui.resetFeature") }, ButtonStyle.GHOST, Icons.RESET) {
        Aetherion.config.resetFeature(def); Aetherion.features.reconcile(); Ui.toast(dev.freelocs.aetherion.common.services.NoticeKind.INFO, I18n.tr("toast.featureReset", I18n.tr(def.titleKey)))
    }
    private val planned = def.status != FeatureStatus.IMPLEMENTED

    private fun roadmapRows(): List<Pair<String, String>> {
        val e = Roadmap.entry(def.id) ?: return emptyList()
        return listOf(
            I18n.tr("detail.dataSource") to I18n.tr("roadmap.source.${e.dataSource}"),
            I18n.tr("detail.complexity") to I18n.tr("roadmap.level.${e.complexity}"),
            I18n.tr("detail.utility") to I18n.tr("roadmap.level.${e.utility}"),
            I18n.tr("detail.rule") to I18n.tr("roadmap.rule.${e.rule}"),
            I18n.tr("detail.milestone") to e.milestone
        )
    }

    private val bodyH: Int get() = if (planned) roadmapRows().size * 16 + 18 else rows.sumOf { it.h } + 14 + (if (def.settings.isNotEmpty()) 28 else 0)
    private val expandable get() = if (planned) true else def.settings.isNotEmpty()
    val isExpanded get() = def.id in ui.expanded

    init {
        h = HEADER_H
        tooltip = null
    }

    override fun preferredHeight(width: Int): Int {
        expand.target = if (isExpanded && expandable) 1f else 0f
        expand.update()
        h = HEADER_H + (bodyH * expand.value.coerceIn(0f, 1f)).roundToInt()
        return h
    }

    private fun starX() = (toggle?.let { x + w - 36 - 14 - 24 } ?: (x + w - 14 - 24))
    private fun toggleX() = x + w - 12 - AuroraToggle.W - 10
    private fun chevronX() = x + w - 20

    private fun layoutBody() {
        if (planned) return
        var ry = y + HEADER_H + 4
        for (r in rows) {
            r.setBounds(x + 16, ry, w - 32, r.h)
            ry += r.h
        }
        resetButton.setBounds(x + w - 16 - resetButton.naturalWidth().coerceAtLeast(70), ry + 6, resetButton.naturalWidth().coerceAtLeast(70), 20)
    }

    override fun render(mx: Int, my: Int) {
        preferredHeight(w)
        val selected = ui.selectedFeatureId == def.id
        val lift = surface(selected)
        val top = y - lift
        val status = Aetherion.features.status(def)
        val enabledNow = status == RuntimeStatus.ACTIVE

        // icon tile
        val cat = FeatureRegistry.category(def.categoryId)
        val tile = 28 + (hover.update() * 2).roundToInt()
        Draw.round(x + 12 - (tile - 28) / 2, top + 12 - (tile - 28) / 2, tile, tile, Radius.MD, Colors.alpha(if (enabledNow) Theme.accent else Theme.textSecondary, if (enabledNow) 0.18f else 0.10f))
        Draw.icon(Icons.byName(cat?.icon ?: "info"), x + 12 + 6, top + 12 + 6, 2, if (enabledNow) Theme.accent else Theme.textSecondary)

        // right cluster
        var right = x + w - 12
        if (expandable) {
            Draw.icon(Icons.CHEVRON_RIGHT, chevronX(), top + HEADER_H / 2 - 4, 1, Colors.lerp(Theme.textSecondary, Theme.text, hover.update()))
            right -= 18
        }
        toggle?.let {
            val tx = right - AuroraToggle.W
            it.setBounds(tx, top + (HEADER_H - AuroraToggle.H) / 2, AuroraToggle.W, AuroraToggle.H)
            it.draw(mx, my, y, Int.MAX_VALUE)
            right = tx - 10
        }
        if (!planned) {
            val fav = Aetherion.config.isFavorite(def.id)
            val over = mx >= right - 12 && mx < right + 2 && my >= top + HEADER_H / 2 - 8 && my < top + HEADER_H / 2 + 8
            Draw.icon(if (fav) Icons.STAR else Icons.STAR_OUTLINE, right - 10, top + HEADER_H / 2 - 4, 1, if (fav) Colors.WARNING else if (over) Theme.text else Theme.textSecondary)
            if (over) Ui.requestTooltip(I18n.tr(if (fav) "ui.unfavorite" else "ui.favorite"))
            right -= 18
        }
        val label = FeatureText.statusLabel(status)
        val left = Chip.drawRight(label, right, top + HEADER_H / 2 - 7, FeatureText.statusColor(status))

        // title + description
        val textX = x + 12 + 28 + 10
        val textW = left - textX - 10
        Draw.text(Draw.fit(I18n.tr(def.titleKey), textW), textX, top + 14, Theme.text)
        Draw.text(Draw.fit(I18n.tr(def.descriptionKey), textW), textX, top + 28, Theme.textSecondary)

        // body
        val ev = expand.value.coerceIn(0f, 1f)
        if (ev > 0.01f) {
            val visibleH = h - HEADER_H
            Draw.pushClip(x, top + HEADER_H, w, visibleH)
            val prev = Draw.alpha
            Draw.alpha = prev * ev
            Draw.rect(x + 12, top + HEADER_H - 1, w - 24, 1, Colors.alpha(Theme.border, 0.8f))
            if (planned) {
                var ry = top + HEADER_H + 8
                for ((k, v) in roadmapRows()) {
                    Draw.text(k, x + 16, ry, Theme.textSecondary)
                    Draw.textRight(v, x + w - 16, ry, Theme.text)
                    ry += 16
                }
            } else {
                layoutBody()
                val clipTop = y + HEADER_H
                val clipBottom = y + h
                for (r in rows) r.draw(mx, my, clipTop, clipBottom)
                if (def.settings.isNotEmpty()) {
                    resetButton.enabled = Aetherion.config.isModified(def)
                    resetButton.draw(mx, my, clipTop, clipBottom)
                }
            }
            Draw.alpha = prev
            Draw.popClip()
        }
    }

    private fun bodyInteractive() = expand.value > 0.9f

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!contains(mx, my)) return false
        if (my < y + HEADER_H) {
            if (toggle != null && toggle.mouseClicked(mx, my, button)) return true
            if (!planned && button == 0 && mx >= starX() - 4 && mx < starX() + 16) { Aetherion.config.toggleFavorite(def.id); return true }
            if (button == 0) {
                ui.selectFeature(def.id)
                if (expandable) ui.toggleExpanded(def.id)
            }
            return true
        }
        ui.selectFeature(def.id)
        if (!bodyInteractive() || planned) return true
        layoutBody()
        for (r in rows) if (r.mouseClicked(mx, my, button)) return true
        if (def.settings.isNotEmpty() && resetButton.mouseClicked(mx, my, button)) return true
        return true
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int): Boolean {
        var used = false
        toggle?.let { used = it.mouseReleased(mx, my, button) || used }
        for (r in rows) used = r.mouseReleased(mx, my, button) || used
        used = resetButton.mouseReleased(mx, my, button) || used
        return used
    }

    override fun mouseDragged(mx: Int, my: Int, button: Int, dx: Int, dy: Int): Boolean {
        for (r in rows) if (r.mouseDragged(mx, my, button, dx, dy)) return true
        return false
    }

    override fun mouseScrolled(mx: Int, my: Int, amount: Double): Boolean {
        for (r in rows) if (r.mouseScrolled(mx, my, amount)) return true
        return false
    }

    /** Controls reachable with Tab: the enable toggle and, while expanded, every setting control. */
    fun collectFocusables(out: MutableList<Widget>) {
        toggle?.let { out += it }
        if (isExpanded && !planned) for (r in rows) if (r.control.focusable) out += r.control
    }

    /** The keybind control inside this card, if any (needed to deliver raw key events while listening). */
    fun keybindRows(): List<AuroraKeybindField> = rows.mapNotNull { it.keybind }

    val definition get() = def

    /** Test access: centre of the enable toggle and the setting controls, in UI coordinates. */
    internal fun debugToggleCenter(): IntArray? = toggle?.let { intArrayOf(it.x + it.w / 2, it.y + it.h / 2) }
    internal fun debugControls(): List<Widget> = rows.map { it.control }
}
