package dev.freelocs.aetherion.common.data

import com.google.gson.JsonParser
import dev.freelocs.aetherion.common.features.Capability
import dev.freelocs.aetherion.common.features.FeatureDefinition
import dev.freelocs.aetherion.common.features.FeatureStatus
import dev.freelocs.aetherion.common.services.I18n
import dev.freelocs.aetherion.core.Log

/** Details of a planned feature, shown in the detail panel and mirrored in docs/ROADMAP.md. */
data class RoadmapEntry(
    val id: String,
    val categoryId: String,
    val status: FeatureStatus,
    val milestone: String,
    val dataSource: String,
    val complexity: String,
    val utility: String,
    val rule: String,
    val apiPermission: String,
    val compatibility: String,
    val acceptance: List<String>
)

/**
 * Loads `data/aetherion/roadmap.json`, the single source of truth for planned features. Entries become
 * non-toggleable [FeatureDefinition]s (status PLANNED or REVIEW_REQUIRED, never enabled) and their texts
 * are registered with [I18n] so they are searchable and localised like everything else.
 */
object Roadmap {
    private val entryMap = LinkedHashMap<String, RoadmapEntry>()
    val entries: List<RoadmapEntry> get() = entryMap.values.toList()
    fun entry(id: String) = entryMap[id]

    fun load(): List<FeatureDefinition> {
        entryMap.clear()
        val text = Roadmap::class.java.getResourceAsStream("/data/aetherion/roadmap.json")
            ?.use { it.reader(Charsets.UTF_8).readText() } ?: run { Log.error("roadmap.json missing"); return emptyList() }
        val defs = ArrayList<FeatureDefinition>()
        for (el in JsonParser.parseString(text).asJsonObject.getAsJsonArray("entries")) {
            val o = el.asJsonObject
            val id = o.get("id").asString
            val status = if (o.get("status").asString == "review") FeatureStatus.REVIEW_REQUIRED else FeatureStatus.PLANNED
            val source = o.get("dataSource").asString
            val entry = RoadmapEntry(
                id, o.get("category").asString, status, o.get("milestone").asString, source,
                o.get("complexity").asString, o.get("utility").asString, o.get("rule").asString,
                o.get("apiPermission").asString, o.get("compatibility").asString,
                o.getAsJsonArray("acceptance").map { it.asString }
            )
            entryMap[id] = entry
            for (lang in I18n.supported) {
                I18n.extend(lang, "roadmap.$id.title", o.getAsJsonObject("title").get(lang).asString)
                I18n.extend(lang, "roadmap.$id.desc", o.getAsJsonObject("description").get(lang).asString)
            }
            defs += FeatureDefinition(
                id = id, categoryId = entry.categoryId,
                titleKey = "roadmap.$id.title", descriptionKey = "roadmap.$id.desc",
                defaultEnabled = false, status = status, toggleable = false,
                capabilities = capabilitiesFor(source)
            )
        }
        return defs
    }

    private fun capabilitiesFor(source: String): Set<Capability> = when (source) {
        "hypixel-key" -> setOf(Capability.NETWORK, Capability.SKYBLOCK_DATA)
        "hypixel-public", "community-data" -> setOf(Capability.NETWORK)
        else -> setOf(Capability.CLIENT_ONLY)
    }
}
