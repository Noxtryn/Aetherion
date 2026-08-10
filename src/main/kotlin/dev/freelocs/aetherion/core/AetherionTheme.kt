package dev.freelocs.aetherion.core

/**
 * Central design tokens for Aetherion's config GUI, extracted from the design/style guide
 * (logo, color palette and GUI mockup). Every screen reads colors, radii and layout sizes
 * from here instead of hard-coding them, so the whole GUI restyles from one place.
 *
 * Colors are packed ARGB ints for [net.minecraft.client.gui.GuiGraphics]. Minecraft's 2D
 * renderer has no native rounded-rect or backdrop-blur primitive, so the "glassmorphism"
 * look from the mockup is approximated with translucent fills/borders and a real (not faked)
 * rounded-corner fill helper — see [dev.freelocs.aetherion.gui.GuiUtil.fillRounded].
 */
object AetherionTheme {

    // ── Palette ──────────────────────────────────────────────────────────────
    const val BG_START        = 0xFF0A1428.toInt() // panel background gradient start
    const val BG_END          = 0xFF142850.toInt() // panel background gradient end
    const val SCREEN_BACKDROP = 0x66060B18 // dim over the blurred world behind the panel, rgba(6,11,24,0.40)
    const val ACCENT          = 0xFF3E9EFF.toInt() // primary accent (active nav, links, glow)
    const val SECONDARY       = 0xFF5B6EE1.toInt() // secondary accent (gradients, hover)
    const val DANGER          = 0xFFFF6B6B.toInt() // alerts / low-HP style indicators
    const val TEXT_PRIMARY    = 0xFFE6ECFF.toInt()
    const val TEXT_SECONDARY  = 0xFF8A9BC4.toInt()

    // ── Translucent surfaces ─────────────────────────────────────────────────
    const val PANEL_BG            = 0xB3101E3D.toInt() // rgba(16,30,61,0.70)
    const val SIDEBAR_BG          = 0xBF0A1428.toInt() // rgba(10,20,40,0.75)
    const val CARD_BG             = 0x08FFFFFF          // rgba(255,255,255,0.03)
    const val CARD_BORDER         = 0x14FFFFFF          // rgba(255,255,255,0.08)
    const val PANEL_BORDER        = 0x2E3E9EFF          // rgba(62,158,255,0.18)
    const val ACCENT_BORDER_HOVER = 0x4D3E9EFF          // rgba(62,158,255,0.30)
    const val NAV_HOVER_BG        = 0x143E9EFF          // rgba(62,158,255,0.08)
    const val TOGGLE_OFF          = 0x1FFFFFFF          // rgba(255,255,255,0.12)
    const val GLOW_ACCENT         = 0x803E9EFF.toInt() // rgba(62,158,255,0.50)

    // ── Radii (px), used by GuiUtil.fillRounded ─────────────────────────────
    const val RADIUS_PANEL    = 20
    const val RADIUS_CARD     = 12
    const val RADIUS_NAV      = 8
    const val RADIUS_LOGO_BOX = 16
    const val RADIUS_TOGGLE   = 10 // pill (= track height / 2)

    // ── Layout ───────────────────────────────────────────────────────────────
    const val SIDEBAR_W     = 200
    const val NAV_ITEM_H    = 36
    const val OPTION_CARD_H = 60
    const val TOGGLE_W      = 36
    const val TOGGLE_H      = 20
    const val PADDING       = 16
}
