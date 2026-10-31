package dev.freelocs.aetherion.common.features

import dev.freelocs.aetherion.common.events.Subscriptions

/** Implementation maturity of a catalog entry. Only [IMPLEMENTED] features can be toggled. */
enum class FeatureStatus { IMPLEMENTED, PLANNED, REVIEW_REQUIRED }

/** Runtime conditions a feature needs before it can do anything useful. */
enum class Capability { CLIENT_ONLY, ON_HYPIXEL, SKYBLOCK_DATA, NETWORK }

data class CategoryDefinition(
    val id: String,
    val groupId: String,
    val titleKey: String,
    val descriptionKey: String,
    val icon: String
)

data class GroupDefinition(val id: String, val titleKey: String)

/**
 * Lifecycle of a runtime feature. The manager guarantees: [onInit] once, [onActivate]/[onDeactivate]
 * strictly alternating, [onCleanup] once on shutdown. Anything subscribed through
 * [FeatureContext.subscriptions] is released automatically after [onDeactivate].
 */
interface Feature {
    fun onInit(ctx: FeatureContext) {}
    fun onActivate(ctx: FeatureContext) {}
    fun onDeactivate(ctx: FeatureContext) {}
    fun onCleanup() {}
}

class FeatureContext(val definition: FeatureDefinition, val subscriptions: Subscriptions)

/**
 * Registry entry describing one feature. [factory] creates the runtime [Feature]; features without
 * one are pure settings holders (their settings are read by other systems).
 *
 * [global] features store their state once for all profiles (interface/performance/theme),
 * all others are stored per profile. [toggleable]=false means the feature is always on.
 */
data class FeatureDefinition(
    val id: String,
    val categoryId: String,
    val titleKey: String,
    val descriptionKey: String,
    val defaultEnabled: Boolean,
    val settings: List<SettingDefinition> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val capabilities: Set<Capability> = setOf(Capability.CLIENT_ONLY),
    val compatibilityKey: String? = null,
    val status: FeatureStatus = FeatureStatus.IMPLEMENTED,
    val toggleable: Boolean = true,
    val global: Boolean = false,
    val factory: (() -> Feature)? = null
) {
    fun setting(settingId: String): SettingDefinition? = settings.firstOrNull { it.id == settingId }
}
