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
 * Mob-targeted auto clicker settings.
 *
 * - [selected]: entry keys the clicker triggers on — `type:<entity id>` (e.g. `type:minecraft:zombie`)
 *   or `name:<lowercase mob name>` (e.g. `name:zealot` for a server-named mob).
 * - [discovered]: per server (address, or `singleplayer`), every mob seen there as `entry key -> display name`,
 *   so the config list shows the mobs that actually exist on the server you're on.
 */
data class AutoClickerConfig(
    var enabled: Boolean = true,
    var selected: LinkedHashSet<String> = linkedSetOf(),
    var discovered: LinkedHashMap<String, LinkedHashMap<String, String>> = linkedMapOf()
)

/**
 * Root config schema: the category scaffold plus the auto clicker settings.
 * Serializes to `{"categories": {"hud": {"enabled": false}, ...}, "autoClicker": {...}}`.
 */
data class AetherionConfig(
    var categories: LinkedHashMap<String, CategoryConfig> = linkedMapOf(
        "hud" to CategoryConfig(),
        "analytics" to CategoryConfig(),
        "notifications" to CategoryConfig(),
        "waypoints" to CategoryConfig(),
        "market" to CategoryConfig(),
        "general" to CategoryConfig()
    ),
    var autoClicker: AutoClickerConfig = AutoClickerConfig()
)
