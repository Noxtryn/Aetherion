package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.common.features.FeatureRegistry
import dev.freelocs.aetherion.common.features.FeatureStatus
import dev.freelocs.aetherion.common.services.I18n
import dev.freelocs.aetherion.common.services.NoticeKind
import dev.freelocs.aetherion.common.ui.UiState
import dev.freelocs.aetherion.core.Aetherion
import dev.freelocs.aetherion.gui.animations.AuroraAnimationEngine
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.engine.Icon
import dev.freelocs.aetherion.gui.engine.Icons
import dev.freelocs.aetherion.gui.engine.PerfStats
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Radius
import dev.freelocs.aetherion.gui.theme.Theme
import dev.freelocs.aetherion.platform.Platform
import kotlin.math.sin

/** Section heading with a small accent tick. */
class SectionTitle(private val text: () -> String, private val trailing: (() -> String)? = null) : Widget() {
    init { h = 22 }
    override fun preferredHeight(width: Int) = 22
    override fun render(mx: Int, my: Int) {
        Draw.rect(x, y + 6, 3, 10, Theme.accent)
        Draw.text(text(), x + 10, y + 7, Theme.text)
        trailing?.let { Draw.textRight(it(), x + w, y + 7, Theme.textSecondary) }
    }
}

/** Wrapped secondary-colour paragraph. */
class Paragraph(private val text: () -> String, private val color: () -> Int = { Theme.textSecondary }) : Widget() {
    override fun preferredHeight(width: Int) = TextWrap.wrap(text(), width).size * 10 + 4
    override fun render(mx: Int, my: Int) {
        for ((i, l) in TextWrap.wrap(text(), w).withIndex()) Draw.text(l, x, y + 2 + i * 10, color())
    }
}

/** Centred placeholder for empty lists. */
class EmptyState(private val icon: Icon, private val text: () -> String) : Widget() {
    init { h = 96 }
    override fun preferredHeight(width: Int) = 96
    override fun render(mx: Int, my: Int) {
        Draw.round(x, y, w, h, Radius.LG, Colors.alpha(Theme.card, 0.5f))
        Draw.ring(x, y, w, h, Radius.LG, Colors.alpha(Theme.border, 0.6f))
        Draw.icon(icon, x + w / 2 - 16, y + 22, 4, Colors.alpha(Theme.textSecondary, 0.6f))
        val lines = TextWrap.wrap(text(), w - 40)
        for ((i, l) in lines.withIndex()) Draw.textCentered(l, x + w / 2, y + 62 + i * 10, Theme.textSecondary)
    }
}

/**
 * Banner card for page headers. An optional slow ambient glow drifts behind the text when decorative
 * effects are on and reduced motion is off.
 */
class HeroCard(
    private val icon: Icon,
    private val title: () -> String,
    private val subtitle: () -> String,
    private val detail: () -> String = { "" },
    private val actions: List<AuroraButton> = emptyList()
) : AuroraCard() {
    init { h = 84 }
    override fun preferredHeight(width: Int) = (if (actions.isEmpty()) 84 else 100) + (if (width < 330 && actions.isNotEmpty()) 28 else 0)

    override fun render(mx: Int, my: Int) {
        val ph = preferredHeight(w); h = ph
        surface(interactive = false)
        if (AuroraAnimationEngine.decorative && Theme.glow > 0f) {
            Draw.pushClip(x, y, w, h)
            val t = if (AuroraAnimationEngine.reducedMotion) 0f else AuroraAnimationEngine.time
            val gx = x + (w * (0.62f + 0.12f * sin(t * 0.35f))).toInt()
            val gy = y + (h * (0.5f + 0.18f * sin(t * 0.27f + 1.3f))).toInt()
            Draw.pushScale(gx.toFloat(), gy.toFloat(), 3.2f)
            Draw.glow(gx - 10, gy - 10, 20, 20, Colors.alpha(Theme.accent, 0.30f * Theme.glow))
            Draw.popTransform()
            Draw.pushScale(gx + 40f, gy + 6f, 2.4f)
            Draw.glow(gx + 30, gy - 4, 20, 20, Colors.alpha(Theme.accentSecondary, 0.22f * Theme.glow))
            Draw.popTransform()
            Draw.popClip()
        }
        Draw.round(x + 16, y + 16, 40, 40, Radius.LG, Colors.alpha(Theme.accent, 0.16f))
        Draw.icon(icon, x + 16 + 4, y + 16 + 4, 4, Theme.accent)
        val tx = x + 68
        val tw = w - 68 - 16
        Draw.text(Draw.fit(title(), tw), tx, y + 18, Theme.text)
        Draw.text(Draw.fit(subtitle(), tw), tx, y + 32, Theme.textSecondary)
        val d = detail()
        if (d.isNotEmpty()) Draw.text(Draw.fit(d, tw), tx, y + 46, Colors.alpha(Theme.textSecondary, 0.8f))
        var bx = tx
        var by = y + h - 28
        for (b in actions) {
            val bw = b.naturalWidth().coerceAtLeast(70)
            if (bx + bw > x + w - 12) { bx = tx; by -= 26 }
            b.setBounds(bx, by, bw, 22)
            b.draw(mx, my, y, y + h)
            bx += bw + 8
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean = actions.any { it.mouseClicked(mx, my, button) } || contains(mx, my)
    override fun mouseReleased(mx: Int, my: Int, button: Int): Boolean = actions.any { it.mouseReleased(mx, my, button) }
}

/** A row of equal-width stat tiles. */
class StatRow(private val tiles: List<Tile>) : Widget() {
    class Tile(val label: () -> String, val value: () -> String, val sub: () -> String, val color: () -> Int = { Theme.text })
    init { h = 62 }
    override fun preferredHeight(width: Int) = 62
    override fun render(mx: Int, my: Int) {
        val gap = 8
        val n = tiles.size
        val tw = (w - gap * (n - 1)) / n
        for ((i, t) in tiles.withIndex()) {
            val tx = x + i * (tw + gap)
            Draw.round(tx, y, tw, h, Radius.LG, Theme.card)
            Draw.ring(tx, y, tw, h, Radius.LG, Colors.alpha(Theme.border, 0.8f))
            Draw.text(Draw.fit(t.label(), tw - 20), tx + 10, y + 9, Theme.textSecondary)
            Draw.text(Draw.fit(t.value(), tw - 20), tx + 10, y + 24, t.color())
            Draw.text(Draw.fit(t.sub(), tw - 20), tx + 10, y + 41, Colors.alpha(Theme.textSecondary, 0.75f))
        }
    }
}

/** Card with a title, description and one action button (used for the HUD editor launcher). */
class ActionCard(private val icon: Icon, private val title: () -> String, private val desc: () -> String, private val button: AuroraButton) : AuroraCard() {
    init { h = 58 }
    override fun preferredHeight(width: Int) = 58
    override fun render(mx: Int, my: Int) {
        surface(interactive = false)
        Draw.round(x + 12, y + 15, 28, 28, Radius.MD, Colors.alpha(Theme.accent, 0.16f))
        Draw.icon(icon, x + 12 + 6, y + 15 + 6, 2, Theme.accent)
        val bw = button.naturalWidth().coerceAtLeast(90)
        button.setBounds(x + w - bw - 14, y + (h - 22) / 2, bw, 22)
        val tw = w - 52 - bw - 28
        Draw.text(Draw.fit(title(), tw), x + 52, y + 15, Theme.text)
        Draw.text(Draw.fit(desc(), tw), x + 52, y + 30, Theme.textSecondary)
        button.draw(mx, my, y, y + h)
    }
    override fun mouseClicked(mx: Int, my: Int, button: Int) = this.button.mouseClicked(mx, my, button)
    override fun mouseReleased(mx: Int, my: Int, button: Int) = this.button.mouseReleased(mx, my, button)
}

/** Profile list with switch/delete per profile plus create, duplicate, export and import. */
class ProfileCard(private val ui: UiState) : AuroraCard() {
    private class Row(val name: String, val switch: AuroraButton, val delete: AuroraButton?)

    private val cfg get() = Aetherion.config
    private val rows: List<Row> = cfg.profileNames.map { name ->
        Row(
            name,
            AuroraButton({ I18n.tr("ui.switch") }, ButtonStyle.SECONDARY) { if (cfg.switchProfile(name)) { Ui.toast(NoticeKind.SUCCESS, I18n.tr("toast.profileSwitched", name)); ui.invalidate() } },
            if (name == "default") null else AuroraButton({ I18n.tr("ui.delete") }, ButtonStyle.DANGER) {
                Ui.openModal(Modals.confirm(I18n.tr("modal.deleteProfile.title"), I18n.tr("modal.deleteProfile.msg", name), I18n.tr("ui.delete"), true) {
                    cfg.deleteProfile(name); Ui.toast(NoticeKind.INFO, I18n.tr("toast.profileDeleted", name)); ui.invalidate()
                })
            }
        )
    }
    private val newButton = AuroraButton({ I18n.tr("ui.newProfile") }, ButtonStyle.PRIMARY, Icons.PLUS) {
        Ui.openModal(Modals.prompt(I18n.tr("modal.newProfile.title"), I18n.tr("modal.newProfile.msg"), "", I18n.tr("ui.create")) { n ->
            val created = cfg.createProfile(n.ifBlank { "profile" }); cfg.switchProfile(created)
            Ui.toast(NoticeKind.SUCCESS, I18n.tr("toast.profileCreated", created)); ui.invalidate()
        })
    }
    private val dupButton = AuroraButton({ I18n.tr("ui.duplicate") }, ButtonStyle.SECONDARY) {
        val created = cfg.createProfile("${cfg.activeProfile} copy", copyFromActive = true)
        Ui.toast(NoticeKind.SUCCESS, I18n.tr("toast.profileCreated", created)); ui.invalidate()
    }
    private val exportButton = AuroraButton({ I18n.tr("ui.export") }, ButtonStyle.SECONDARY, Icons.EXPORT) {
        val json = cfg.exportActiveProfile()
        Platform.clipboard = json
        Ui.toast(NoticeKind.SUCCESS, I18n.tr("toast.exported"))
    }
    private val importButton = AuroraButton({ I18n.tr("ui.import") }, ButtonStyle.SECONDARY, Icons.IMPORT) {
        cfg.importProfile(Platform.clipboard).fold(
            { name -> Ui.toast(NoticeKind.SUCCESS, I18n.tr("toast.imported", name)); ui.invalidate() },
            { e -> Ui.toast(NoticeKind.ERROR, I18n.tr("toast.importFailed", e.message ?: "?")) }
        )
    }
    private val resetButton = AuroraButton({ I18n.tr("ui.resetAll") }, ButtonStyle.DANGER, Icons.RESET) {
        Ui.openModal(Modals.confirm(I18n.tr("modal.resetAll.title"), I18n.tr("modal.resetAll.msg"), I18n.tr("ui.resetAll"), true) {
            cfg.resetAll(includeGlobal = true); Aetherion.applyUiSettings(); Aetherion.features.reconcile()
            Ui.toast(NoticeKind.INFO, I18n.tr("toast.resetAll")); ui.invalidate()
        })
    }

    private val actionRow1 = listOf(newButton, dupButton, exportButton, importButton)
    private val actionRow2 = listOf(resetButton)
    private val rowH = 28
    private val buttonRows get() = 2

    override fun preferredHeight(width: Int): Int { h = 14 + 16 + rows.size * rowH + 10 + buttonRows * 26 + 8 + 10; return h }

    override fun render(mx: Int, my: Int) {
        preferredHeight(w)
        surface(interactive = false)
        Draw.text(I18n.tr("profiles.title"), x + 14, y + 12, Theme.text)
        Draw.textRight(I18n.tr("profiles.hint"), x + w - 14, y + 12, Theme.textSecondary)
        var ry = y + 30
        for (r in rows) {
            val active = r.name == cfg.activeProfile
            Draw.round(x + 10, ry, w - 20, rowH - 4, Radius.MD, Colors.alpha(if (active) Theme.accent else Theme.surface, if (active) 0.12f else 0.55f))
            Draw.icon(Icons.USER, x + 20, ry + (rowH - 4 - 8) / 2, 1, if (active) Theme.accent else Theme.textSecondary)
            Draw.text(Draw.fit(r.name, w - 230), x + 34, ry + (rowH - 4 - 8) / 2, Theme.text)
            var bx = x + w - 18
            r.delete?.let { b -> val bw = 64; bx -= bw; b.setBounds(bx, ry + 1, bw, 20); b.draw(mx, my, y, y + h); bx -= 6 }
            if (active) Chip.drawRight(I18n.tr("ui.active"), bx, ry + (rowH - 4 - 14) / 2, Colors.SUCCESS)
            else { val bw = 64; bx -= bw; r.switch.setBounds(bx, ry + 1, bw, 20); r.switch.draw(mx, my, y, y + h) }
            ry += rowH
        }
        ry += 6
        place(actionRow1, ry, mx, my)
        place(actionRow2, ry + 26, mx, my)
    }

    private fun place(buttons: List<AuroraButton>, by: Int, mx: Int, my: Int) {
        var bx = x + 14
        for (b in buttons) {
            val bw = b.naturalWidth().coerceAtLeast(60)
            b.setBounds(bx, by, bw, 20)
            b.draw(mx, my, y, y + h)
            bx += bw + 6
        }
    }

    private fun all(): List<AuroraButton> = rows.flatMap { listOfNotNull(it.switch, it.delete) } + listOf(newButton, dupButton, exportButton, importButton, resetButton)

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        for (b in all()) if (b.contains(mx, my) && b.mouseClicked(mx, my, button)) return true
        return contains(mx, my)
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int): Boolean { var used = false; for (b in all()) used = b.mouseReleased(mx, my, button) || used; return used }
}

/** Live measurements of the config screen itself plus the state of the performance switches. */
class PerformanceCard : AuroraCard() {
    init { h = 150 }
    override fun preferredHeight(width: Int) = 150

    private fun activeCount(): Int {
        var n = 0
        for (f in FeatureRegistry.features) if (f.status == FeatureStatus.IMPLEMENTED && Aetherion.features.isActive(f.id)) n++
        return n
    }

    override fun render(mx: Int, my: Int) {
        surface(interactive = false)
        Draw.text(I18n.tr("perf.title"), x + 14, y + 12, Theme.text)
        val n = PerfStats.sampleCount
        Draw.textRight(I18n.tr("perf.samples", n), x + w - 14, y + 12, Theme.textSecondary)

        // frame-time bars (real samples), scaled to the worst of the window
        val chartX = x + 14; val chartY = y + 30; val chartW = w - 28; val chartH = 44
        Draw.round(chartX, chartY, chartW, chartH, Radius.MD, Colors.alpha(Theme.surface, 0.7f))
        val max = PerfStats.maxMs().coerceAtLeast(0.5f)
        val barW = (chartW - 8) / PerfStats.CAPACITY.toFloat()
        for (i in 0 until n) {
            val v = PerfStats.sampleMs(i)
            val bh = ((v / max) * (chartH - 10)).toInt().coerceAtLeast(1)
            val bx = chartX + 4 + (i * barW).toInt()
            Draw.rect(bx, chartY + chartH - 4 - bh, maxOf(1, barW.toInt()), bh, Colors.lerp(Theme.accent, Theme.accentSecondary, (v / max).coerceIn(0f, 1f)))
        }
        val avg = String.format("%.2f", PerfStats.averageMs())
        val mxv = String.format("%.2f", PerfStats.maxMs())
        Draw.text(I18n.tr("perf.frame", avg, mxv), x + 14, y + 82, Theme.text)
        Draw.text(I18n.tr("perf.draws", PerfStats.lastDrawCalls), x + 14, y + 94, Theme.textSecondary)
        val reduced = if (AuroraAnimationEngine.reducedMotion) I18n.tr("ui.on") else I18n.tr("ui.off")
        val deco = if (AuroraAnimationEngine.decorative) I18n.tr("ui.on") else I18n.tr("ui.off")
        Draw.text(I18n.tr("perf.motion", reduced, deco), x + 14, y + 106, Theme.textSecondary)
        Draw.text(I18n.tr("perf.subs", dev.freelocs.aetherion.common.events.EventBus.subscriberCount, activeCount()), x + 14, y + 118, Theme.textSecondary)
        Draw.text(I18n.tr("perf.note"), x + 14, y + 132, Colors.alpha(Theme.textSecondary, 0.7f))
    }
}
