package dev.freelocs.aetherion.gui.components

import dev.freelocs.aetherion.gui.theme.Metrics

/**
 * Responsive region model of the config screen. Pure arithmetic on the UI-space size, so it can be
 * tested without Minecraft. The sidebar collapses to an icon rail on narrow panels, and the detail
 * panel only docks when the content column would still be comfortably wide.
 */
class AuroraLayout {
    var panelX = 0; var panelY = 0; var panelW = 0; var panelH = 0
    var sidebarW = 0
    var headerH = Metrics.HEADER_H
    var detailW = 0

    /** Main content column (below the header, right of the sidebar, left of the detail panel). */
    var contentX = 0; var contentY = 0; var contentW = 0; var contentH = 0
    var detailX = 0

    var compact = false
        private set

    val mainRight get() = contentX + contentW

    fun compute(viewW: Int, viewH: Int, sidebarCollapsed: Boolean, wantsDetail: Boolean) {
        val margin = if (viewW >= 560 && viewH >= 340) 14 else 0
        panelW = minOf(Metrics.PANEL_MAX_W, viewW - margin * 2)
        panelH = minOf(Metrics.PANEL_MAX_H, viewH - margin * 2)
        panelX = (viewW - panelW) / 2
        panelY = (viewH - panelH) / 2

        compact = sidebarCollapsed || panelW < 620
        sidebarW = if (compact) Metrics.SIDEBAR_COLLAPSED_W else Metrics.SIDEBAR_W

        val remaining = panelW - sidebarW
        detailW = if (wantsDetail && remaining >= 600) Metrics.DETAIL_W else 0

        contentX = panelX + sidebarW
        contentY = panelY + headerH
        contentW = remaining - detailW
        contentH = panelH - headerH
        detailX = contentX + contentW
    }

    /** UI scale that shrinks the whole interface on very small windows (never above 1). */
    companion object {
        fun uiScale(screenW: Int, screenH: Int): Float = minOf(1f, minOf(screenW / 600f, screenH / 360f)).coerceAtLeast(0.75f)
    }
}
