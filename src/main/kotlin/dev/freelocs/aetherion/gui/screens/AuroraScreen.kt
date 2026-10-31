package dev.freelocs.aetherion.gui.screens

import dev.freelocs.aetherion.common.events.LanguageChangedEvent
import dev.freelocs.aetherion.common.events.ProfileChangedEvent
import dev.freelocs.aetherion.common.events.Subscriptions
import dev.freelocs.aetherion.common.ui.UiState
import dev.freelocs.aetherion.core.Aetherion
import dev.freelocs.aetherion.gui.animations.AuroraAnimationEngine
import dev.freelocs.aetherion.gui.animations.Easing
import dev.freelocs.aetherion.gui.animations.Spring
import dev.freelocs.aetherion.gui.animations.Tween
import dev.freelocs.aetherion.gui.components.AuroraFeatureCard
import dev.freelocs.aetherion.gui.components.AuroraHeader
import dev.freelocs.aetherion.gui.components.AuroraKeybindField
import dev.freelocs.aetherion.gui.components.AuroraLayout
import dev.freelocs.aetherion.gui.components.AuroraModal
import dev.freelocs.aetherion.gui.components.AuroraScrollbar
import dev.freelocs.aetherion.gui.components.AuroraSidebar
import dev.freelocs.aetherion.gui.components.AuroraToast
import dev.freelocs.aetherion.gui.components.AuroraTooltip
import dev.freelocs.aetherion.gui.components.FeatureDetailPanel
import dev.freelocs.aetherion.gui.components.Modals
import dev.freelocs.aetherion.gui.components.TextWrap
import dev.freelocs.aetherion.gui.components.Ui
import dev.freelocs.aetherion.gui.components.Widget
import dev.freelocs.aetherion.gui.editor.AuroraHudEditor
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.engine.PerfStats
import dev.freelocs.aetherion.gui.theme.AuroraThemeManager
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Radius
import dev.freelocs.aetherion.gui.theme.Theme
import dev.freelocs.aetherion.platform.Keys
import dev.freelocs.aetherion.platform.Platform
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Aetherion configuration screen. It owns the layout, the page widgets and the overlay stack
 * (popup, modal, tooltip, toasts) and routes input in a fixed priority order:
 * modal > key capture > popup > focused widget > header/sidebar/detail > page content.
 */
class AuroraScreen(private val parent: Screen?, startCategory: String? = null) : Screen(Component.literal("Aetherion")) {
    val ui = UiState().also { s -> startCategory?.let { s.openCategory(it) } }

    private val layout = AuroraLayout()
    private val sidebar = AuroraSidebar(ui)
    private val header = AuroraHeader(ui) { openModal(Modals.notifications { dev.freelocs.aetherion.common.services.Notifications.clear() }) }
    private val detail = FeatureDetailPanel(ui)
    private val scroll = AuroraScrollbar()

    private var widgets: List<Widget> = emptyList()
    private var builtVersion = -1
    private val openAnim = Spring(0f, 300f, 28f)
    private val pageAnim = Tween(1f, 0.24f, Easing.OUT_CUBIC)
    private var closing = false
    private var modal: AuroraModal? = null
    private var uiScale = 1f
    private var subs: Subscriptions? = null
    private var mouseUx = 0
    private var mouseUy = 0

    private val gap = 10
    private val pad = 16

    // ── lifecycle ───────────────────────────────────────────────────────────

    override fun init() {
        if (subs == null) {
            subs = Subscriptions().also { s ->
                s.on<LanguageChangedEvent> { TextWrap.clear(); ui.invalidate() }
                s.on<ProfileChangedEvent> { ui.invalidate() }
            }
        }
        Ui.reset()
        Ui.openModal = { openModal(it) }
        Ui.openHudEditor = { Platform.openScreen(AuroraHudEditor(this)) }
        // Language may have been changed in the game options since last open.
        Aetherion.applyUiSettings()
    }

    override fun removed() {
        subs?.cancelAll(); subs = null
        Ui.reset()
        AuroraToast.reset()
        Aetherion.config.saveNow()
    }

    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = false

    override fun onClose() { closing = true }

    override fun tick() {
        if (closing && openAnim.value < 0.03f) Platform.openScreen(parent)
    }

    private fun openModal(m: AuroraModal) {
        modal = m
        m.focusBody()
    }

    // ── rendering ───────────────────────────────────────────────────────────

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (minecraft.level == null) extractPanorama(ctx, partialTick)
        if (Aetherion.config.bool("performance.render", "backdropBlur")) extractBlurredBackground(ctx)
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val t0 = System.nanoTime()
        val a0 = PerfStats.allocatedNow()
        AuroraAnimationEngine.beginFrame()
        AuroraThemeManager.update()
        uiScale = AuroraLayout.uiScale(width, height)
        val vw = (width / uiScale).roundToInt()
        val vh = (height / uiScale).roundToInt()
        Ui.viewW = vw; Ui.viewH = vh
        mouseUx = (mouseX / uiScale).roundToInt(); mouseUy = (mouseY / uiScale).roundToInt()
        Draw.begin(ctx, font)
        if (modal?.finished == true) modal = null
        Ui.hoverAllowed = modal == null

        openAnim.target = if (closing) 0f else 1f
        val open = openAnim.update().coerceIn(0f, 1f)

        if (uiScale != 1f) { ctx.pose().pushMatrix(); ctx.pose().scale(uiScale, uiScale) }

        // dim scrim over the (blurred) world
        Draw.alpha = open
        Draw.rect(0, 0, vw, vh, Theme.scrim)

        if (ui.version != builtVersion) rebuildPage()
        layout.compute(vw, vh, ui.sidebarCollapsed, ui.selectedFeatureId != null)

        Draw.pushScale(vw / 2f, vh / 2f, 0.965f + 0.035f * open)
        renderPanel(mouseUx, mouseUy)
        Draw.popTransform()
        Draw.alpha = 1f

        // overlays: always above the panel
        Draw.nextLayer()
        Draw.alpha = open
        Ui.popup?.render(mouseUx, mouseUy)
        modal?.render(mouseUx, mouseUy)
        val tipText = Ui.resolveTooltip(AuroraAnimationEngine.dt)
        val tip = if (Aetherion.config.bool("general.interface", "tooltips") && modal == null && Ui.popup == null) tipText else null
        AuroraTooltip.render(tip, mouseUx, mouseUy)
        AuroraToast.render(layout.panelX + layout.panelW - 14, layout.panelY + layout.panelH - 14)
        Draw.alpha = 1f

        if (uiScale != 1f) ctx.pose().popMatrix()
        PerfStats.record(System.nanoTime() - t0, Draw.calls)
        if (PerfStats.trackAllocations && a0 != 0L) PerfStats.recordAllocation(PerfStats.allocatedNow() - a0)
    }

    private fun rebuildPage() {
        widgets = Pages.build(ui)
        builtVersion = ui.version
        scroll.reset()
        pageAnim.snap(0.35f)
        pageAnim.animateTo(1f)
    }

    private fun renderPanel(mx: Int, my: Int) {
        val l = layout
        if (Theme.glow > 0f) Draw.glow(l.panelX, l.panelY, l.panelW, l.panelH, Colors.alpha(Theme.accent, 0.10f * Theme.glow))
        Draw.glow(l.panelX, l.panelY, l.panelW, l.panelH, Colors.alpha(0xFF000000.toInt(), 0.55f))
        Draw.round(l.panelX, l.panelY, l.panelW, l.panelH, Radius.XL, Theme.surface)

        // ambient accent motion (decorative, off with reduced motion / decorative effects off)
        if (AuroraAnimationEngine.decorative && Theme.glow > 0f) {
            Draw.pushClip(l.panelX, l.panelY, l.panelW, l.panelH)
            val t = if (AuroraAnimationEngine.reducedMotion) 0f else AuroraAnimationEngine.time
            val ax = l.panelX + (l.panelW * (0.72f + 0.10f * sin(t * 0.21f))).toInt()
            val ay = l.panelY + (l.panelH * (0.18f + 0.06f * sin(t * 0.17f + 2f))).toInt()
            blob(ax, ay, 6f, Colors.alpha(Theme.accent, 0.16f * Theme.glow))
            val bx = l.panelX + l.sidebarW + (l.panelW * (0.25f + 0.08f * sin(t * 0.13f + 4f))).toInt()
            blob(bx, l.panelY + l.panelH - 40, 5f, Colors.alpha(Theme.accentSecondary, 0.13f * Theme.glow))
            Draw.popClip()
        }

        sidebar.compact = l.compact
        sidebar.x = l.panelX; sidebar.y = l.panelY; sidebar.w = l.sidebarW; sidebar.h = l.panelH
        sidebar.render(mx, my)

        header.x = l.contentX; header.y = l.panelY; header.w = l.panelW - l.sidebarW; header.h = l.headerH
        header.render(mx, my)

        renderContent(mx, my)

        if (l.detailW > 0) {
            detail.x = l.detailX; detail.y = l.contentY; detail.w = l.detailW; detail.h = l.contentH
            detail.render(mx, my)
        }
        Draw.ring(l.panelX, l.panelY, l.panelW, l.panelH, Radius.XL, Colors.alpha(Theme.border, 0.9f))
    }

    /** Soft round glow: a small glow sprite scaled up around its centre. */
    private fun blob(cx: Int, cy: Int, scale: Float, color: Int) {
        Draw.pushScale(cx.toFloat(), cy.toFloat(), scale)
        Draw.glow(cx - 10, cy - 10, 20, 20, color)
        Draw.popTransform()
    }

    private fun layoutWidgets(): Int {
        val l = layout
        val cw = l.contentW - pad * 2 - 6
        var cy = l.contentY + 12 - scroll.offset
        for (w in widgets) {
            val h = w.preferredHeight(cw)
            w.setBounds(l.contentX + pad, cy, cw, h)
            cy += h + gap
        }
        return cy - (l.contentY - scroll.offset) + 4
    }

    private fun renderContent(mx: Int, my: Int) {
        val l = layout
        val total = layoutWidgets()
        scroll.setViewport(l.contentX, l.contentY, l.contentW, l.contentH, total)
        scroll.update()
        val a = pageAnim.update()
        val prev = Draw.alpha
        Draw.alpha = prev * a
        val shift = ((1f - a) * 14f).roundToInt()
        Draw.pushClip(l.contentX, l.contentY, l.contentW, l.contentH)
        val clipTop = l.contentY
        val clipBottom = l.contentY + l.contentH
        for (w in widgets) {
            if (w.y + w.h + shift < clipTop || w.y + shift > clipBottom) continue
            if (shift != 0) { w.y += shift }
            w.draw(mx, my, clipTop, clipBottom)
            if (shift != 0) { w.y -= shift }
        }
        Draw.popClip()
        Draw.alpha = prev
        scroll.render(mx, my)
    }

    // ── input ───────────────────────────────────────────────────────────────

    private fun ux(v: Double) = (v / uiScale).roundToInt()

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = ux(event.x()); val my = ux(event.y()); val b = event.button()
        modal?.let { return it.mouseClicked(mx, my, b) }
        Ui.popup?.let { p -> if (p.mouseClicked(mx, my, b)) return true }
        Ui.focusedWidget?.let { f -> if (!f.contains(mx, my)) Ui.focus(null) }

        if (header.mouseClicked(mx, my, b)) return true
        if (sidebar.mouseClicked(mx, my, b)) return true
        if (layout.detailW > 0 && detail.mouseClicked(mx, my, b)) return true
        val l = layout
        if (mx >= l.contentX && mx < l.contentX + l.contentW && my >= l.contentY && my < l.contentY + l.contentH) {
            layoutWidgets()
            if (scroll.mouseClicked(mx, my, b)) return true
            for (w in widgets.asReversed()) if (w.contains(mx, my) && w.mouseClicked(mx, my, b)) return true
        }
        return mx >= l.panelX && mx < l.panelX + l.panelW && my >= l.panelY && my < l.panelY + l.panelH
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val mx = ux(event.x()); val my = ux(event.y()); val b = event.button()
        modal?.let { return it.mouseReleased(mx, my, b) }
        Ui.popup?.mouseReleased(mx, my, b)
        header.search.mouseReleased(mx, my, b)
        sidebar.mouseReleased(); detail.mouseReleased(); scroll.mouseReleased()
        for (w in widgets) w.mouseReleased(mx, my, b)
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val mx = ux(event.x()); val my = ux(event.y()); val b = event.button()
        val dxu = (dx / uiScale).roundToInt(); val dyu = (dy / uiScale).roundToInt()
        modal?.let { return it.mouseDragged(mx, my, b, dxu, dyu) }
        if (Ui.popup?.mouseDragged(mx, my, b, dxu, dyu) == true) return true
        header.search.mouseDragged(mx, my, b, dxu, dyu)
        if (sidebar.mouseDragged(my) || detail.mouseDragged(my) || scroll.mouseDragged(my)) return true
        for (w in widgets) if (w.mouseDragged(mx, my, b, dxu, dyu)) return true
        return true
    }

    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        val mx = ux(x); val my = ux(y)
        modal?.let { return it.mouseScrolled(mx, my, scrollY) }
        if (Ui.popup?.mouseScrolled(mx, my, scrollY) == true) return true
        if (sidebar.mouseScrolled(mx, my, scrollY)) return true
        if (layout.detailW > 0 && detail.mouseScrolled(mx, my, scrollY)) return true
        for (w in widgets) if (w.contains(mx, my) && w.mouseScrolled(mx, my, scrollY)) return true
        val l = layout
        if (mx >= l.contentX && mx < l.contentX + l.contentW && my >= l.contentY && my < l.contentY + l.contentH) {
            scroll.wheel(scrollY); return true
        }
        return false
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val key = event.key(); val ctrl = event.hasControlDown(); val shift = event.hasShiftDown()
        (Ui.focusedWidget as? AuroraKeybindField)?.let { if (it.listening) return it.capture(event) }
        modal?.let { return it.keyPressed(key, ctrl, shift) }
        Ui.popup?.let { p -> if (p.keyPressed(key, ctrl, shift)) return true }

        val focused = Ui.focusedWidget
        if (focused != null && focused.keyPressed(key, ctrl, shift)) return true

        when {
            key == Keys.ESCAPE -> {
                when {
                    focused != null -> Ui.focus(null)
                    ui.searching -> { header.search.clear() }
                    else -> onClose()
                }
                return true
            }
            ctrl && key == Keys.F -> { Ui.focus(header.search); return true }
            key == Keys.TAB -> { cycleFocus(if (shift) -1 else 1); return true }
            focused == null -> if (navigateCards(key)) return true
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        val cp = event.codepoint()
        modal?.let { return it.charTyped(cp) }
        if (Ui.popup?.charTyped(cp) == true) return true
        Ui.focusedWidget?.let { if (it.charTyped(cp)) return true }
        return false
    }

    private fun cycleFocus(dir: Int) {
        val list = ArrayList<Widget>()
        list += header.search
        for (w in widgets) if (w is AuroraFeatureCard) w.collectFocusables(list)
        if (list.isEmpty()) return
        val cur = list.indexOf(Ui.focusedWidget)
        val next = if (cur < 0) (if (dir > 0) 0 else list.size - 1) else Math.floorMod(cur + dir, list.size)
        Ui.focus(list[next])
        val w = list[next]
        if (w !== header.search) { layoutWidgets(); scroll.ensureVisible(w.y - layout.contentY + scroll.offset - 8, w.y + w.h - layout.contentY + scroll.offset + 8) }
    }

    /** Up/Down select feature cards, Right/Left expand/collapse, Enter toggles. */
    private fun navigateCards(key: Int): Boolean {
        val cards = widgets.filterIsInstance<AuroraFeatureCard>()
        if (cards.isEmpty()) return false
        val idx = cards.indexOfFirst { it.definition.id == ui.selectedFeatureId }
        when (key) {
            Keys.DOWN, Keys.UP -> {
                val next = if (idx < 0) 0 else (idx + if (key == Keys.DOWN) 1 else -1).coerceIn(0, cards.size - 1)
                ui.selectFeature(cards[next].definition.id)
                layoutWidgets()
                val c = cards[next]
                scroll.ensureVisible(c.y - layout.contentY + scroll.offset - 6, c.y + c.h - layout.contentY + scroll.offset + 6)
            }
            Keys.RIGHT -> if (idx >= 0 && !cards[idx].isExpanded) ui.toggleExpanded(cards[idx].definition.id) else return false
            Keys.LEFT -> if (idx >= 0 && cards[idx].isExpanded) ui.toggleExpanded(cards[idx].definition.id) else return false
            Keys.ENTER, Keys.ENTER_NUMPAD -> {
                val def = cards.getOrNull(idx)?.definition ?: return false
                if (!def.toggleable) return false
                Aetherion.features.setEnabled(def, !Aetherion.config.isEnabled(def))
            }
            else -> return false
        }
        return true
    }

    // ── test/automation access ──────────────────────────────────────────────

    /** Layout and widgets for the dev harness (clicks are delivered through the normal input methods). */
    internal fun debugWidgets(): List<Widget> = widgets
    internal fun debugLayout() = layout
    internal fun debugScale() = uiScale
    internal fun debugModal() = modal
    internal fun debugScrollOffset() = scroll.offset
    internal fun debugSidebar() = sidebar
    internal fun debugHeader() = header
    internal fun debugBellCenter(): IntArray = header.debugBellCenter()

    /** Scrolls [w] near the top of the viewport immediately (no animation) and re-lays out; for UI tests. */
    internal fun debugReveal(w: Widget) {
        layoutWidgets()
        val top = w.y - layout.contentY + scroll.offset
        scroll.scrollTo((top - 24).toFloat(), animate = false)
        layoutWidgets()
    }
    internal fun uiToScreen(v: Int): Double = v * uiScale.toDouble()
}
