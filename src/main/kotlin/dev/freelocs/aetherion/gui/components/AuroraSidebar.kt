package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.common.features.FeatureRegistry
import dev.freelocs.aetherion.common.features.FeatureStatus
import dev.freelocs.aetherion.common.services.I18n
import dev.freelocs.aetherion.common.ui.UiState
import dev.freelocs.aetherion.core.Aetherion
import dev.freelocs.aetherion.gui.animations.AuroraAnimationEngine
import dev.freelocs.aetherion.gui.animations.Spring
import dev.freelocs.aetherion.gui.animations.StateAnim
import dev.freelocs.aetherion.gui.animations.Tween
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.engine.Icons
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Radius
import dev.freelocs.aetherion.gui.theme.Theme
import kotlin.math.roundToInt

/**
 * Left navigation: logo/wordmark, collapsible groups of categories, favourite features and footer
 * shortcuts. A single indicator pill glides (spring) between rows, so switching category - even while a
 * previous move is still in flight - always looks continuous.
 */
class AuroraSidebar(private val ui: UiState) {
    var x = 0; var y = 0; var w = 0; var h = 0
    var compact = false

    private enum class Kind { GROUP, CATEGORY, FAV_HEADER, FAVORITE }

    private class Row(val kind: Kind, val id: String, val groupId: String?) {
        val hover = StateAnim()
        var top = 0
        var height = 0
        var active = 0
        var interactive = false
    }

    private val rowCache = HashMap<String, Row>()
    private var rows = ArrayList<Row>()
    private var signature = Int.MIN_VALUE
    private val groupOpen = HashMap<String, Spring>()
    private val scroll = AuroraScrollbar()
    private val indY = Spring(0f, 360f, 32f)
    private val indH = Spring(30f, 360f, 32f)
    private val indAlpha = Tween(0f, 0.18f)
    private var indInit = false
    private var lastCounts = -10f
    private val settingsHover = StateAnim()
    private val collapseHover = StateAnim()

    private companion object {
        const val LOGO_H = 56
        const val FOOTER_H = 58
        const val ROW_H = 30
        const val GROUP_H = 22
        const val FAV_H = 26
    }

    private fun key(kind: Kind, id: String) = "${kind.name}:$id"

    private fun rebuild() {
        val old = rowCache
        val out = ArrayList<Row>()
        fun row(kind: Kind, id: String, group: String?) = old.getOrPut(key(kind, id)) { Row(kind, id, group) }
        for (g in FeatureRegistry.groups) {
            out += row(Kind.GROUP, g.id, null)
            for (c in FeatureRegistry.categoriesIn(g.id)) out += row(Kind.CATEGORY, c.id, g.id)
        }
        if (!compact) {
            val favs = Aetherion.config.favorites.filter { FeatureRegistry.feature(it) != null }
            if (favs.isNotEmpty()) {
                out += row(Kind.FAV_HEADER, "favorites", null)
                for (f in favs) out += row(Kind.FAVORITE, f, null)
            }
        }
        rows = out
    }

    private val counts = HashMap<String, IntArray>() // category -> [active, implemented]

    private fun refreshCounts() {
        counts.clear()
        for (f in FeatureRegistry.features) {
            if (f.status != FeatureStatus.IMPLEMENTED) continue
            val c = counts.getOrPut(f.categoryId) { IntArray(2) }
            c[1]++
            if (f.toggleable && Aetherion.config.isEnabled(f)) c[0]++
        }
    }

    private fun layout(): Int {
        var yy = 0
        for (r in rows) {
            when (r.kind) {
                Kind.GROUP -> { r.height = if (compact) 8 else GROUP_H; r.interactive = !compact }
                Kind.CATEGORY -> {
                    val open = groupOpen.getOrPut(r.groupId!!) { Spring(if (r.groupId in ui.collapsedGroups) 0f else 1f, 300f, 30f) }
                    open.target = if (compact || r.groupId !in ui.collapsedGroups) 1f else 0f
                    val f = open.update().coerceIn(0f, 1f)
                    r.height = (ROW_H * f).roundToInt(); r.interactive = f > 0.9f
                }
                Kind.FAV_HEADER -> { r.height = GROUP_H; r.interactive = false }
                Kind.FAVORITE -> { r.height = FAV_H; r.interactive = true }
            }
            r.top = yy
            yy += r.height
        }
        return yy
    }

    fun render(mx: Int, my: Int) {
        val sig = Aetherion.config.favoritesVersion * 31 + ui.collapsedGroups.hashCode() * 17 + I18n.language.hashCode() + (if (compact) 7 else 0)
        if (sig != signature) { signature = sig; rebuild() }
        if (AuroraAnimationEngine.time - lastCounts > 0.5f) { lastCounts = AuroraAnimationEngine.time; refreshCounts() }

        Draw.round(x, y, w, h, Radius.XL, Theme.sidebar)
        // square off the inner (right) edge so it meets the content area seamlessly
        Draw.rect(x + w - Radius.XL, y, Radius.XL, h, Theme.sidebar)
        Draw.rect(x + w - 1, y, 1, h, Colors.alpha(Theme.border, 0.7f))

        renderLogo()

        val listY = y + LOGO_H
        val listH = h - LOGO_H - FOOTER_H
        val contentH = layout()
        scroll.setViewport(x, listY, w, listH, contentH + 8)
        scroll.update()
        val off = scroll.offset

        // indicator target
        var activeRow: Row? = null
        for (r in rows) {
            val a = when (r.kind) {
                Kind.CATEGORY -> !ui.searching && r.id == ui.categoryId
                else -> false
            }
            r.active = if (a || (r.kind == Kind.FAVORITE && !ui.searching && r.id == ui.selectedFeatureId)) 1 else 0
            if (a) activeRow = r
        }
        if (activeRow != null) {
            indY.target = activeRow.top.toFloat(); indH.target = activeRow.height.toFloat().coerceAtLeast(20f)
            if (!indInit) { indY.snap(activeRow.top.toFloat()); indH.snap(activeRow.height.toFloat()); indInit = true }
            indAlpha.animateTo(1f)
        } else indAlpha.animateTo(0f)
        indY.update(); indH.update(); indAlpha.update()

        Draw.pushClip(x, listY, w, listH)
        val rowX = x + 8
        val rowW = w - 16
        if (indAlpha.value > 0.01f) {
            val iy = listY + indY.value.roundToInt() - off + 1
            val ih = indH.value.roundToInt() - 3
            val prev = Draw.alpha
            Draw.alpha = prev * indAlpha.value
            if (Theme.glow > 0f) Draw.glow(rowX, iy, rowW, ih, Colors.alpha(Theme.accent, 0.22f * Theme.glow), big = false)
            Draw.round(rowX, iy, rowW, ih, Radius.MD, Colors.alpha(Theme.accent, 0.14f))
            Draw.round(rowX, iy + 5, 3, ih - 10, 1, Theme.accent)
            Draw.alpha = prev
        }
        for (r in rows) {
            val ry = listY + r.top - off
            if (r.height <= 0 || ry + r.height < listY || ry > listY + listH) continue
            val over = r.interactive && mx >= x && mx < x + w && my >= ry && my < ry + r.height && my >= listY && my < listY + listH
            r.hover.set(over)
            val hv = r.hover.update()
            val prev = Draw.alpha
            when (r.kind) {
                Kind.GROUP -> {
                    if (compact) Draw.rect(x + 16, ry + 3, w - 32, 1, Colors.alpha(Theme.border, 0.8f))
                    else {
                        val open = groupOpen[r.id]?.value ?: 1f
                        Draw.icon(if (open > 0.5f) Icons.CHEVRON_DOWN else Icons.CHEVRON_RIGHT, rowX + 4, ry + 7, 1, Colors.lerp(Theme.textSecondary, Theme.text, hv))
                        Draw.text(I18n.tr(FeatureRegistry.group(r.id)!!.titleKey).uppercase(), rowX + 16, ry + 7, Colors.alpha(Theme.textSecondary, 0.9f))
                    }
                }
                Kind.FAV_HEADER -> {
                    Draw.icon(Icons.STAR, rowX + 3, ry + 7, 1, Colors.WARNING)
                    Draw.text(I18n.tr("nav.favorites").uppercase(), rowX + 16, ry + 7, Colors.alpha(Theme.textSecondary, 0.9f))
                }
                Kind.CATEGORY -> {
                    val groupF = (r.height / ROW_H.toFloat()).coerceIn(0f, 1f)
                    Draw.alpha = prev * groupF * groupF
                    val cat = FeatureRegistry.category(r.id)!!
                    if (hv > 0.01f && r.active == 0) Draw.round(rowX, ry + 1, rowW, r.height - 3, Radius.MD, Colors.alpha(Theme.text, 0.05f * hv))
                    val tint = if (r.active == 1) Theme.accent else Colors.lerp(Theme.textSecondary, Theme.text, hv)
                    if (compact) {
                        Draw.icon(Icons.byName(cat.icon), x + (w - 16) / 2, ry + (r.height - 16) / 2, 2, tint)
                        if (over) Ui.requestTooltip(I18n.tr(cat.titleKey))
                    } else {
                        Draw.icon(Icons.byName(cat.icon), rowX + 10, ry + (r.height - 16) / 2, 2, tint)
                        val labelColor = if (r.active == 1) Theme.text else Colors.lerp(Theme.textSecondary, Theme.text, hv * 0.8f)
                        val c = counts[r.id]
                        val chipW = if (c != null && c[0] > 0) Draw.textWidth("${c[0]}") + 10 else 0
                        Draw.text(Draw.fit(I18n.tr(cat.titleKey), rowW - 40 - chipW - 8), rowX + 34, ry + (r.height - 8) / 2, labelColor)
                        if (c != null && c[0] > 0) Chip.draw("${c[0]}", rowX + rowW - chipW - 6, ry + (r.height - 14) / 2, Colors.SUCCESS)
                    }
                    Draw.alpha = prev
                }
                Kind.FAVORITE -> {
                    val def = FeatureRegistry.feature(r.id)!!
                    if (hv > 0.01f && r.active == 0) Draw.round(rowX, ry + 1, rowW, r.height - 2, Radius.MD, Colors.alpha(Theme.text, 0.05f * hv))
                    Draw.icon(Icons.STAR, rowX + 10, ry + (r.height - 8) / 2, 1, Colors.WARNING)
                    Draw.text(Draw.fit(I18n.tr(def.titleKey), rowW - 36), rowX + 26, ry + (r.height - 8) / 2, if (r.active == 1) Theme.text else Colors.lerp(Theme.textSecondary, Theme.text, hv))
                }
            }
        }
        Draw.popClip()
        scroll.render(mx, my)

        renderFooter(mx, my)
    }

    private fun renderLogo() {
        val lx = x + (if (compact) (w - 24) / 2 else 16)
        Draw.logo(lx, y + 16, 24)
        if (!compact) {
            // wordmark: two-tone, drawn at 2x for presence
            val p = Draw.g.pose()
            p.pushMatrix(); p.translate((lx + 32).toFloat(), (y + 19).toFloat()); p.scale(1.6f, 1.6f)
            Draw.text("Aether", 0, 0, Theme.text)
            Draw.text("ion", Draw.textWidth("Aether"), 0, Theme.accent)
            p.popMatrix()
        }
        Draw.rect(x + 14, y + LOGO_H - 1, w - 28, 1, Colors.alpha(Theme.border, 0.7f))
    }

    private fun footerY() = y + h - FOOTER_H

    private fun renderFooter(mx: Int, my: Int) {
        val fy = footerY()
        Draw.rect(x + 14, fy, w - 28, 1, Colors.alpha(Theme.border, 0.7f))
        val rowX = x + 8; val rowW = w - 16
        val sOver = mx >= rowX && mx < rowX + rowW && my >= fy + 6 && my < fy + 30
        settingsHover.set(sOver); val sh = settingsHover.update()
        val active = ui.categoryId == "general" && !ui.searching && ui.selectedFeatureId == null
        if (sh > 0.01f) Draw.round(rowX, fy + 6, rowW, 24, Radius.MD, Colors.alpha(Theme.text, 0.05f * sh))
        Draw.icon(Icons.GEAR, if (compact) x + (w - 16) / 2 else rowX + 10, fy + 10, 2, if (active) Theme.accent else Colors.lerp(Theme.textSecondary, Theme.text, sh))
        if (!compact) Draw.text(I18n.tr("nav.settings"), rowX + 34, fy + 14, Colors.lerp(Theme.textSecondary, Theme.text, sh))
        else if (sOver) Ui.requestTooltip(I18n.tr("nav.settings"))

        val cOver = mx >= rowX && mx < rowX + rowW && my >= fy + 32 && my < fy + 54
        collapseHover.set(cOver); val ch = collapseHover.update()
        if (ch > 0.01f) Draw.round(rowX, fy + 32, rowW, 22, Radius.MD, Colors.alpha(Theme.text, 0.05f * ch))
        Draw.icon(Icons.MENU, if (compact) x + (w - 8) / 2 else rowX + 14, fy + 39, 1, Colors.lerp(Theme.textSecondary, Theme.text, ch))
        if (!compact) Draw.text(I18n.tr(if (ui.sidebarCollapsed) "nav.expand" else "nav.collapse"), rowX + 34, fy + 39, Colors.lerp(Theme.textSecondary, Theme.text, ch))
        else if (cOver) Ui.requestTooltip(I18n.tr("nav.expand"))
    }

    fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false
        if (scroll.mouseClicked(mx, my, button)) return true
        if (button != 0) return true
        val fy = footerY()
        if (my >= fy + 6 && my < fy + 30) { ui.openCategory("general"); return true }
        if (my >= fy + 32 && my < fy + 54) { ui.sidebarCollapsed = !ui.sidebarCollapsed; return true }
        val listY = y + LOGO_H
        if (my < listY || my >= listY + (h - LOGO_H - FOOTER_H)) return true
        val off = scroll.offset
        for (r in rows) {
            val ry = listY + r.top - off
            if (!r.interactive || my < ry || my >= ry + r.height) continue
            when (r.kind) {
                Kind.GROUP -> ui.toggleGroup(r.id)
                Kind.CATEGORY -> ui.openCategory(r.id)
                Kind.FAVORITE -> FeatureRegistry.feature(r.id)?.let { ui.openFeature(it.categoryId, it.id) }
                else -> {}
            }
            return true
        }
        return true
    }

    /** Centre of a category row in UI coordinates, for automated UI tests. */
    internal fun debugCategoryCenter(id: String): IntArray? {
        val listY = y + LOGO_H
        val r = rows.firstOrNull { it.kind == Kind.CATEGORY && it.id == id && it.height > 0 } ?: return null
        return intArrayOf(x + w / 2, listY + r.top - scroll.offset + r.height / 2)
    }

    fun mouseDragged(my: Int) = scroll.mouseDragged(my)
    fun mouseReleased() = scroll.mouseReleased()
    fun mouseScrolled(mx: Int, my: Int, amount: Double): Boolean {
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false
        scroll.wheel(amount); return true
    }
}
