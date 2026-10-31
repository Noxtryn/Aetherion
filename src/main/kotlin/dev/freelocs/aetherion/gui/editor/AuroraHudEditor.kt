package dev.freelocs.aetherion.gui.editor

import dev.freelocs.aetherion.common.features.FeatureRegistry
import dev.freelocs.aetherion.common.services.I18n
import dev.freelocs.aetherion.common.services.NoticeKind
import dev.freelocs.aetherion.core.Aetherion
import dev.freelocs.aetherion.gui.animations.AuroraAnimationEngine
import dev.freelocs.aetherion.gui.animations.Spring
import dev.freelocs.aetherion.gui.components.AuroraButton
import dev.freelocs.aetherion.gui.components.AuroraDropdown
import dev.freelocs.aetherion.gui.components.AuroraSlider
import dev.freelocs.aetherion.gui.components.AuroraToast
import dev.freelocs.aetherion.gui.components.AuroraToggle
import dev.freelocs.aetherion.gui.components.AuroraTooltip
import dev.freelocs.aetherion.gui.components.ButtonStyle
import dev.freelocs.aetherion.gui.components.Ui
import dev.freelocs.aetherion.gui.components.Widget
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.engine.Icons
import dev.freelocs.aetherion.gui.theme.AuroraThemeManager
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Radius
import dev.freelocs.aetherion.gui.theme.Theme
import dev.freelocs.aetherion.hud.HudManager
import dev.freelocs.aetherion.hud.WidgetBox
import dev.freelocs.aetherion.platform.Keys
import dev.freelocs.aetherion.platform.Platform
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.abs
import kotlin.math.roundToInt

/** 3x3 anchor selector for the selected widget. */
private class AnchorPicker(private val get: () -> Int, private val set: (Int) -> Unit) : Widget() {
    init { w = 36; h = 36 }
    override fun render(mx: Int, my: Int) {
        Draw.round(x, y, w, h, Radius.MD, Theme.surface)
        Draw.ring(x, y, w, h, Radius.MD, Theme.border)
        for (i in 0 until 9) {
            val cx = x + 5 + (i % 3) * 11
            val cy = y + 5 + (i / 3) * 11
            val sel = get() == i
            val over = mx >= cx - 1 && mx < cx + 9 && my >= cy - 1 && my < cy + 9
            Draw.round(cx + 1, cy + 1, 6, 6, 3, if (sel) Theme.accent else Colors.alpha(Theme.textSecondary, if (over) 0.9f else 0.45f))
        }
    }
    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (button != 0 || !contains(mx, my)) return false
        val col = ((mx - x - 3) / 11).coerceIn(0, 2); val row = ((my - y - 3) / 11).coerceIn(0, 2)
        set(row * 3 + col); return true
    }
}

/**
 * Full-screen visual HUD layout editor. It draws the real widgets with live data (so it is a true preview),
 * lets you drag them with grid snapping and alignment guides, and edits anchor, scale, opacity and layer of
 * the selection. Layouts persist per profile; presets and three save slots are available. This is the only
 * place HUD elements receive mouse input - during play they never intercept anything.
 */
class AuroraHudEditor(private val parent: Screen?) : Screen(Component.literal("Aetherion HUD Editor")) {
    private val cfg get() = Aetherion.config
    private var selected: String? = null
    private var dragging = false
    private var grabX = 0
    private var grabY = 0
    private val open = Spring(0f, 320f, 30f)
    private var closing = false

    // alignment guides found during the last drag step
    private var guideX = -1
    private var guideY = -1

    private companion object { const val EDGE = 8 }

    /** Toolbar dock side; chosen each frame so the toolbars never cover widgets (kept stable while dragging). */
    private var dockBottom = false

    /** Tab hides the toolbars so widgets underneath (small screens!) can be reached. */
    private var toolsHidden = false
    private var topBarY = EDGE

    private fun hud() = FeatureRegistry.feature("hud.core")!!
    private fun snapOn() = cfg.bool("hud.core", "snap")
    private fun guidesOn() = cfg.bool("hud.core", "guides")
    private fun grid() = cfg.double("hud.core", "grid").toInt().coerceAtLeast(2)

    // ── toolbar widgets ─────────────────────────────────────────────────────
    private val presetDefault = AuroraButton({ I18n.tr("hud.preset.default") }) { applyPreset(0) }
    private val presetCorners = AuroraButton({ I18n.tr("hud.preset.corners") }) { applyPreset(1) }
    private val presetStack = AuroraButton({ I18n.tr("hud.preset.stack") }) { applyPreset(2) }
    private val resetButton = AuroraButton({ I18n.tr("hud.reset") }, ButtonStyle.DANGER, Icons.RESET) { applyPreset(0); toast("hud.resetDone") }
    private val doneButton = AuroraButton({ I18n.tr("hud.done") }, ButtonStyle.PRIMARY, Icons.CHECK) { onClose() }
    private var slot = "slot1"
    private val slotDropdown = AuroraDropdown({ listOf("slot1", "slot2", "slot3") }, { slotLabel(it) }, { slot }, { slot = it }, 110)
    private val saveButton = AuroraButton({ I18n.tr("hud.slot.save") }) { cfg.saveLayoutSlot(slot); toast("hud.slot.saved", slotLabel(slot)) }
    private val loadButton = AuroraButton({ I18n.tr("hud.slot.load") }) {
        if (cfg.restoreLayoutSlot(slot)) { HudManager.markOrderDirty(); toast("hud.slot.loaded", slotLabel(slot)) } else toast("hud.slot.empty", slotLabel(slot))
    }
    private val snapToggle = AuroraToggle({ snapOn() }, { cfg.set(hud(), hud().setting("snap")!!, it) })
    private val guidesToggle = AuroraToggle({ guidesOn() }, { cfg.set(hud(), hud().setting("guides")!!, it) })
    private val gridSlider = AuroraSlider(2.0, 32.0, 2.0, 0, " px", 8.0, { cfg.double("hud.core", "grid") }, { cfg.set(hud(), hud().setting("grid")!!, it) }, 70)

    // inspector widgets (act on the selection)
    private val scaleSlider = AuroraSlider(0.5, 3.0, 0.1, 1, "x", 1.0, { selPlacement()?.scale?.toDouble() ?: 1.0 }, { v -> selPlacement()?.let { setScale(it, v.toFloat()) } }, 80)
    private val opacitySlider = AuroraSlider(10.0, 100.0, 5.0, 0, " %", 100.0, { (selPlacement()?.opacity ?: 1f) * 100.0 }, { v -> selPlacement()?.let { it.opacity = (v / 100.0).toFloat(); cfg.markHudDirty() } }, 80)
    private val anchorPicker = AnchorPicker({ selPlacement()?.anchor ?: 0 }, { a -> selBox()?.let { HudManager.setAnchor(it, a, width, height); cfg.markHudDirty() } })
    private val forward = AuroraButton({ I18n.tr("hud.forward") }, icon = Icons.CHEVRON_DOWN.flippedCached) { moveLayer(+1) }
    private val backward = AuroraButton({ I18n.tr("hud.backward") }, icon = Icons.CHEVRON_DOWN) { moveLayer(-1) }
    private val visibleToggle = AuroraToggle({ selPlacement()?.visible ?: true }, { v -> selPlacement()?.let { it.visible = v; cfg.markHudDirty() } })

    private val topWidgets: List<Widget> = listOf(presetDefault, presetCorners, presetStack, resetButton, doneButton, slotDropdown, saveButton, loadButton, snapToggle, guidesToggle, gridSlider)
    private val inspectorWidgets: List<Widget> = listOf(scaleSlider, opacitySlider, anchorPicker, forward, backward, visibleToggle)
    private var topH = 0
    private var inspectorY = 0
    private var inspectorH = 0

    // ── helpers ─────────────────────────────────────────────────────────────
    private fun slotLabel(id: String) = I18n.tr("hud.slot.name", id.removePrefix("slot")) + if (cfg.layoutSlots.containsKey(id)) " ●" else ""
    private fun toast(key: String, vararg a: Any?) = Ui.toast(NoticeKind.INFO, I18n.tr(key, *a))
    private fun selBox(): WidgetBox? = selected?.let { HudManager.box(it) }
    private fun selPlacement() = selBox()?.let { HudManager.placement(it.widget) }

    private fun setScale(p: dev.freelocs.aetherion.common.config.WidgetPlacement, v: Float) {
        val b = selBox() ?: return
        val (x, y) = b.x to b.y
        p.scale = v.coerceIn(0.5f, 3f)
        HudManager.layout(b, width, height)
        HudManager.setPosition(b, x, y, width, height)
        cfg.markHudDirty()
    }

    private fun moveLayer(delta: Int) {
        val id = selected ?: return
        val ordered = HudManager.active.map { it.widget.id }.sortedBy { HudManager.placement(HudManager.box(it)!!.widget).layer }.toMutableList()
        val i = ordered.indexOf(id)
        val j = i + delta
        if (i < 0 || j !in ordered.indices) return
        ordered.removeAt(i); ordered.add(j, id)
        ordered.forEachIndexed { n, wid -> HudManager.placement(HudManager.box(wid)!!.widget).layer = n }
        HudManager.markOrderDirty(); cfg.markHudDirty()
    }

    private fun applyPreset(kind: Int) {
        val boxes = HudManager.active.toList()
        for ((i, b) in boxes.withIndex()) {
            val w = b.widget
            val p = HudManager.placement(w)
            when (kind) {
                0 -> { p.anchor = w.defaultAnchor; p.x = w.defaultX; p.y = w.defaultY; p.scale = 1f; p.opacity = 1f; p.visible = true; p.layer = i }
                1 -> { val corners = intArrayOf(0, 2, 6, 8); p.anchor = corners[i % 4]; p.x = if (p.anchor % 3 == 0) 6f else -6f; p.y = if (p.anchor / 3 == 0) 6f + (i / 4) * 22 else -6f - (i / 4) * 22; p.layer = i }
                2 -> { p.anchor = 0; p.x = 6f; p.y = 6f + i * 20f; p.layer = i }
            }
        }
        HudManager.markOrderDirty(); cfg.markHudDirty()
    }

    // ── lifecycle ───────────────────────────────────────────────────────────
    override fun init() {
        Ui.reset()
        HudManager.refresh(force = true)
    }

    override fun removed() { Ui.reset(); cfg.saveNow() }
    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = false
    override fun onClose() { closing = true }
    override fun tick() { if (closing && open.value < 0.03f) Platform.openScreen(parent) }

    // ── rendering ───────────────────────────────────────────────────────────
    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {}

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        AuroraAnimationEngine.beginFrame()
        AuroraThemeManager.update()
        Draw.begin(ctx, font)
        Ui.viewW = width; Ui.viewH = height
        Ui.hoverAllowed = true
        open.target = if (closing) 0f else 1f
        val o = open.update().coerceIn(0f, 1f)
        HudManager.refresh()

        Draw.alpha = o
        Draw.rect(0, 0, width, height, Colors.alpha(Theme.scrim, 0.55f))
        if (snapOn()) drawGrid()

        val boxes = HudManager.active
        if (boxes.isEmpty()) {
            Draw.textCentered(I18n.tr("hud.noWidgets"), width / 2, height / 2 - 4, Theme.textSecondary)
        }
        Draw.alpha = o
        HudManager.render(width, height, editing = true, selectedId = selected)
        // selection frames
        for (b in boxes) {
            val sel = b.widget.id == selected
            val over = mouseX >= b.x && mouseX < b.x + b.sw && mouseY >= b.y && mouseY < b.y + b.sh
            if (sel || over) {
                Draw.ring(b.x - 2, b.y - 2, b.sw + 4, b.sh + 4, 5, if (sel) Theme.accent else Colors.alpha(Theme.text, 0.5f))
                if (sel && Theme.glow > 0f) Draw.glow(b.x - 2, b.y - 2, b.sw + 4, b.sh + 4, Colors.alpha(Theme.accent, 0.3f * Theme.glow), big = false)
                val label = HudManager.title(b)
                val ly = if (b.y > 14) b.y - 13 else b.y + b.sh + 4
                Draw.text(label, b.x, ly, if (sel) Theme.accent else Theme.textSecondary)
            }
        }
        if (dragging && guidesOn()) {
            if (guideX >= 0) Draw.rect(guideX, 0, 1, height, Colors.alpha(Theme.accentSecondary, 0.8f))
            if (guideY >= 0) Draw.rect(0, guideY, width, 1, Colors.alpha(Theme.accentSecondary, 0.8f))
        }

        layoutToolbars()
        if (toolsHidden) {
            Draw.textCentered(I18n.tr("hud.toolsHidden"), width / 2, height - 14, Colors.alpha(Theme.textSecondary, 0.8f))
        } else {
            renderTopBar(mouseX, mouseY)
            if (selected != null && selBox() != null) renderInspector(mouseX, mouseY)
        }

        Draw.nextLayer()
        Ui.popup?.render(mouseX, mouseY)
        val tip = Ui.resolveTooltip(AuroraAnimationEngine.dt)
        AuroraTooltip.render(if (Ui.popup == null) tip else null, mouseX, mouseY)
        AuroraToast.render(width - 12, height - 12)
        Draw.alpha = 1f
    }

    private fun drawGrid() {
        val g = grid().coerceAtLeast(4)
        val c = Colors.alpha(Theme.text, 0.045f)
        var x = g
        while (x < width) { Draw.rect(x, 0, 1, height, c); x += g }
        var y = g
        while (y < height) { Draw.rect(0, y, width, 1, c); y += g }
        Draw.rect(width / 2, 0, 1, height, Colors.alpha(Theme.accent, 0.10f))
        Draw.rect(0, height / 2, width, 1, Colors.alpha(Theme.accent, 0.10f))
    }

    /** Flow-lays widgets in rows within [maxW]; returns total height. */
    private fun flow(ws: List<Widget>, startX: Int, startY: Int, maxW: Int, extraWidth: (Widget) -> Int = { 0 }): Int {
        var x = startX; var y = startY; var rowH = 0
        for (w in ws) {
            if (!w.visible) continue
            val ww = widthOf(w)
            if (x > startX && x + ww > startX + maxW) { x = startX; y += rowH + 6; rowH = 0 }
            w.setBounds(x, y, ww, w.h)
            x += ww + 6 + extraWidth(w)
            rowH = maxOf(rowH, w.h)
        }
        return y + rowH - startY
    }

    private fun widthOf(w: Widget): Int = when (w) {
        is AuroraButton -> w.naturalWidth().coerceAtLeast(52)
        else -> w.w
    }

    private fun layoutToolbars() {
        val maxW = (width - 24).coerceAtMost(640)
        val x0 = (width - maxW) / 2
        if (toolsHidden) { topH = 0; inspectorH = 0; return }
        val topExtra = { w: Widget -> if (w === snapToggle || w === guidesToggle) 40 else if (w === gridSlider) 34 else 0 }
        topH = flow(topWidgets, x0 + 12, 0, maxW - 24, topExtra) + 28 + 10
        // inspector: measure first, then place at the bottom edge
        val spread = { w: Widget -> if (w === visibleToggle) 40 else 0 }
        val ih = flow(inspectorWidgets, x0 + 12, 0, maxW - 24, spread)
        inspectorH = ih + 36 + 10
        val hasInspector = selected != null && selBox() != null
        val stackH = topH + (if (hasInspector) inspectorH + 6 else 0)
        if (!dragging) {
            val top = overlaps(x0, EDGE, maxW, stackH)
            val bottom = overlaps(x0, height - EDGE - stackH, maxW, stackH)
            dockBottom = if (top == bottom) dockBottom else bottom < top
        }
        val stackY = if (dockBottom) height - EDGE - stackH else EDGE
        topBarY = stackY
        inspectorY = stackY + topH + 6
        flow(topWidgets, x0 + 12, topBarY + 28, maxW - 24, topExtra)
        flow(inspectorWidgets, x0 + 12, inspectorY + 36, maxW - 24, spread)
    }

    /** Number of HUD widgets intersecting a rectangle. */
    private fun overlaps(rx: Int, ry: Int, rw: Int, rh: Int): Int =
        HudManager.active.count { b -> b.x < rx + rw && b.x + b.sw > rx && b.y < ry + rh && b.y + b.sh > ry }

    private fun renderTopBar(mx: Int, my: Int) {
        val maxW = (width - 24).coerceAtMost(640)
        val x0 = (width - maxW) / 2
        Draw.glow(x0, topBarY, maxW, topH, Colors.alpha(0xFF000000.toInt(), 0.5f))
        Draw.round(x0, topBarY, maxW, topH, Radius.LG, Theme.card)
        Draw.ring(x0, topBarY, maxW, topH, Radius.LG, Theme.border)
        Draw.rect(x0 + 12, topBarY + 11, 3, 10, Theme.accent)
        Draw.text(I18n.tr("hud.editor.title"), x0 + 22, topBarY + 12, Theme.text)
        Draw.textRight(Draw.fit(I18n.tr("hud.editor.hint"), maxW - 24 - Draw.textWidth(I18n.tr("hud.editor.title")) - 20), x0 + maxW - 12, topBarY + 12, Theme.textSecondary)
        for (w in topWidgets) w.draw(mx, my, 0, height)
        Draw.text(I18n.tr("hud.snap"), snapToggle.x + snapToggle.w + 5, snapToggle.y + (snapToggle.h - 8) / 2, Theme.textSecondary)
        Draw.text(I18n.tr("hud.guides"), guidesToggle.x + guidesToggle.w + 5, guidesToggle.y + (guidesToggle.h - 8) / 2, Theme.textSecondary)
        Draw.text(I18n.tr("hud.grid"), gridSlider.x + gridSlider.w + 5, gridSlider.y + (gridSlider.h - 8) / 2, Theme.textSecondary)
    }

    private fun renderInspector(mx: Int, my: Int) {
        val maxW = (width - 24).coerceAtMost(640)
        val x0 = (width - maxW) / 2
        val b = selBox() ?: return
        Draw.glow(x0, inspectorY, maxW, inspectorH, Colors.alpha(0xFF000000.toInt(), 0.5f))
        Draw.round(x0, inspectorY, maxW, inspectorH, Radius.LG, Theme.card)
        Draw.ring(x0, inspectorY, maxW, inspectorH, Radius.LG, Theme.border)
        Draw.rect(x0 + 12, inspectorY + 11, 3, 10, Theme.accent)
        Draw.text(HudManager.title(b), x0 + 22, inspectorY + 12, Theme.text)
        for (w in inspectorWidgets) w.draw(mx, my, 0, height)
        for (pair in listOf(scaleSlider to I18n.tr("hud.scale"), opacitySlider to I18n.tr("hud.opacity"), visibleToggle to I18n.tr("hud.visible"))) {
            if (pair.first === visibleToggle) Draw.text(pair.second, pair.first.x + pair.first.w + 5, pair.first.y + 4, Theme.textSecondary)
            else Draw.text(pair.second, pair.first.x, pair.first.y - 10, Theme.textSecondary)
        }
        Draw.text(I18n.tr("hud.anchor"), anchorPicker.x, anchorPicker.y - 10, Theme.textSecondary)
    }

    // ── input ───────────────────────────────────────────────────────────────
    private fun boxAt(mx: Int, my: Int): WidgetBox? {
        var best: WidgetBox? = null
        var bestLayer = Int.MIN_VALUE
        for (b in HudManager.active) {
            if (mx >= b.x && mx < b.x + b.sw && my >= b.y && my < b.y + b.sh) {
                val l = HudManager.placement(b.widget).layer
                if (l >= bestLayer) { best = b; bestLayer = l }
            }
        }
        return best
    }

    private fun overToolbar(mx: Int, my: Int): Boolean {
        if (toolsHidden) return false
        val maxW = (width - 24).coerceAtMost(640)
        val x0 = (width - maxW) / 2
        if (mx >= x0 && mx < x0 + maxW && my >= topBarY && my < topBarY + topH) return true
        return selected != null && mx >= x0 && mx < x0 + maxW && my >= inspectorY && my < inspectorY + inspectorH
    }

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = event.x().toInt(); val my = event.y().toInt(); val b = event.button()
        Ui.popup?.let { if (it.mouseClicked(mx, my, b)) return true }
        if (overToolbar(mx, my)) {
            for (w in topWidgets) if (w.contains(mx, my) && w.mouseClicked(mx, my, b)) return true
            if (selected != null) for (w in inspectorWidgets) if (w.contains(mx, my) && w.mouseClicked(mx, my, b)) return true
            return true
        }
        val hit = boxAt(mx, my)
        selected = hit?.widget?.id
        if (hit != null && b == 0) {
            dragging = true
            grabX = mx - hit.x; grabY = my - hit.y
        }
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val mx = event.x().toInt(); val my = event.y().toInt(); val b = event.button()
        for (w in topWidgets) w.mouseReleased(mx, my, b)
        for (w in inspectorWidgets) w.mouseReleased(mx, my, b)
        if (dragging) {
            dragging = false; guideX = -1; guideY = -1
            selBox()?.let { HudManager.reanchorNearest(it, width, height); cfg.markHudDirty() }
        }
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val mx = event.x().toInt(); val my = event.y().toInt(); val b = event.button()
        for (w in topWidgets) w.mouseDragged(mx, my, b, dx.toInt(), dy.toInt())
        for (w in inspectorWidgets) w.mouseDragged(mx, my, b, dx.toInt(), dy.toInt())
        val box = selBox() ?: return true
        if (!dragging) return true
        var nx = mx - grabX
        var ny = my - grabY
        guideX = -1; guideY = -1
        if (snapOn()) {
            val g = grid()
            nx = (nx.toFloat() / g).roundToInt() * g
            ny = (ny.toFloat() / g).roundToInt() * g
        }
        if (guidesOn()) {
            val threshold = 4
            // candidate x lines of the dragged box: left, centre, right
            val cxs = intArrayOf(nx, nx + box.sw / 2, nx + box.sw)
            val cys = intArrayOf(ny, ny + box.sh / 2, ny + box.sh)
            val targetsX = ArrayList<Int>(); val targetsY = ArrayList<Int>()
            targetsX += 0; targetsX += width / 2; targetsX += width
            targetsY += 0; targetsY += height / 2; targetsY += height
            for (o in HudManager.active) if (o !== box) {
                targetsX += o.x; targetsX += o.x + o.sw / 2; targetsX += o.x + o.sw
                targetsY += o.y; targetsY += o.y + o.sh / 2; targetsY += o.y + o.sh
            }
            var bestDx = threshold + 1
            for (c in cxs.indices) for (t in targetsX) { val d = t - cxs[c]; if (abs(d) < abs(bestDx) && abs(d) <= threshold) { bestDx = d; guideX = t } }
            var bestDy = threshold + 1
            for (c in cys.indices) for (t in targetsY) { val d = t - cys[c]; if (abs(d) < abs(bestDy) && abs(d) <= threshold) { bestDy = d; guideY = t } }
            if (abs(bestDx) <= threshold) nx += bestDx
            if (abs(bestDy) <= threshold) ny += bestDy
        }
        nx = nx.coerceIn(0, (width - box.sw).coerceAtLeast(0))
        ny = ny.coerceIn(0, (height - box.sh).coerceAtLeast(0))
        HudManager.setPosition(box, nx, ny, width, height)
        cfg.markHudDirty()
        return true
    }

    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        Ui.popup?.let { if (it.mouseScrolled(x.toInt(), y.toInt(), scrollY)) return true }
        val box = boxAt(x.toInt(), y.toInt()) ?: selBox()
        if (box != null && !overToolbar(x.toInt(), y.toInt())) {
            selected = box.widget.id
            val p = HudManager.placement(box.widget)
            setScale(p, (p.scale + (if (scrollY > 0) 0.1f else -0.1f)).let { (it * 10).roundToInt() / 10f })
        }
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val key = event.key()
        Ui.popup?.let { if (it.keyPressed(key, event.hasControlDown(), event.hasShiftDown())) return true }
        if (key == Keys.TAB) { toolsHidden = !toolsHidden; return true }
        if (key == Keys.ESCAPE) {
            if (selected != null) selected = null else onClose()
            return true
        }
        val box = selBox() ?: return super.keyPressed(event)
        val step = if (event.hasShiftDown()) grid() else 1
        var nx = box.x; var ny = box.y
        when (key) {
            Keys.LEFT -> nx -= step
            Keys.RIGHT -> nx += step
            Keys.UP -> ny -= step
            Keys.DOWN -> ny += step
            else -> return super.keyPressed(event)
        }
        HudManager.setPosition(box, nx.coerceIn(0, (width - box.sw).coerceAtLeast(0)), ny.coerceIn(0, (height - box.sh).coerceAtLeast(0)), width, height)
        HudManager.reanchorNearest(box, width, height)
        cfg.markHudDirty()
        return true
    }

    override fun charTyped(event: CharacterEvent): Boolean = false
}
