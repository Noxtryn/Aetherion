package dev.freelocs.aetherion.gui

import dev.freelocs.aetherion.autoclicker.AutoClicker
import dev.freelocs.aetherion.autoclicker.MobCatalog
import dev.freelocs.aetherion.autoclicker.MobEntry
import dev.freelocs.aetherion.core.AetherionMod
import dev.freelocs.aetherion.core.AetherionTheme
import dev.freelocs.aetherion.core.ConfigManager
import net.minecraft.client.gui.components.EditBox
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
private const val AUTO_CLICKER_ID = "autoclicker"
private const val SEARCH_H = 20
private const val ROW_H = 22
private const val CHECK_SIZE = 10

/**
 * Config screen: sidebar navigation across every module. "Auto Clicker" (selected by default)
 * holds a searchable list of every mob on the current server — see [MobCatalog] — where you tick
 * the mobs the [AutoClicker] triggers on; the other modules show a "Coming soon" placeholder
 * until they ship real options. Styled from
 * [AetherionTheme] — panel/card rounding via [GuiUtil.fillRounded], glow via
 * [GuiUtil.drawGlowBorder] (see those for why: Minecraft's 2D renderer has no native
 * round-rect or backdrop-blur). The world behind the screen stays visible but blurred
 * ([extractBlurredBackground], the same hook vanilla's pause menu uses) so the panel
 * reads as floating in the middle rather than covering a flat background.
 */
class AetherionScreen(private val parent: Screen?) : Screen(Component.literal("Aetherion")) {

    private val categories = listOf(
        NavCategory(AUTO_CLICKER_ID, "Auto Clicker"),
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

    // ── Auto Clicker mob list state ──────────────────────────────────────────
    private lateinit var searchBox: EditBox
    private var allMobs: List<MobEntry> = emptyList()
    private var filteredMobs: List<MobEntry> = emptyList()
    private var scrollRow = 0
    private var hoverRow: Int? = null
    private var refreshTicks = 0

    private val autoClickerSelected get() = categories[selected].id == AUTO_CLICKER_ID

    override fun init() {
        panelW = (width * 0.66).toInt().coerceIn(560, 980)
        panelH = (height * 0.78).toInt().coerceIn(360, 680)
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2

        val previousQuery = if (::searchBox.isInitialized) searchBox.value else ""
        val (sx, sy, sw, _) = searchFieldBounds()
        searchBox = EditBox(font, sx + 10, sy + (SEARCH_H - 8) / 2, sw - 20, 10, Component.literal("Search"))
        searchBox.setBordered(false)
        searchBox.setMaxLength(64)
        searchBox.setTextColor(AetherionTheme.TEXT_PRIMARY)
        searchBox.setHint(Component.literal("Mob suchen…").withStyle(EditBox.SEARCH_HINT_STYLE))
        searchBox.value = previousQuery
        searchBox.setResponder { applyFilter() }
        searchBox.visible = autoClickerSelected
        addRenderableWidget(searchBox)
        if (autoClickerSelected) setInitialFocus(searchBox)

        refreshMobs()
    }

    override fun tick() {
        super.tick()
        // New mobs may be discovered while the screen is open (it doesn't pause the game).
        if (++refreshTicks % 20 == 0) refreshMobs()
    }

    override fun isPauseScreen() = false

    override fun onClose() {
        ConfigManager.save()
        AutoClicker.saveDiscoveries()
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

        if (cat.id == AUTO_CLICKER_ID) {
            renderAutoClicker(ctx, cx, contentW)
            ctx.disableScissor()
            return
        }

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

    // ── Auto Clicker page ────────────────────────────────────────────────────

    private data class Bounds(val x: Int, val y: Int, val w: Int, val h: Int)

    private fun searchFieldBounds(): Bounds {
        val cx = panelX + AetherionTheme.SIDEBAR_W
        val contentW = panelW - AetherionTheme.SIDEBAR_W
        return Bounds(cx + AetherionTheme.PADDING, panelY + AetherionTheme.PADDING + 36, contentW - AetherionTheme.PADDING * 2, SEARCH_H)
    }

    private fun listBounds(): Bounds {
        val search = searchFieldBounds()
        val top = search.y + search.h + 8
        return Bounds(search.x, top, search.w, panelY + panelH - AetherionTheme.PADDING - top)
    }

    private val visibleRows get() = (listBounds().h / ROW_H).coerceAtLeast(1)

    private val maxScroll get() = (filteredMobs.size - visibleRows).coerceAtLeast(0)

    private fun refreshMobs() {
        allMobs = MobCatalog.entries()
        applyFilter()
    }

    private fun applyFilter() {
        val query = if (::searchBox.isInitialized) searchBox.value.trim().lowercase() else ""
        filteredMobs = if (query.isEmpty()) allMobs else allMobs.filter {
            query in it.label.lowercase() || query in it.detail.lowercase()
        }
        scrollRow = scrollRow.coerceIn(0, maxScroll)
    }

    private fun renderAutoClicker(ctx: GuiGraphicsExtractor, cx: Int, contentW: Int) {
        val selectedKeys = ConfigManager.config.autoClicker.selected
        val server = MobCatalog.serverKey()
        val summary = "${selectedKeys.size} ausgewählt · Server: $server"
        ctx.text(font, summary, cx + AetherionTheme.PADDING, panelY + AetherionTheme.PADDING + 14, AetherionTheme.TEXT_SECONDARY, false)

        val search = searchFieldBounds()
        val searchBorder = if (searchBox.isFocused) AetherionTheme.ACCENT_BORDER_HOVER else AetherionTheme.CARD_BORDER
        GuiUtil.fillRounded(ctx, search.x, search.y, search.w, search.h, AetherionTheme.CARD_BG, AetherionTheme.RADIUS_NAV)
        GuiUtil.drawRoundedBorder(ctx, search.x, search.y, search.w, search.h, searchBorder, AetherionTheme.RADIUS_NAV)

        val list = listBounds()
        if (filteredMobs.isEmpty()) {
            val empty = "Keine Mobs gefunden"
            ctx.text(font, empty, list.x + (list.w - font.width(empty)) / 2, list.y + 12, AetherionTheme.TEXT_SECONDARY, false)
            return
        }

        ctx.enableScissor(list.x, list.y, list.x + list.w, list.y + list.h)
        val end = (scrollRow + visibleRows).coerceAtMost(filteredMobs.size)
        for (i in scrollRow until end) {
            val entry = filteredMobs[i]
            val rowY = list.y + (i - scrollRow) * ROW_H
            val rowW = list.w - 8
            val isChecked = entry.key in selectedKeys

            when {
                isChecked -> GuiUtil.fillRounded(ctx, list.x, rowY, rowW, ROW_H - 2, AetherionTheme.NAV_HOVER_BG, AetherionTheme.RADIUS_NAV)
                hoverRow == i -> GuiUtil.fillRounded(ctx, list.x, rowY, rowW, ROW_H - 2, AetherionTheme.CARD_BG, AetherionTheme.RADIUS_NAV)
            }

            val boxX = list.x + 8
            val boxY = rowY + (ROW_H - 2 - CHECK_SIZE) / 2
            GuiUtil.drawRoundedBorder(ctx, boxX, boxY, CHECK_SIZE, CHECK_SIZE, if (isChecked) AetherionTheme.ACCENT else AetherionTheme.TEXT_SECONDARY, 2)
            if (isChecked) GuiUtil.fillRounded(ctx, boxX + 2, boxY + 2, CHECK_SIZE - 4, CHECK_SIZE - 4, AetherionTheme.ACCENT, 1)

            val textY = rowY + (ROW_H - 2 - 8) / 2
            ctx.text(font, entry.label, boxX + CHECK_SIZE + 8, textY, AetherionTheme.TEXT_PRIMARY, false)
            if (entry.seenOnServer) {
                val dotX = boxX + CHECK_SIZE + 8 + font.width(entry.label) + 6
                GuiUtil.fillRounded(ctx, dotX, textY + 2, 4, 4, AetherionTheme.ACCENT, 2)
            }
            ctx.text(font, entry.detail, list.x + rowW - 8 - font.width(entry.detail), textY, AetherionTheme.TEXT_SECONDARY, false)
        }

        // Scrollbar
        if (maxScroll > 0) {
            val trackX = list.x + list.w - 3
            ctx.fill(trackX, list.y, trackX + 3, list.y + list.h, AetherionTheme.CARD_BORDER)
            val thumbH = (list.h * visibleRows / filteredMobs.size).coerceAtLeast(16)
            val thumbY = list.y + (list.h - thumbH) * scrollRow / maxScroll
            ctx.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, AetherionTheme.ACCENT)
        }
        ctx.disableScissor()
    }

    private fun mobRowAt(mx: Int, my: Int): Int? {
        if (!autoClickerSelected) return null
        val list = listBounds()
        if (mx !in list.x until list.x + list.w - 8 || my !in list.y until list.y + visibleRows * ROW_H) return null
        val idx = scrollRow + (my - list.y) / ROW_H
        return idx.takeIf { it in filteredMobs.indices }
    }

    private fun toggleMob(entry: MobEntry) {
        val selectedKeys = ConfigManager.config.autoClicker.selected
        if (!selectedKeys.remove(entry.key)) selectedKeys += entry.key
        ConfigManager.save()
    }

    private fun selectCategory(idx: Int) {
        selected = idx
        searchBox.visible = autoClickerSelected
        if (!autoClickerSelected && focused === searchBox) focused = null
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubled: Boolean): Boolean {
        if (mouseButtonEvent.button() != 0) return super.mouseClicked(mouseButtonEvent, doubled)

        val mx = mouseButtonEvent.x().toInt()
        val my = mouseButtonEvent.y().toInt()
        val idx = navIndexAt(mx, my)
        if (idx != null) {
            selectCategory(idx)
            return true
        }
        val row = mobRowAt(mx, my)
        if (row != null) {
            toggleMob(filteredMobs[row])
            return true
        }
        return super.mouseClicked(mouseButtonEvent, doubled)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (autoClickerSelected && scrollY != 0.0) {
            val step = if (scrollY > 0) -3 else 3
            scrollRow = (scrollRow + step).coerceIn(0, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        hoverIndex = navIndexAt(mouseX.toInt(), mouseY.toInt())
        hoverRow = mobRowAt(mouseX.toInt(), mouseY.toInt())
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
