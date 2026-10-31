package dev.freelocs.aetherion.core

import dev.freelocs.aetherion.common.data.Roadmap
import dev.freelocs.aetherion.common.features.CategoryDefinition
import dev.freelocs.aetherion.common.features.FeatureDefinition
import dev.freelocs.aetherion.common.features.FeatureRegistry
import dev.freelocs.aetherion.common.features.GroupDefinition
import dev.freelocs.aetherion.common.features.SettingDefinition
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.hud.ClockFeature
import dev.freelocs.aetherion.hud.CoordsFeature
import dev.freelocs.aetherion.hud.FpsFeature

/**
 * Everything Aetherion registers at startup: navigation groups, categories, the features that are
 * actually implemented, and the roadmap entries (planned, never enabled).
 */
object Catalog {
    const val OPEN_KEY_DEFAULT = "key.keyboard.right.shift"

    fun registerAll() {
        FeatureRegistry.clear()
        registerGroups()
        registerCategories()
        registerImplemented()
        Roadmap.load().forEach(FeatureRegistry::registerFeature)
    }

    private fun registerGroups() {
        FeatureRegistry.registerGroup(GroupDefinition("main", "group.main"))
        FeatureRegistry.registerGroup(GroupDefinition("skyblock", "group.skyblock"))
        FeatureRegistry.registerGroup(GroupDefinition("system", "group.system"))
    }

    private fun registerCategories() {
        fun cat(id: String, group: String, icon: String) =
            FeatureRegistry.registerCategory(CategoryDefinition(id, group, "category.$id.title", "category.$id.desc", icon))
        cat("general", "main", "dashboard")
        cat("hud", "main", "hud")
        cat("mining", "skyblock", "mining")
        cat("farming", "skyblock", "farming")
        cat("combat", "skyblock", "combat")
        cat("dungeons", "skyblock", "dungeons")
        cat("kuudra", "skyblock", "kuudra")
        cat("fishing", "skyblock", "fishing")
        cat("foraging", "skyblock", "foraging")
        cat("rift", "skyblock", "rift")
        cat("economy", "skyblock", "economy")
        cat("collections", "skyblock", "collections")
        cat("events", "skyblock", "events")
        cat("shards", "skyblock", "shards")
        cat("cosmetics", "system", "cosmetics")
        cat("performance", "system", "performance")
    }

    private fun toggle(id: String, default: Boolean, desc: Boolean = false) =
        SettingDefinition.Toggle(id, "setting.$id.title", if (desc) "setting.$id.desc" else null, default)

    private fun registerImplemented() {
        val f = FeatureRegistry::registerFeature

        f(FeatureDefinition(
            id = "general.interface", categoryId = "general",
            titleKey = "feature.general.interface.title", descriptionKey = "feature.general.interface.desc",
            defaultEnabled = true, toggleable = false, global = true,
            settings = listOf(
                SettingDefinition.Choice("language", "setting.language.title", "setting.language.desc", listOf("auto", "en", "de"), "auto", "option.language"),
                SettingDefinition.Keybind("openKey", "setting.openKey.title", "setting.openKey.desc", OPEN_KEY_DEFAULT),
                toggle("tooltips", true),
                toggle("toasts", true)
            )
        ))

        f(FeatureDefinition(
            id = "hud.core", categoryId = "hud",
            titleKey = "feature.hud.core.title", descriptionKey = "feature.hud.core.desc",
            defaultEnabled = true, compatibilityKey = "compat.hud.core",
            settings = listOf(
                SettingDefinition.Slider("grid", "setting.grid.title", "setting.grid.desc", 2.0, 32.0, 2.0, 8.0, suffix = " px"),
                toggle("snap", true),
                toggle("guides", true),
                SettingDefinition.Keybind("editorKey", "setting.editorKey.title", "setting.editorKey.desc", "")
            )
        ))
        f(FeatureDefinition(
            id = "hud.fps", categoryId = "hud",
            titleKey = "feature.hud.fps.title", descriptionKey = "feature.hud.fps.desc",
            defaultEnabled = false, dependencies = listOf("hud.core"), factory = ::FpsFeature,
            settings = listOf(toggle("label", true), SettingDefinition.Color("color", "setting.color.title", null, Colors.ICE))
        ))
        f(FeatureDefinition(
            id = "hud.clock", categoryId = "hud",
            titleKey = "feature.hud.clock.title", descriptionKey = "feature.hud.clock.desc",
            defaultEnabled = false, dependencies = listOf("hud.core"), factory = ::ClockFeature,
            settings = listOf(toggle("use24h", true), toggle("seconds", false), SettingDefinition.Color("color", "setting.color.title", null, Colors.TEXT))
        ))
        f(FeatureDefinition(
            id = "hud.coords", categoryId = "hud",
            titleKey = "feature.hud.coords.title", descriptionKey = "feature.hud.coords.desc",
            defaultEnabled = false, dependencies = listOf("hud.core"), factory = ::CoordsFeature,
            settings = listOf(
                SettingDefinition.Slider("decimals", "setting.decimals.title", null, 0.0, 2.0, 1.0, 0.0),
                SettingDefinition.Color("color", "setting.color.title", null, Colors.VIOLET)
            )
        ))

        f(FeatureDefinition(
            id = "cosmetics.theme", categoryId = "cosmetics",
            titleKey = "feature.cosmetics.theme.title", descriptionKey = "feature.cosmetics.theme.desc",
            defaultEnabled = true, toggleable = false, global = true,
            settings = listOf(
                SettingDefinition.Choice("variant", "setting.variant.title", null, listOf("aurora", "midnight"), "aurora", "option.variant"),
                SettingDefinition.Choice("accent", "setting.accent.title", "setting.accent.desc", listOf("pink", "ice", "violet", "custom"), "pink", "option.accent"),
                SettingDefinition.Color("customAccent", "setting.customAccent.title", "setting.customAccent.desc", Colors.PINK),
                SettingDefinition.Slider("glow", "setting.glow.title", "setting.glow.desc", 0.0, 100.0, 5.0, 60.0, suffix = " %"),
                SettingDefinition.Choice("hudStyle", "setting.hudStyle.title", null, listOf("glass", "minimal"), "glass", "option.hudStyle"),
                toggle("hudTextShadow", true)
            )
        ))

        f(FeatureDefinition(
            id = "performance.render", categoryId = "performance",
            titleKey = "feature.performance.render.title", descriptionKey = "feature.performance.render.desc",
            defaultEnabled = true, toggleable = false, global = true,
            settings = listOf(
                toggle("reducedMotion", false, desc = true),
                toggle("decorative", true, desc = true),
                toggle("backdropBlur", true, desc = true),
                toggle("smoothScrolling", true),
                SettingDefinition.Slider("hudRefresh", "setting.hudRefresh.title", "setting.hudRefresh.desc", 50.0, 1000.0, 50.0, 250.0, suffix = " ms")
            )
        ))
    }
}
