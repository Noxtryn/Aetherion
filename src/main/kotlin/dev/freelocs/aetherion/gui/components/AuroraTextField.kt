package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.gui.animations.AuroraAnimationEngine
import dev.freelocs.aetherion.gui.animations.StateAnim
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.engine.Icons
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Metrics
import dev.freelocs.aetherion.gui.theme.Radius
import dev.freelocs.aetherion.gui.theme.Theme
import dev.freelocs.aetherion.platform.Keys
import dev.freelocs.aetherion.platform.Platform

/**
 * Single-line text input with caret, selection, clipboard and horizontal scrolling. Base for the
 * search field and the hex/name inputs in the color picker and modals.
 */
open class AuroraTextField(
    private val placeholder: () -> String,
    private val maxLength: Int = 64,
    private val accepts: (Int) -> Boolean = { true },
    private val onChange: (String) -> Unit = {},
    private val onSubmit: (String) -> Unit = {}
) : Widget() {
    var text = ""
        private set
    private var cursor = 0
    private var anchor = 0
    private var scrollX = 0
    private var caretPx = 0
    private var selecting = false
    private val focusAnim = StateAnim()
    override val focusable get() = true

    /** Horizontal space reserved left/right of the text (search icon, clear button). */
    protected open val padLeft get() = 7
    protected open val padRight get() = 7

    init { h = Metrics.CONTROL_H }

    override fun preferredHeight(width: Int) = Metrics.CONTROL_H

    fun setText(value: String, notify: Boolean = false) {
        val t = value.take(maxLength)
        if (t == text) return
        text = t
        cursor = cursor.coerceAtMost(t.length); anchor = cursor
        recomputeCaret()
        if (notify) onChange(text)
    }

    fun clear() { setText("", notify = true); cursor = 0; anchor = 0; scrollX = 0 }

    private fun recomputeCaret() { caretPx = Draw.textWidth(text.substring(0, cursor.coerceIn(0, text.length))) }

    private fun hasSelection() = cursor != anchor
    private fun selStart() = minOf(cursor, anchor)
    private fun selEnd() = maxOf(cursor, anchor)

    private fun replaceSelection(insert: String) {
        val s = selStart(); val e = selEnd()
        val room = maxLength - (text.length - (e - s))
        val ins = insert.take(room.coerceAtLeast(0))
        text = text.substring(0, s) + ins + text.substring(e)
        cursor = s + ins.length; anchor = cursor
        recomputeCaret()
        onChange(text)
    }

    private fun move(to: Int, extend: Boolean) {
        cursor = to.coerceIn(0, text.length)
        if (!extend) anchor = cursor
        recomputeCaret()
    }

    private fun wordLeft(): Int { var i = cursor; while (i > 0 && text[i - 1] == ' ') i--; while (i > 0 && text[i - 1] != ' ') i--; return i }
    private fun wordRight(): Int { var i = cursor; while (i < text.length && text[i] != ' ') i++; while (i < text.length && text[i] == ' ') i++; return i }

    private fun indexAt(mx: Int): Int {
        val rel = mx - (x + padLeft) + scrollX
        var best = 0
        for (i in 0..text.length) {
            if (Draw.textWidth(text.substring(0, i)) <= rel) best = i else break
        }
        // snap to the nearer boundary
        if (best < text.length) {
            val a = Draw.textWidth(text.substring(0, best)); val b = Draw.textWidth(text.substring(0, best + 1))
            if (rel - a > b - rel) best++
        }
        return best
    }

    override fun render(mx: Int, my: Int) {
        val hv = hover.update()
        val fo = focusAnim.apply { set(focused) }.update()
        Draw.round(x, y, w, h, Radius.MD, Colors.lerp(Theme.surface, Theme.card, 0.35f + 0.4f * hv))
        if (fo > 0.01f && Theme.glow > 0f) Draw.glow(x, y, w, h, Colors.alpha(Theme.accent, 0.3f * fo * Theme.glow), big = false)
        Draw.ring(x, y, w, h, Radius.MD, Colors.lerp(Theme.border, Theme.accent, fo))

        val innerX = x + padLeft
        val innerW = w - padLeft - padRight
        // keep the caret visible
        if (caretPx - scrollX > innerW - 2) scrollX = caretPx - innerW + 2
        if (caretPx - scrollX < 0) scrollX = caretPx
        if (Draw.textWidth(text) <= innerW) scrollX = 0

        Draw.pushClip(innerX, y, innerW, h)
        if (text.isEmpty() && !focused) {
            Draw.textMiddle(placeholder(), innerX, y, h, Colors.alpha(Theme.textSecondary, 0.65f))
        } else {
            if (hasSelection() && focused) {
                val a = Draw.textWidth(text.substring(0, selStart())); val b = Draw.textWidth(text.substring(0, selEnd()))
                Draw.rect(innerX + a - scrollX, y + 4, b - a, h - 8, Colors.alpha(Theme.accent, 0.35f))
            }
            Draw.textMiddle(text, innerX - scrollX, y, h, Theme.text)
            if (focused && !hasSelection() && ((AuroraAnimationEngine.time * 1.8f).toInt() % 2 == 0 || AuroraAnimationEngine.reducedMotion)) {
                Draw.rect(innerX + caretPx - scrollX, y + 5, 1, h - 10, Theme.accent)
            }
        }
        Draw.popClip()
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!contains(mx, my)) { return false }
        if (button != 0) return false
        Ui.focus(this)
        move(indexAt(mx), extend = false)
        selecting = true
        return true
    }

    override fun mouseDragged(mx: Int, my: Int, button: Int, dx: Int, dy: Int): Boolean {
        if (!selecting) return false
        move(indexAt(mx), extend = true); return true
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int): Boolean { val was = selecting; selecting = false; return was }

    override fun charTyped(codepoint: Int): Boolean {
        if (!focused || codepoint < 32 || codepoint == 127 || !accepts(codepoint)) return false
        replaceSelection(String(Character.toChars(codepoint)))
        return true
    }

    override fun keyPressed(key: Int, ctrl: Boolean, shift: Boolean): Boolean {
        if (!focused) return false
        when {
            ctrl && key == Keys.A -> { anchor = 0; move(text.length, extend = true) }
            ctrl && key == Keys.C -> if (hasSelection()) Platform.clipboard = text.substring(selStart(), selEnd())
            ctrl && key == Keys.X -> if (hasSelection()) { Platform.clipboard = text.substring(selStart(), selEnd()); replaceSelection("") }
            ctrl && key == Keys.V -> replaceSelection(Platform.clipboard.filter { it.code >= 32 && accepts(it.code) })
            key == Keys.BACKSPACE -> {
                if (hasSelection()) replaceSelection("")
                else if (cursor > 0) { anchor = if (ctrl) wordLeft() else cursor - 1; replaceSelection("") }
            }
            key == Keys.DELETE -> {
                if (hasSelection()) replaceSelection("")
                else if (cursor < text.length) { anchor = if (ctrl) wordRight() else cursor + 1; val c = cursor; cursor = anchor; anchor = c; replaceSelection("") }
            }
            key == Keys.LEFT -> move(if (ctrl) wordLeft() else if (hasSelection() && !shift) selStart() else cursor - 1, shift)
            key == Keys.RIGHT -> move(if (ctrl) wordRight() else if (hasSelection() && !shift) selEnd() else cursor + 1, shift)
            key == Keys.HOME -> move(0, shift)
            key == Keys.END -> move(text.length, shift)
            key == Keys.ENTER || key == Keys.ENTER_NUMPAD -> onSubmit(text)
            key == Keys.ESCAPE -> { Ui.focus(null) }
            else -> return key != Keys.TAB && key != Keys.UP && key != Keys.DOWN && !ctrl
        }
        return true
    }
}

/** Text field with a magnifier on the left and a clear button on the right while non-empty. */
class AuroraSearchField(placeholder: () -> String, onChange: (String) -> Unit) :
    AuroraTextField(placeholder, maxLength = 48, onChange = onChange) {
    override val padLeft get() = 22
    override val padRight get() = 20

    override fun render(mx: Int, my: Int) {
        super.render(mx, my)
        Draw.icon(Icons.SEARCH, x + 8, y + (h - 8) / 2, 1, Colors.lerp(Theme.textSecondary, Theme.accent, if (focused) 1f else 0f))
        if (text.isNotEmpty()) {
            val over = mx >= x + w - 18 && mx < x + w - 4 && my >= y && my < y + h
            Draw.icon(Icons.CROSS, x + w - 14, y + (h - 8) / 2, 1, if (over) Theme.text else Theme.textSecondary)
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (text.isNotEmpty() && contains(mx, my) && mx >= x + w - 18 && button == 0) { clear(); Ui.focus(this); return true }
        return super.mouseClicked(mx, my, button)
    }
}
