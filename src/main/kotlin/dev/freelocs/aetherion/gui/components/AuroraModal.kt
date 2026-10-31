package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.common.services.I18n
import dev.freelocs.aetherion.common.services.Notifications
import dev.freelocs.aetherion.gui.animations.Spring
import dev.freelocs.aetherion.gui.engine.Draw
import dev.freelocs.aetherion.gui.engine.Icons
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Radius
import dev.freelocs.aetherion.gui.theme.Theme
import dev.freelocs.aetherion.platform.Keys

class ModalAction(val label: () -> String, val style: ButtonStyle = ButtonStyle.SECONDARY, val closes: Boolean = true, val run: () -> Unit = {})

/**
 * Centred dialog with scrim, spring scale-in and fade. Owns its buttons and an optional body widget.
 * While a modal is open the screen routes all input to it.
 */
class AuroraModal(
    private val title: () -> String,
    private val message: (() -> String)? = null,
    private val actions: List<ModalAction>,
    private val body: Widget? = null,
    private val bodyHeight: Int = 0,
    private val width: Int = 300
) {
    private val open = Spring(0f, 380f, 30f)
    var closing = false
        private set
    val finished get() = closing && open.value < 0.02f

    private val buttons = actions.map { a ->
        AuroraButton(a.label, a.style) { a.run(); if (a.closes) dismiss() }
    }

    private fun messageLines() = message?.let { TextWrap.wrap(it(), width - 32) } ?: emptyList()
    private fun boxH() = 16 + 12 + 14 + messageLines().size * 10 + (if (body != null) bodyHeight + 10 else 0) + 14 + 22 + 16

    fun dismiss() { closing = true; Ui.focus(null) }

    /** Gives keyboard focus to the body (e.g. a prompt's text field) once the modal appears. */
    fun focusBody() { body?.let { if (it.focusable) Ui.focus(it) } }

    fun render(mx: Int, my: Int) {
        open.target = if (closing) 0f else 1f
        val t = open.update().coerceIn(0f, 1.05f)
        val a = t.coerceIn(0f, 1f)
        val prev = Draw.alpha
        Draw.alpha = prev * a
        Draw.rect(0, 0, Ui.viewW, Ui.viewH, Colors.alpha(Theme.scrim, 1f))
        val bh = boxH()
        val bx = (Ui.viewW - width) / 2
        val by = (Ui.viewH - bh) / 2
        Draw.pushScale(Ui.viewW / 2f, Ui.viewH / 2f, 0.94f + 0.06f * t)
        Draw.glow(bx, by, width, bh, Colors.alpha(0xFF000000.toInt(), 0.6f))
        Draw.round(bx, by, width, bh, Radius.XL, Theme.card)
        Draw.ring(bx, by, width, bh, Radius.XL, Theme.border)
        Draw.rect(bx + 16, by + 16, 3, 12, Theme.accent)
        Draw.text(title(), bx + 26, by + 18, Theme.text)
        var y = by + 16 + 12 + 10
        for (line in messageLines()) { Draw.text(line, bx + 16, y, Theme.textSecondary); y += 10 }
        if (body != null) {
            y += 10
            body.setBounds(bx + 16, y, width - 32, bodyHeight)
            body.draw(mx, my, 0, Ui.viewH)
            y += bodyHeight
        }
        val by2 = by + bh - 16 - 22
        var bxr = bx + width - 16
        for (i in buttons.indices.reversed()) {
            val b = buttons[i]
            val bw = b.naturalWidth().coerceAtLeast(64)
            bxr -= bw
            b.setBounds(bxr, by2, bw, 22)
            b.draw(mx, my, 0, Ui.viewH)
            bxr -= 8
        }
        Draw.popTransform()
        Draw.alpha = prev
    }

    fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (closing) return true
        for (b in buttons) if (b.mouseClicked(mx, my, button)) return true
        body?.mouseClicked(mx, my, button)
        return true
    }

    fun mouseReleased(mx: Int, my: Int, button: Int): Boolean {
        for (b in buttons) b.mouseReleased(mx, my, button)
        body?.mouseReleased(mx, my, button)
        return true
    }

    fun mouseDragged(mx: Int, my: Int, button: Int, dx: Int, dy: Int): Boolean { body?.mouseDragged(mx, my, button, dx, dy); return true }
    fun mouseScrolled(mx: Int, my: Int, amount: Double): Boolean { body?.mouseScrolled(mx, my, amount); return true }

    fun keyPressed(key: Int, ctrl: Boolean, shift: Boolean): Boolean {
        if (body != null && body.focused && body.keyPressed(key, ctrl, shift)) {
            if (key == Keys.ENTER || key == Keys.ENTER_NUMPAD) triggerPrimary()
            return true
        }
        when (key) {
            Keys.ESCAPE -> dismiss()
            Keys.ENTER, Keys.ENTER_NUMPAD -> triggerPrimary()
        }
        return true
    }

    fun charTyped(cp: Int): Boolean { body?.charTyped(cp); return true }

    private fun triggerPrimary() {
        val idx = actions.indexOfLast { it.style == ButtonStyle.PRIMARY || it.style == ButtonStyle.DANGER }
        if (idx >= 0) { actions[idx].run(); if (actions[idx].closes) dismiss() }
        else dismiss()
    }
}

/** Factories for the dialogs the UI needs. */
object Modals {
    fun confirm(title: String, message: String, confirmLabel: String, danger: Boolean, onConfirm: () -> Unit) = AuroraModal(
        { title }, { message },
        listOf(
            ModalAction({ I18n.tr("ui.cancel") }),
            ModalAction({ confirmLabel }, if (danger) ButtonStyle.DANGER else ButtonStyle.PRIMARY, run = onConfirm)
        )
    )

    fun info(title: String, message: String) = AuroraModal({ title }, { message }, listOf(ModalAction({ I18n.tr("ui.ok") }, ButtonStyle.PRIMARY)))

    fun prompt(title: String, message: String, initial: String, confirmLabel: String, onSubmit: (String) -> Unit): AuroraModal {
        lateinit var field: AuroraTextField
        field = AuroraTextField({ I18n.tr("ui.name") }, 24)
        field.setText(initial)
        return AuroraModal(
            { title }, { message },
            listOf(
                ModalAction({ I18n.tr("ui.cancel") }),
                ModalAction({ confirmLabel }, ButtonStyle.PRIMARY) { onSubmit(field.text) }
            ),
            body = field, bodyHeight = 22
        )
    }

    fun notifications(onClear: () -> Unit): AuroraModal = AuroraModal(
        { I18n.tr("ui.notifications") }, null,
        listOf(
            ModalAction({ I18n.tr("ui.clear") }, closes = false, run = onClear),
            ModalAction({ I18n.tr("ui.close") }, ButtonStyle.PRIMARY)
        ),
        body = NotificationList(), bodyHeight = 140, width = 340
    ).also { Notifications.markAllRead() }
}

/** Read-only list of recent notifications, newest first. */
private class NotificationList : Widget() {
    private val scroll = AuroraScrollbar()

    override fun render(mx: Int, my: Int) {
        val items = Notifications.all
        if (items.isEmpty()) {
            Draw.textCentered(I18n.tr("ui.noNotifications"), x + w / 2, y + h / 2 - 4, Theme.textSecondary)
            return
        }
        val rowH = 24
        scroll.setViewport(x, y, w, h, items.size * rowH)
        scroll.update()
        Draw.pushClip(x, y, w, h)
        val off = scroll.offset
        for ((i, n) in items.withIndex()) {
            val ry = y + i * rowH - off
            if (ry + rowH < y || ry > y + h) continue
            val color = when (n.kind) {
                dev.freelocs.aetherion.common.services.NoticeKind.SUCCESS -> Colors.SUCCESS
                dev.freelocs.aetherion.common.services.NoticeKind.WARNING -> Colors.WARNING
                dev.freelocs.aetherion.common.services.NoticeKind.ERROR -> Colors.DANGER
                else -> Theme.accent
            }
            Draw.round(x, ry + 1, w - 6, rowH - 3, Radius.MD, Colors.alpha(Theme.surface, 0.7f))
            Draw.rect(x + 6, ry + 7, 2, rowH - 15, color)
            Draw.icon(Icons.INFO, x + 13, ry + (rowH - 8) / 2, 1, color)
            Draw.textMiddle(Draw.fit(n.message, w - 40), x + 26, ry + 1, rowH - 3, Theme.text)
        }
        Draw.popClip()
        scroll.render(mx, my)
    }

    override fun mouseScrolled(mx: Int, my: Int, amount: Double): Boolean { scroll.wheel(amount); return true }
    override fun mouseClicked(mx: Int, my: Int, button: Int) = scroll.mouseClicked(mx, my, button)
    override fun mouseDragged(mx: Int, my: Int, button: Int, dx: Int, dy: Int) = scroll.mouseDragged(my)
    override fun mouseReleased(mx: Int, my: Int, button: Int) = scroll.mouseReleased()
}
