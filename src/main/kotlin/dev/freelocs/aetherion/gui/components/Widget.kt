package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.common.services.NoticeKind
import dev.freelocs.aetherion.common.services.Notifications
import dev.freelocs.aetherion.gui.animations.StateAnim

/**
 * Base of every Aurora control. State (value, callbacks) lives in subclasses; rendering reads it and
 * the widget's animators. Positions are assigned by the parent layout before [render]/input calls, so
 * a widget never needs to know where it is placed. Coordinates are in UI units.
 */
abstract class Widget {
    var x = 0
    var y = 0
    var w = 0
    var h = 0
    var visible = true
    var enabled = true
    var focused = false
    open val focusable get() = false

    /** Tooltip text while hovered (null = none). */
    var tooltip: (() -> String?)? = null

    val hover = StateAnim()

    fun setBounds(nx: Int, ny: Int, nw: Int, nh: Int) { x = nx; y = ny; w = nw; h = nh }

    /** Preferred height for a given width (used by stacking layouts). */
    open fun preferredHeight(width: Int): Int = h

    fun contains(mx: Int, my: Int) = visible && mx >= x && mx < x + w && my >= y && my < y + h

    /** Updates hover animation and tooltip, then draws. [clip] is the visible region of the parent scroll view. */
    fun draw(mx: Int, my: Int, clipTop: Int, clipBottom: Int) {
        if (!visible) return
        val over = enabled && contains(mx, my) && my >= clipTop && my < clipBottom && Ui.hoverAllowed
        hover.set(over)
        if (over) tooltip?.invoke()?.let { Ui.requestTooltip(it) }
        render(mx, my)
    }

    abstract fun render(mx: Int, my: Int)

    open fun mouseClicked(mx: Int, my: Int, button: Int): Boolean = false
    open fun mouseReleased(mx: Int, my: Int, button: Int): Boolean = false
    open fun mouseDragged(mx: Int, my: Int, button: Int, dx: Int, dy: Int): Boolean = false
    open fun mouseScrolled(mx: Int, my: Int, amount: Double): Boolean = false
    open fun keyPressed(key: Int, ctrl: Boolean, shift: Boolean): Boolean = false
    open fun charTyped(codepoint: Int): Boolean = false
    open fun onBlur() {}
}

/**
 * Screen-wide interaction services: keyboard focus, the single open popup, hover tooltips and toasts.
 * A singleton because exactly one Aurora screen is interactive at a time.
 */
object Ui {
    var focusedWidget: Widget? = null
        private set
    var popup: Popup? = null
        private set
    var hoverAllowed = true

    /** Size of the UI coordinate space this frame (the screen size divided by the UI scale). */
    var viewW = 0
    var viewH = 0

    private var tipText: String? = null
    private var shownTip: String? = null
    private var tipHoverSeconds = 0f

    fun focus(widget: Widget?) {
        if (focusedWidget === widget) return
        focusedWidget?.let { it.focused = false; it.onBlur() }
        focusedWidget = widget
        widget?.focused = true
    }

    fun openPopup(p: Popup) {
        popup?.onClosed()
        popup = p
    }

    fun closePopup() {
        popup?.onClosed()
        popup = null
    }

    fun requestTooltip(text: String) { tipText = text }

    /** Returns the tooltip to display this frame, honouring a short hover delay. */
    fun resolveTooltip(dt: Float): String? {
        val t = tipText
        tipText = null
        if (t == null) { tipHoverSeconds = 0f; shownTip = null; return null }
        if (t != shownTip) { shownTip = t; tipHoverSeconds = 0f }
        tipHoverSeconds += dt
        return if (tipHoverSeconds >= 0.45f) t else null
    }

    fun toast(kind: NoticeKind, message: String) = Notifications.push(kind, message)

    /** Hooks installed by the active screen so widgets can open dialogs and the HUD editor without knowing the screen. */
    var openModal: (AuroraModal) -> Unit = {}
    var openHudEditor: () -> Unit = {}

    fun reset() {
        focusedWidget = null; popup = null; tipText = null; shownTip = null; tipHoverSeconds = 0f; hoverAllowed = true
    }
}

/** Floating UI above the page (dropdown lists, color picker). Rendered in the overlay layer, never clipped. */
interface Popup {
    fun render(mx: Int, my: Int)
    fun mouseClicked(mx: Int, my: Int, button: Int): Boolean
    fun mouseScrolled(mx: Int, my: Int, amount: Double): Boolean = false
    fun mouseDragged(mx: Int, my: Int, button: Int, dx: Int, dy: Int): Boolean = false
    fun mouseReleased(mx: Int, my: Int, button: Int): Boolean = false
    fun keyPressed(key: Int, ctrl: Boolean, shift: Boolean): Boolean = false
    fun charTyped(codepoint: Int): Boolean = false
    fun onClosed() {}
}
