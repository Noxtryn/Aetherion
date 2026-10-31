package dev.freelocs.aetherion.hud

import dev.freelocs.aetherion.common.config.HudLayout
import dev.freelocs.aetherion.common.config.WidgetPlacement
import dev.freelocs.aetherion.common.services.I18n
import dev.freelocs.aetherion.core.Aetherion
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Theme
import dev.freelocs.aetherion.platform.Platform
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.resources.Identifier
import kotlin.math.roundToInt

/** Screen rectangle of a laid-out widget, reused between frames to avoid allocation. */
class WidgetBox(val widget: HudWidget) {
    var x = 0; var y = 0; var w = 0; var h = 0
    var scale = 1f
    var text = ""
    val sw get() = (w * scale).roundToInt()
    val sh get() = (h * scale).roundToInt()
}

/**
 * Owns the set of active widgets, resolves their placement (anchor + offset + scale) and draws them.
 * Placement lives in the active profile's [HudLayout]; defaults are applied lazily and only persisted
 * once the user changes something.
 */
object HudManager {
    private const val PAD = 4
    private const val BAR = 2

    private val boxes = LinkedHashMap<String, WidgetBox>()
    private var ordered = emptyArray<WidgetBox>()
    private var orderDirty = true
    private var lastRefresh = 0L
    private val hudId = Identifier.fromNamespaceAndPath("aetherion", "hud")

    val active: Collection<WidgetBox> get() = boxes.values

    fun register(widget: HudWidget) {
        boxes[widget.id] = WidgetBox(widget).also { it.text = widget.sample }
        orderDirty = true
        lastRefresh = 0L
    }

    fun unregister(id: String) {
        boxes.remove(id)
        orderDirty = true
    }

    fun box(id: String) = boxes[id]

    fun install() {
        HudElementRegistry.addLast(hudId) { g, _ ->
            val mc = Platform.mc
            if (Platform.currentScreen() is dev.freelocs.aetherion.gui.editor.AuroraHudEditor) return@addLast
            if (boxes.isEmpty() || !Aetherion.config.isEnabled("hud.core")) return@addLast
            Draw.begin(g, mc.font)
            render(mc.window.guiScaledWidth, mc.window.guiScaledHeight, editing = false, selectedId = null)
        }
    }

    fun placement(widget: HudWidget): WidgetPlacement {
        val layout = Aetherion.config.hudLayout
        return layout.getOrPut(widget.id) {
            WidgetPlacement(anchor = widget.defaultAnchor, x = widget.defaultX, y = widget.defaultY, layer = boxes.size)
        }
    }

    fun markOrderDirty() { orderDirty = true }

    private fun rebuildOrder() {
        val list = boxes.values.toMutableList()
        list.sortBy { placement(it.widget).layer }
        ordered = list.toTypedArray()
        orderDirty = false
    }

    /** Refreshes widget text at the configured interval. [force] ignores the interval (editor, profile change). */
    fun refresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val interval = Aetherion.config.double("performance.render", "hudRefresh").toLong()
        if (!force && now - lastRefresh < interval) return
        lastRefresh = now
        for (b in boxes.values) b.text = runCatching { b.widget.compute() }.getOrDefault(b.widget.sample)
    }

    /** Computes size and screen position of [b] for the given GUI-scaled screen size. */
    fun layout(b: WidgetBox, screenW: Int, screenH: Int) {
        val p = placement(b.widget)
        val text = if (b.text.isEmpty()) b.widget.sample else b.text
        b.w = Draw.textWidth(text) + PAD * 2 + BAR + 2
        b.h = 8 + PAD * 2
        b.scale = p.scale
        val col = p.anchor % 3
        val row = p.anchor / 3
        b.x = (col * screenW / 2f + p.x - col * b.sw / 2f).roundToInt()
        b.y = (row * screenH / 2f + p.y - row * b.sh / 2f).roundToInt()
    }

    /** Offset values that put the widget's top-left at ([x],[y]) for its current anchor. */
    fun setPosition(b: WidgetBox, x: Int, y: Int, screenW: Int, screenH: Int) {
        val p = placement(b.widget)
        val col = p.anchor % 3
        val row = p.anchor / 3
        p.x = x - col * screenW / 2f + col * b.sw / 2f
        p.y = y - row * screenH / 2f + row * b.sh / 2f
        // Refresh geometry now: several input events can arrive within one frame, and the next one must see this position.
        layout(b, screenW, screenH)
    }

    /** Re-anchors to the screen region containing the widget's centre, keeping its on-screen position. */
    fun reanchorNearest(b: WidgetBox, screenW: Int, screenH: Int) {
        val cx = b.x + b.sw / 2f
        val cy = b.y + b.sh / 2f
        val col = (cx / screenW * 3f).toInt().coerceIn(0, 2)
        val row = (cy / screenH * 3f).toInt().coerceIn(0, 2)
        setAnchor(b, row * 3 + col, screenW, screenH)
    }

    fun setAnchor(b: WidgetBox, anchor: Int, screenW: Int, screenH: Int) {
        val x = b.x; val y = b.y
        placement(b.widget).anchor = anchor.coerceIn(0, 8)
        setPosition(b, x, y, screenW, screenH)
    }

    fun render(screenW: Int, screenH: Int, editing: Boolean, selectedId: String?) {
        refresh()
        if (orderDirty) rebuildOrder()
        for (b in ordered) {
            val p = placement(b.widget)
            if (!p.visible && !editing) continue
            layout(b, screenW, screenH)
            drawBox(b, p, editing, b.widget.id == selectedId)
        }
    }

    fun drawBox(b: WidgetBox, p: WidgetPlacement, editing: Boolean, selected: Boolean) {
        val text = if (b.text.isEmpty()) b.widget.sample else b.text
        val prevAlpha = Draw.alpha
        Draw.alpha = prevAlpha * p.opacity * (if (!p.visible) 0.35f else 1f)
        val s = p.scale
        val glass = Aetherion.config.string("cosmetics.theme", "hudStyle") != "minimal"
        val shadow = Aetherion.config.bool("cosmetics.theme", "hudTextShadow")
        Draw.pushScale(b.x.toFloat(), b.y.toFloat(), s)
        if (glass) {
            Draw.round(b.x, b.y, b.w, b.h, 4, 0xB00B1020.toInt())
            Draw.ring(b.x, b.y, b.w, b.h, 4, Colors.alpha(Theme.border, 0.9f))
            Draw.round(b.x + 3, b.y + 3, BAR, b.h - 6, 1, Theme.accent)
        }
        Draw.text(text, b.x + PAD + BAR + (if (glass) 2 else 0), b.y + PAD, b.widget.color(), shadow)
        Draw.popTransform()
        Draw.alpha = prevAlpha
    }

    /** Test/diagnostic helper. */
    fun describe(): String = boxes.values.joinToString { "${it.widget.id}@${placement(it.widget).anchor}" }

    fun title(b: WidgetBox) = I18n.tr(b.widget.titleKey)
}
