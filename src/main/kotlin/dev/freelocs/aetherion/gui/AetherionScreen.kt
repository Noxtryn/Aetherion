package dev.freelocs.aetherion.gui

import dev.freelocs.aetherion.core.AetherionMod
import dev.freelocs.aetherion.core.AetherionTheme
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

private data class NavCategory(val id: String, val label: String)

private val LOGO_ICON = Identifier.fromNamespaceAndPath(AetherionMod.MOD_ID, "icon.png")
private const val LOGO_ICON_SIZE = 128

/**
 * Structure-only config screen: sidebar navigation across every planned module, each
 * showing a "Coming soon" placeholder until it ships real options. Styled from
 * [AetherionTheme] — panel/card rounding via [GuiUtil.fillRounded], glow via
 * [GuiUtil.drawGlowBorder] (see those for why: Minecraft's 2D renderer has no native
 * round-rect or backdrop-blur). The world behind the screen stays visible but blurred
 * ([extractBlurredBackground], the same hook vanilla's pause menu uses) so the panel
 * reads as floating in the middle rather than covering a flat background.
 */
class AetherionScreen(private val parent: Screen?) : Screen(Component.literal("Aetherion")) {

    private val categories = listOf(
        NavCategory("hud", "HUD"),
        NavCategory("analytics", "Analytics"),
        NavCategory("notifications", "Notifications"),
        NavCategory("waypoints", "Waypoints"),
        NavCategory("market", "Market"),
        NavCategory("general", "General")
    )

    private var selected = 0
    private var hoverIndex: Int? = null

    private var panelX = 0
    private var panelY = 0
    private var panelW = 0
    private var panelH = 0

    private val headerH = 52

    override fun init() {
        panelW = (width * 0.66).toInt().coerceIn(560, 980)
        panelH = (height * 0.78).toInt().coerceIn(360, 680)
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2
    }

    override fun isPauseScreen() = false

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        return super.keyPressed(keyEvent)
    }

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractBlurredBackground(ctx)
        ctx.fill(0, 0, width, height, AetherionTheme.SCREEN_BACKDROP)
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(ctx, mouseX, mouseY, partialTick)
        renderPanel(ctx, mouseX, mouseY)
    }

    private fun renderPanel(ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
        GuiUtil.drawGlowBorder(ctx, panelX, panelY, panelW, panelH, AetherionTheme.GLOW_ACCENT, AetherionTheme.RADIUS_PANEL)
        GuiUtil.fillRounded(ctx, panelX, panelY, panelW, panelH, AetherionTheme.PANEL_BG, AetherionTheme.RADIUS_PANEL)
        GuiUtil.drawRoundedBorder(ctx, panelX, panelY, panelW, panelH, AetherionTheme.PANEL_BORDER, AetherionTheme.RADIUS_PANEL)

        renderSidebar(ctx, mx, my)
        renderContent(ctx, mx, my)
    }

    private fun renderSidebar(ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
        val sx = panelX
        val sy = panelY
        val sw = AetherionTheme.SIDEBAR_W
        val sh = panelH

        ctx.enableScissor(sx, sy, sx + sw, sy + sh)
        GuiUtil.fillRounded(ctx, sx, sy, sw, sh, AetherionTheme.SIDEBAR_BG, AetherionTheme.RADIUS_PANEL)
        ctx.fill(sx + sw - 1, sy, sx + sw, sy + sh, AetherionTheme.CARD_BORDER)

        // Header: mod logo (icon.png, same asset as the mod icon) + wordmark.
        val badgeSize = 22
        val headerY = sy + 20
        ctx.blit(
            RenderPipelines.GUI_TEXTURED, LOGO_ICON,
            sx + 12, headerY, 0f, 0f,
            badgeSize, badgeSize, LOGO_ICON_SIZE, LOGO_ICON_SIZE, -1
        )
        ctx.text(font, "§lAetherion", sx + 12 + badgeSize + 8, headerY + (badgeSize - 8) / 2, AetherionTheme.TEXT_PRIMARY, false)

        val navY = headerY + badgeSize + 18
        categories.forEachIndexed { i, cat ->
            val itemY = navY + i * AetherionTheme.NAV_ITEM_H
            val isActive = i == selected
            val isHovered = hoverIndex == i

            when {
                isActive -> GuiUtil.fillRounded(ctx, sx + 10, itemY, sw - 20, AetherionTheme.NAV_ITEM_H - 4, AetherionTheme.NAV_HOVER_BG, AetherionTheme.RADIUS_NAV)
                isHovered -> GuiUtil.fillRounded(ctx, sx + 10, itemY, sw - 20, AetherionTheme.NAV_ITEM_H - 4, AetherionTheme.CARD_BG, AetherionTheme.RADIUS_NAV)
            }
            if (isActive) {
                ctx.fill(sx + 10, itemY + 4, sx + 13, itemY + AetherionTheme.NAV_ITEM_H - 8, AetherionTheme.ACCENT)
            }
            val labelColor = if (isActive) AetherionTheme.TEXT_PRIMARY else AetherionTheme.TEXT_SECONDARY
            ctx.text(font, cat.label, sx + 22, itemY + (AetherionTheme.NAV_ITEM_H - 4 - 8) / 2, labelColor, false)
        }
        ctx.disableScissor()
    }

    private fun renderContent(ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
        val cx = panelX + AetherionTheme.SIDEBAR_W
        val contentW = panelW - AetherionTheme.SIDEBAR_W
        val cy = panelY
        val contentH = panelH

        ctx.enableScissor(cx, cy, cx + contentW, cy + contentH)

        val cat = categories[selected]
        ctx.text(font, "§l${cat.label}", cx + AetherionTheme.PADDING, cy + AetherionTheme.PADDING, AetherionTheme.TEXT_PRIMARY, false)

        // Placeholder card — real options land here once the module ships.
        val cardX = cx + AetherionTheme.PADDING
        val cardY = cy + AetherionTheme.PADDING + 28
        val cardW = contentW - AetherionTheme.PADDING * 2
        val cardH = AetherionTheme.OPTION_CARD_H

        GuiUtil.fillRounded(ctx, cardX, cardY, cardW, cardH, AetherionTheme.CARD_BG, AetherionTheme.RADIUS_CARD)
        GuiUtil.drawRoundedBorder(ctx, cardX, cardY, cardW, cardH, AetherionTheme.CARD_BORDER, AetherionTheme.RADIUS_CARD)

        val comingSoon = "Coming soon"
        ctx.text(
            font, comingSoon,
            cardX + (cardW - font.width(comingSoon)) / 2,
            cardY + (cardH - 8) / 2,
            AetherionTheme.TEXT_SECONDARY, false
        )

        ctx.disableScissor()
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubled: Boolean): Boolean {
        if (mouseButtonEvent.button() != 0) return super.mouseClicked(mouseButtonEvent, doubled)

        val idx = navIndexAt(mouseButtonEvent.x().toInt(), mouseButtonEvent.y().toInt())
        if (idx != null) {
            selected = idx
            return true
        }
        return super.mouseClicked(mouseButtonEvent, doubled)
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        hoverIndex = navIndexAt(mouseX.toInt(), mouseY.toInt())
    }

    private fun navIndexAt(mx: Int, my: Int): Int? {
        val sx = panelX
        val badgeSize = 22
        val headerY = panelY + 20
        val navY = headerY + badgeSize + 18
        if (mx !in sx until sx + AetherionTheme.SIDEBAR_W) return null
        val idx = (my - navY) / AetherionTheme.NAV_ITEM_H
        if (my < navY || idx !in categories.indices) return null
        return idx
    }

    override fun toString(): String = "AetherionScreen(v=${AetherionMod.VERSION})"
}
