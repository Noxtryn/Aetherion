package dev.freelocs.aetherion.autoclicker

import dev.freelocs.aetherion.core.ConfigManager
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import kotlin.math.abs

/** One selectable row in the auto clicker's mob list. */
data class MobEntry(val key: String, val label: String, val detail: String, val seenOnServer: Boolean)

/**
 * Knows which mobs exist and what they are really called.
 *
 * Two kinds of entries:
 * - **Types** (`type:minecraft:zombie`): every vanilla mob type, named by its translated name.
 * - **Server names** (`name:zealot`): custom mob names a server shows over its mobs (e.g. Hypixel
 *   Skyblock's `[Lv55] Zealot 13k/13k❤` name tags on invisible armor stands). These can't be known
 *   up front, so they're collected while you play and stored per server in the config.
 */
object MobCatalog {
    const val TYPE_PREFIX = "type:"
    const val NAME_PREFIX = "name:"
    private const val MAX_DISCOVERED_PER_SERVER = 1000

    private val LEVEL_PREFIX = Regex("""^\[[^\]]*]\s*""")
    private val HEALTH_SUFFIX = Regex("""\s+[\d.,]+[kKmMbB]?(\s*/\s*[\d.,]+[kKmMbB]?\s*❤?|\s*❤).*$""")

    /** Config key of the server you're currently on (address, or `singleplayer`). */
    fun serverKey(mc: Minecraft = Minecraft.getInstance()): String =
        mc.currentServer?.ip?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "singleplayer"

    fun typeKey(type: EntityType<*>): String = TYPE_PREFIX + EntityType.getKey(type)

    fun nameKey(name: String): String = NAME_PREFIX + name.lowercase()

    /** Living, non-player, non-armor-stand entities — the things the clicker may target. */
    fun isMob(entity: Entity): Boolean =
        entity is LivingEntity && entity !is Player && entity !is ArmorStand

    /** Both keys an entity can be selected by; the name key is absent if it has no server name. */
    fun keysOf(entity: Entity, level: Level): List<String> {
        val keys = mutableListOf(typeKey(entity.type))
        resolveName(entity, level)?.let { keys += nameKey(it) }
        return keys
    }

    /**
     * The mob's real name as the server shows it: its own custom name, or else the name tag of
     * the armor stand floating just above it (how Hypixel & co. label mobs). Returns null when
     * the mob only has its vanilla type name.
     */
    fun resolveName(entity: Entity, level: Level): String? {
        entity.customName?.string?.let(::cleanName)?.let { return it }

        val bb = entity.boundingBox
        val search = AABB(bb.minX - 0.3, bb.maxY - 0.6, bb.minZ - 0.3, bb.maxX + 0.3, bb.maxY + 2.6, bb.maxZ + 0.3)
        val stand = level.getEntities(entity, search) { it is ArmorStand && it.hasCustomName() }
            .minByOrNull { abs(it.x - entity.x) + abs(it.z - entity.z) + abs(it.y - bb.maxY) }
            ?: return null
        return stand.customName?.string?.let(::cleanName)
    }

    /** `§8[§7Lv55§8] §cZealot §a13k§f/§a13k§c❤` → `Zealot`. Null if nothing name-like is left. */
    fun cleanName(raw: String): String? {
        var s = ChatFormatting.stripFormatting(raw) ?: return null
        s = s.trimStart { !it.isLetterOrDigit() && it != '[' }
        s = s.replace(LEVEL_PREFIX, "")
        s = s.replace(HEALTH_SUFFIX, "")
        s = s.trim { !it.isLetterOrDigit() }
        if (s.length !in 2..40 || s.none { it.isLetter() }) return null
        return s
    }

    /** Records a mob seen in the world under the current server. Returns true if it was new. */
    fun discover(entity: Entity, level: Level, server: String): Boolean {
        val known = ConfigManager.config.autoClicker.discovered.getOrPut(server) { linkedMapOf() }
        if (known.size >= MAX_DISCOVERED_PER_SERVER) return false

        var added = false
        val typeKey = typeKey(entity.type)
        if (typeKey !in known) {
            known[typeKey] = entity.type.description.string
            added = true
        }
        val name = resolveName(entity, level)
        if (name != null && !name.equals(entity.type.description.string, ignoreCase = true)) {
            val key = nameKey(name)
            if (key !in known) {
                known[key] = name
                added = true
            }
        }
        return added
    }

    /**
     * Full list for the config screen: everything seen on the current server first (server
     * names, then vanilla types), followed by all remaining vanilla mob types.
     */
    fun entries(server: String = serverKey()): List<MobEntry> {
        val known = ConfigManager.config.autoClicker.discovered[server].orEmpty()
        val result = LinkedHashMap<String, MobEntry>()

        known.entries
            .sortedWith(compareBy({ !it.key.startsWith(NAME_PREFIX) }, { it.value.lowercase() }))
            .forEach { (key, label) ->
                val detail = if (key.startsWith(NAME_PREFIX)) "Server-Mob" else key.removePrefix(TYPE_PREFIX)
                result[key] = MobEntry(key, label, detail, seenOnServer = true)
            }

        BuiltInRegistries.ENTITY_TYPE
            .filter { it.category != MobCategory.MISC }
            .map { MobEntry(typeKey(it), it.description.string, EntityType.getKey(it).toString(), seenOnServer = false) }
            .sortedBy { it.label.lowercase() }
            .forEach { result.putIfAbsent(it.key, it) }

        return result.values.toList()
    }
}
