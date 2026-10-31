package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.common.data.Roadmap
import dev.freelocs.aetherion.common.features.Capability
import dev.freelocs.aetherion.common.features.FeatureDefinition
import dev.freelocs.aetherion.common.features.FeatureRegistry
import dev.freelocs.aetherion.common.features.FeatureStatus
import dev.freelocs.aetherion.common.features.RuntimeStatus
import dev.freelocs.aetherion.common.services.I18n
import dev.freelocs.aetherion.common.services.NoticeKind
import dev.freelocs.aetherion.common.ui.UiState
import dev.freelocs.aetherion.core.Aetherion
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.engine.Icons
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Radius
import dev.freelocs.aetherion.gui.theme.Theme
import dev.freelocs.aetherion.platform.Platform
import net.fabricmc.loader.api.FabricLoader

/**
 * Right-hand panel describing the selected feature: state and why, description, dependencies in both
 * directions, runtime requirements, compatibility notes and - for roadmap entries - data source,
 * complexity, API permissions, rule assessment and acceptance criteria. Everything is derived from the
 * registry; nothing is hardcoded per feature.
 */
class FeatureDetailPanel(private val ui: UiState) {
    var x = 0; var y = 0; var w = 0; var h = 0
    private val scroll = AuroraScrollbar()
    private var lastId: String? = null

    // Buttons are drawn by this panel, which also dispatches their clicks, so their own onClick stays empty.
    private var enableLabel = ""
    private var favLabel = ""
    private var enableAction: () -> Unit = {}
    private var favAction: () -> Unit = {}
    private var resetAction: () -> Unit = {}
    private val enableButton = AuroraButton({ enableLabel }, ButtonStyle.PRIMARY) {}
    private val favButton = AuroraButton({ favLabel }, ButtonStyle.SECONDARY, Icons.STAR) {}
    private val resetButton = AuroraButton({ I18n.tr("ui.resetFeature") }, ButtonStyle.GHOST, Icons.RESET) {}

    // click targets for dependency links, rebuilt each frame (fixed capacity to avoid allocation)
    private val linkY = IntArray(16)
    private val linkId = arrayOfNulls<String>(16)
    private var links = 0

    private val mcVersion: String by lazy {
        FabricLoader.getInstance().getModContainer("minecraft").map { it.metadata.version.friendlyString }.orElse("?")
    }

    private fun yesNo(b: Boolean) = if (b) Colors.SUCCESS else Colors.WARNING

    fun render(mx: Int, my: Int) {
        val def = ui.selectedFeatureId?.let { FeatureRegistry.feature(it) }
        Draw.rect(x, y, 1, h, Colors.alpha(Theme.border, 0.7f))
        if (def == null) {
            Draw.icon(Icons.INFO, x + w / 2 - 16, y + h / 2 - 28, 4, Colors.alpha(Theme.textSecondary, 0.5f))
            val lines = TextWrap.wrap(I18n.tr("detail.empty"), w - 40)
            for ((i, l) in lines.withIndex()) Draw.textCentered(l, x + w / 2, y + h / 2 + 10 + i * 10, Theme.textSecondary)
            return
        }
        if (def.id != lastId) { lastId = def.id; scroll.reset() }
        val status = Aetherion.features.status(def)
        val pad = 16
        val cw = w - pad * 2 - 6
        links = 0
        scroll.setViewport(x, y, w, h, contentHeight)
        scroll.update()
        var cy = y + pad - scroll.offset
        val left = x + pad

        Draw.pushClip(x + 1, y, w - 1, h)
        // title
        val cat = FeatureRegistry.category(def.categoryId)
        Draw.text(Draw.fit(I18n.tr(def.titleKey), cw), left, cy, Theme.text); cy += 14
        Draw.text(Draw.fit(cat?.let { I18n.tr(it.titleKey) } ?: "", cw), left, cy, Theme.textSecondary); cy += 16
        // status
        Chip.draw(FeatureText.statusLabel(status), left, cy, FeatureText.statusColor(status)); cy += 20
        cy = paragraph(statusReason(def, status), left, cy, cw, Theme.textSecondary) + 6
        // description
        cy = heading(I18n.tr("detail.about"), left, cy, cw)
        cy = paragraph(I18n.tr(def.descriptionKey), left, cy, cw, Theme.text) + 8

        // actions
        enableButton.visible = false; favButton.visible = false; resetButton.visible = false
        if (def.status == FeatureStatus.IMPLEMENTED) {
            var bx = left
            if (def.toggleable) {
                val on = Aetherion.config.isEnabled(def)
                enableLabel = I18n.tr(if (on) "ui.disable" else "ui.enable")
                enableAction = { Aetherion.features.setEnabled(def, !on) }
                val bw = Draw.textWidth(enableLabel) + 24
                enableButton.visible = true
                enableButton.setBounds(bx, cy, bw, 22)
                bx += bw + 6
                enableButton.draw(mx, my, y, y + h)
            }
            val fav = Aetherion.config.isFavorite(def.id)
            favLabel = I18n.tr(if (fav) "ui.unfavorite" else "ui.favorite")
            favAction = { Aetherion.config.toggleFavorite(def.id) }
            val fw = Draw.textWidth(favLabel) + 26
            if (bx + fw > left + cw) { bx = left; cy += 26 }
            favButton.visible = true; favButton.setBounds(bx, cy, fw, 22); favButton.draw(mx, my, y, y + h)
            bx += fw + 6
            resetAction = { Aetherion.config.resetFeature(def); Aetherion.features.reconcile(); Ui.toast(NoticeKind.INFO, I18n.tr("toast.featureReset", I18n.tr(def.titleKey))) }
            val rw = resetButton.naturalWidth().coerceAtLeast(70)
            if (bx + rw > left + cw) { bx = left; cy += 26 }
            resetButton.visible = true; resetButton.setBounds(bx, cy, rw, 22); resetButton.enabled = Aetherion.config.isModified(def); resetButton.draw(mx, my, y, y + h)
            cy += 32
        }

        // dependencies
        if (def.dependencies.isNotEmpty()) {
            cy = heading(I18n.tr("detail.dependencies"), left, cy, cw)
            for (d in def.dependencies) cy = depRow(d, left, cy, cw, mx, my)
            cy += 6
        }
        val dependants = FeatureRegistry.dependantsOf(def.id)
        if (dependants.isNotEmpty()) {
            cy = heading(I18n.tr("detail.requiredBy"), left, cy, cw)
            for (d in dependants) cy = depRow(d.id, left, cy, cw, mx, my)
            cy += 6
        }
        // requirements
        cy = heading(I18n.tr("detail.requirements"), left, cy, cw)
        for (c in def.capabilities) {
            val ok = Platform.isAvailable(c) || c == Capability.CLIENT_ONLY
            Draw.icon(if (ok) Icons.CHECK else Icons.CROSS, left, cy, 1, yesNo(ok))
            Draw.text(Draw.fit(I18n.tr("capability.${c.name.lowercase()}"), cw - 14), left + 14, cy, Theme.text)
            cy += 13
        }
        cy += 6
        // compatibility
        cy = heading(I18n.tr("detail.compatibility"), left, cy, cw)
        val compat = Roadmap.entry(def.id)?.compatibility ?: def.compatibilityKey?.let { I18n.tr(it) } ?: I18n.tr("compat.default")
        cy = paragraph(compat, left, cy, cw, Theme.textSecondary)
        cy = paragraph(I18n.tr("compat.build", mcVersion, Aetherion.version), left, cy, cw, Colors.alpha(Theme.textSecondary, 0.7f)) + 6

        // roadmap facts
        val rm = Roadmap.entry(def.id)
        if (rm != null) {
            cy = heading(I18n.tr("detail.roadmap"), left, cy, cw)
            cy = kv(I18n.tr("detail.dataSource"), I18n.tr("roadmap.source.${rm.dataSource}"), left, cy, cw)
            cy = kv(I18n.tr("detail.complexity"), I18n.tr("roadmap.level.${rm.complexity}"), left, cy, cw)
            cy = kv(I18n.tr("detail.utility"), I18n.tr("roadmap.level.${rm.utility}"), left, cy, cw)
            cy = kv(I18n.tr("detail.milestone"), rm.milestone, left, cy, cw)
            cy = kv(I18n.tr("detail.rule"), I18n.tr("roadmap.rule.${rm.rule}"), left, cy, cw, if (rm.rule == "review") Colors.WARNING else Theme.text)
            cy = paragraph(I18n.tr("detail.apiPermission") + ": " + rm.apiPermission, left, cy + 2, cw, Theme.textSecondary) + 4
            cy = heading(I18n.tr("detail.acceptance"), left, cy, cw)
            for (a in rm.acceptance) {
                Draw.rect(left, cy + 3, 3, 3, Theme.accent)
                cy = paragraph(a, left + 9, cy, cw - 9, Theme.textSecondary)
            }
            cy += 4
        } else if (def.settings.isNotEmpty()) {
            cy = heading(I18n.tr("detail.settings"), left, cy, cw)
            cy = paragraph(I18n.tr("detail.settingsHint", def.settings.size), left, cy, cw, Theme.textSecondary) + 4
        }
        contentHeight = cy - (y + pad - scroll.offset) + pad
        Draw.popClip()
        scroll.render(mx, my)
    }

    private var contentHeight = 0

    private fun statusReason(def: FeatureDefinition, s: RuntimeStatus): String = when (s) {
        RuntimeStatus.ACTIVE -> I18n.tr("reason.active")
        RuntimeStatus.DISABLED -> I18n.tr("reason.disabled")
        RuntimeStatus.BLOCKED_BY_DEPENDENCY -> I18n.tr("reason.blocked", def.dependencies.filter { !Aetherion.features.isActive(it) }.joinToString { FeatureRegistry.feature(it)?.let { f -> I18n.tr(f.titleKey) } ?: it })
        RuntimeStatus.AWAITING_CAPABILITY -> I18n.tr("reason.capability", Aetherion.features.missingCapabilities(def).joinToString { I18n.tr("capability.${it.name.lowercase()}") })
        RuntimeStatus.PLANNED -> I18n.tr("reason.planned")
        RuntimeStatus.REVIEW_REQUIRED -> I18n.tr("reason.review")
        RuntimeStatus.FAILED -> I18n.tr("reason.failed")
    }

    private fun heading(text: String, x: Int, y: Int, w: Int): Int {
        Draw.text(text.uppercase(), x, y + 4, Colors.alpha(Theme.textSecondary, 0.9f))
        Draw.rect(x, y + 14, w, 1, Colors.alpha(Theme.border, 0.7f))
        return y + 21
    }

    private fun paragraph(text: String, x: Int, y: Int, w: Int, color: Int): Int {
        var yy = y
        for (l in TextWrap.wrap(text, w)) { Draw.text(l, x, yy, color); yy += 10 }
        return yy
    }

    private fun kv(k: String, v: String, x: Int, y: Int, w: Int, color: Int = Theme.text): Int {
        Draw.text(k, x, y, Theme.textSecondary)
        Draw.textRight(Draw.fit(v, w - Draw.textWidth(k) - 10), x + w, y, color)
        return y + 13
    }

    private fun depRow(id: String, x: Int, y: Int, w: Int, mx: Int, my: Int): Int {
        val d = FeatureRegistry.feature(id) ?: return y
        val st = Aetherion.features.status(d)
        val over = mx >= x && mx < x + w && my >= y - 2 && my < y + 12
        Draw.round(x, y + 1, 6, 6, 3, FeatureText.statusColor(st))
        Draw.text(Draw.fit(I18n.tr(d.titleKey), w - 16), x + 12, y, if (over) Theme.accent else Theme.text)
        if (links < linkId.size) { linkY[links] = y; linkId[links] = id; links++ }
        return y + 13
    }

    fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false
        if (scroll.mouseClicked(mx, my, button)) return true
        if (button != 0) return true
        if (enableButton.contains(mx, my) && selectedToggleable()) { enableAction(); return true }
        if (favButton.contains(mx, my)) { favAction(); return true }
        if (resetButton.contains(mx, my) && resetButton.enabled) { resetAction(); return true }
        for (i in 0 until links) {
            if (my >= linkY[i] - 2 && my < linkY[i] + 12) {
                FeatureRegistry.feature(linkId[i]!!)?.let { ui.openFeature(it.categoryId, it.id) }
                return true
            }
        }
        return true
    }

    fun mouseDragged(my: Int) = scroll.mouseDragged(my)
    fun mouseReleased() = scroll.mouseReleased()
    fun mouseScrolled(mx: Int, my: Int, amount: Double): Boolean {
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false
        scroll.wheel(amount); return true
    }

    private fun selectedToggleable() = ui.selectedFeatureId?.let { FeatureRegistry.feature(it) }?.let { it.toggleable && it.status == FeatureStatus.IMPLEMENTED } == true
}
