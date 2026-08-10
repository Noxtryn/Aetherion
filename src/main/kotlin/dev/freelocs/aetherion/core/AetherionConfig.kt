package dev.freelocs.aetherion.core

/**
 * Placeholder category entry. This is intentionally the entire schema for now — once a
 * module (hud, analytics, ...) gets real options, its settings are added here without
 * breaking the existing `categories` map shape.
 */
data class CategoryConfig(
    var enabled: Boolean = false
)

/**
 * Root config schema: just the category scaffold, no feature values yet.
 * Serializes to `{"categories": {"hud": {"enabled": false}, ...}}`.
 */
data class AetherionConfig(
    var categories: LinkedHashMap<String, CategoryConfig> = linkedMapOf(
        "hud" to CategoryConfig(),
        "analytics" to CategoryConfig(),
        "notifications" to CategoryConfig(),
        "waypoints" to CategoryConfig(),
        "market" to CategoryConfig(),
        "general" to CategoryConfig()
    )
)
