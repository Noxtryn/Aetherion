package dev.freelocs.aetherion.gui.screens

import dev.freelocs.aetherion.common.features.FeatureDefinition
import dev.freelocs.aetherion.common.features.FeatureRegistry
import dev.freelocs.aetherion.common.features.FeatureStatus
import dev.freelocs.aetherion.common.services.I18n
import dev.freelocs.aetherion.common.ui.UiState
import dev.freelocs.aetherion.core.Aetherion
import dev.freelocs.aetherion.gui.animations.AuroraAnimationEngine
import dev.freelocs.aetherion.gui.components.ActionCard
import dev.freelocs.aetherion.gui.components.AuroraButton
import dev.freelocs.aetherion.gui.components.AuroraFeatureCard
import dev.freelocs.aetherion.gui.components.AuroraTabBar
import dev.freelocs.aetherion.gui.components.ButtonStyle
import dev.freelocs.aetherion.gui.components.EmptyState
import dev.freelocs.aetherion.gui.components.HeroCard
import dev.freelocs.aetherion.gui.components.Paragraph
import dev.freelocs.aetherion.gui.components.PerformanceCard
import dev.freelocs.aetherion.gui.components.ProfileCard
import dev.freelocs.aetherion.gui.components.SectionTitle
import dev.freelocs.aetherion.gui.components.StatRow
import dev.freelocs.aetherion.gui.components.Ui
import dev.freelocs.aetherion.gui.components.Widget
import dev.freelocs.aetherion.gui.engine.Icons
import dev.freelocs.aetherion.gui.theme.Colors

/**
 * Builds the widget list for whatever the UI state currently shows. Pages are data-driven: categories,
 * features and settings all come from the registry, so adding a feature needs no GUI code.
 */
object Pages {
    private val autoExpanded = HashSet<String>()

    fun build(ui: UiState): List<Widget> = when {
        ui.searching -> searchPage(ui)
        ui.categoryId == "general" -> dashboard(ui)
        else -> categoryPage(ui)
    }

    private fun cards(defs: List<FeatureDefinition>, ui: UiState): List<Widget> = defs.map { def ->
        if (def.status == FeatureStatus.IMPLEMENTED && !def.toggleable && autoExpanded.add(def.id)) ui.expanded += def.id
        AuroraFeatureCard(def, ui)
    }

    private fun counts(categoryId: String): String {
        val all = FeatureRegistry.featuresIn(categoryId)
        val impl = all.count { it.status == FeatureStatus.IMPLEMENTED }
        val active = all.count { it.status == FeatureStatus.IMPLEMENTED && it.toggleable && Aetherion.config.isEnabled(it) }
        val planned = all.size - impl
        return I18n.tr("category.counts", impl, active, planned)
    }

    private fun categoryPage(ui: UiState): List<Widget> {
        val cat = FeatureRegistry.category(ui.categoryId) ?: return emptyList()
        val out = ArrayList<Widget>()
        out += HeroCard(Icons.byName(cat.icon), { I18n.tr(cat.titleKey) }, { I18n.tr(cat.descriptionKey) }, { counts(cat.id) })

        if (cat.id == "hud") {
            out += ActionCard(Icons.MOVE, { I18n.tr("hud.editor.title") }, { I18n.tr("hud.editor.desc") },
                AuroraButton({ I18n.tr("hud.editor.open") }, ButtonStyle.PRIMARY, Icons.MOVE) { Ui.openHudEditor() })
        }
        if (cat.id == "performance") out += PerformanceCard()

        val all = FeatureRegistry.featuresIn(cat.id)
        val implemented = all.filter { it.status == FeatureStatus.IMPLEMENTED }
        val planned = all.filter { it.status != FeatureStatus.IMPLEMENTED }

        when {
            implemented.isNotEmpty() && planned.isNotEmpty() -> {
                val labels = listOf(I18n.tr("tab.features"), I18n.tr("tab.roadmap"))
                out += AuroraTabBar({ labels }, { if (ui.roadmapTab) 1 else 0 }, { ui.setRoadmapTab(it == 1) })
                if (ui.roadmapTab) { out += Paragraph({ I18n.tr("roadmap.intro") }); out += cards(planned, ui) }
                else out += cards(implemented, ui)
            }
            implemented.isNotEmpty() -> out += cards(implemented, ui)
            planned.isNotEmpty() -> {
                out += Paragraph({ I18n.tr("roadmap.noneImplemented") }, { Colors.WARNING })
                out += SectionTitle({ I18n.tr("tab.roadmap") }, { "${planned.size}" })
                out += cards(planned, ui)
            }
            else -> out += EmptyState(Icons.INFO) { I18n.tr("ui.nothingHere") }
        }
        return out
    }

    private fun dashboard(ui: UiState): List<Widget> {
        val out = ArrayList<Widget>()
        out += HeroCard(
            Icons.DASHBOARD,
            { "Aetherion " + Aetherion.version },
            { I18n.tr("dashboard.tagline") },
            { I18n.tr("dashboard.honesty") },
            listOf(
                AuroraButton({ I18n.tr("hud.editor.open") }, ButtonStyle.PRIMARY, Icons.MOVE) { Ui.openHudEditor() },
                AuroraButton({ I18n.tr("nav.performance") }, ButtonStyle.SECONDARY, Icons.PERFORMANCE) { ui.openCategory("performance") }
            )
        )
        var cachedAt = -10f
        var activeCount = 0
        var implCount = 0
        fun refresh() {
            if (AuroraAnimationEngine.time - cachedAt < 0.5f) return
            cachedAt = AuroraAnimationEngine.time
            activeCount = FeatureRegistry.features.count { it.status == FeatureStatus.IMPLEMENTED && it.toggleable && Aetherion.config.isEnabled(it) }
            implCount = FeatureRegistry.features.count { it.status == FeatureStatus.IMPLEMENTED && it.toggleable }
        }
        out += StatRow(listOf(
            StatRow.Tile({ I18n.tr("stat.features") }, { refresh(); "$activeCount / $implCount" }, { I18n.tr("stat.featuresSub", FeatureRegistry.features.count { it.status != FeatureStatus.IMPLEMENTED }) }),
            StatRow.Tile({ I18n.tr("stat.profile") }, { Aetherion.config.activeProfile }, { I18n.tr("stat.profileSub", Aetherion.config.profileNames.size) }),
            StatRow.Tile({ I18n.tr("stat.api") }, { I18n.tr("stat.apiValue") }, { I18n.tr("stat.apiSub") }, { Colors.WARNING }),
            StatRow.Tile({ I18n.tr("stat.favorites") }, { "${Aetherion.config.favorites.size}" }, { I18n.tr("stat.favoritesSub") })
        ))

        out += SectionTitle({ I18n.tr("nav.favorites") })
        val favs = Aetherion.config.favorites.mapNotNull { FeatureRegistry.feature(it) }
        if (favs.isEmpty()) out += EmptyState(Icons.STAR_OUTLINE) { I18n.tr("dashboard.noFavorites") }
        else out += cards(favs, ui)

        out += SectionTitle({ I18n.tr("profiles.section") })
        out += ProfileCard(ui)

        val general = FeatureRegistry.featuresIn("general")
        out += SectionTitle({ I18n.tr("dashboard.settings") })
        out += cards(general.filter { it.status == FeatureStatus.IMPLEMENTED }, ui)
        val planned = general.filter { it.status != FeatureStatus.IMPLEMENTED }
        if (planned.isNotEmpty()) {
            out += SectionTitle({ I18n.tr("tab.roadmap") }, { "${planned.size}" })
            out += cards(planned, ui)
        }
        return out
    }

    private fun searchPage(ui: UiState): List<Widget> {
        val out = ArrayList<Widget>()
        val results = FeatureRegistry.search(ui.search) { I18n.tr(it) }
        out += SectionTitle({ I18n.tr("search.results", results.size) })
        if (results.isEmpty()) out += EmptyState(Icons.SEARCH) { I18n.tr("search.none", ui.search.trim()) }
        else out += cards(results, ui)
        return out
    }
}
