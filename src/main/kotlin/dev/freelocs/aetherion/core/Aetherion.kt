package dev.freelocs.aetherion.core

import dev.freelocs.aetherion.common.config.ConfigStore
import dev.freelocs.aetherion.common.events.EventBus
import dev.freelocs.aetherion.common.events.LanguageChangedEvent
import dev.freelocs.aetherion.common.events.ProfileChangedEvent
import dev.freelocs.aetherion.common.events.SettingChangedEvent
import dev.freelocs.aetherion.common.features.FeatureManager
import dev.freelocs.aetherion.common.features.FeatureRegistry
import dev.freelocs.aetherion.common.services.I18n
import dev.freelocs.aetherion.gui.animations.AuroraAnimationEngine
import dev.freelocs.aetherion.gui.theme.AccentPreset
import dev.freelocs.aetherion.gui.theme.AuroraThemeManager
import dev.freelocs.aetherion.gui.theme.Theme
import dev.freelocs.aetherion.hud.HudManager
import dev.freelocs.aetherion.platform.Platform
import net.fabricmc.loader.api.FabricLoader

/** Process-wide service holder. Initialised once from [AetherionMod]. */
object Aetherion {
    const val ID = "aetherion"

    lateinit var config: ConfigStore
        private set
    lateinit var features: FeatureManager
        private set

    val version: String by lazy {
        FabricLoader.getInstance().getModContainer(ID).map { it.metadata.version.friendlyString }.orElse("dev")
    }

    private var initialised = false

    fun init() {
        check(!initialised) { "Aetherion initialised twice" }
        initialised = true

        Catalog.registerAll()
        FeatureRegistry.validate().takeIf { it.isNotEmpty() }?.let { problems ->
            problems.forEach { Log.error("Feature schema problem: $it") }
            error("Invalid feature registry (${problems.size} problems), see log")
        }

        config = ConfigStore(FabricLoader.getInstance().configDir.resolve("aetherion.json"))
        config.load()
        Log.info("Config: ${config.loadOutcome}")

        features = FeatureManager(config, Platform)
        applyUiSettings(snap = true)
        features.reconcile()

        EventBus.on<SettingChangedEvent> { e ->
            if (e.featureId == "general.interface" || e.featureId == "performance.render" || e.featureId == "cosmetics.theme") applyUiSettings()
        }
        EventBus.on<ProfileChangedEvent> {
            features.reconcile()
            HudManager.markOrderDirty()
            HudManager.refresh(force = true)
        }
        Log.info("Aetherion $version initialised: ${FeatureRegistry.categories.size} categories, ${FeatureRegistry.features.size} catalog entries")
    }

    /** Applies language, motion and theme settings from the config to the live UI state. */
    fun applyUiSettings(snap: Boolean = false) {
        val c = config
        I18n.apply(c.string("general.interface", "language"), runCatching { Platform.gameLanguage }.getOrDefault("en_us"))
        AuroraAnimationEngine.reducedMotion = c.bool("performance.render", "reducedMotion")
        AuroraAnimationEngine.decorative = c.bool("performance.render", "decorative")
        AuroraThemeManager.setVariant(c.string("cosmetics.theme", "variant"))
        Theme.glow = (c.double("cosmetics.theme", "glow") / 100.0).toFloat() * (if (AuroraAnimationEngine.decorative) 1f else 0f)
        val preset = AccentPreset.byId(c.string("cosmetics.theme", "accent"))
        val (primary, secondary) = if (preset != null) preset.primary to preset.secondary else {
            val custom = c.color("cosmetics.theme", "customAccent")
            custom to dev.freelocs.aetherion.gui.theme.Colors.lerp(custom, dev.freelocs.aetherion.gui.theme.Colors.VIOLET, 0.5f)
        }
        if (snap) AuroraThemeManager.snapAccent(primary, secondary) else AuroraThemeManager.setAccent(primary, secondary)
    }

    fun shutdown() {
        if (!initialised) return
        features.shutdown()
        config.saveNow()
    }
}
