package dev.freelocs.aetherion.common.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import dev.freelocs.aetherion.common.events.EventBus
import dev.freelocs.aetherion.common.events.FeatureToggledEvent
import dev.freelocs.aetherion.common.events.ProfileChangedEvent
import dev.freelocs.aetherion.common.events.SettingChangedEvent
import dev.freelocs.aetherion.common.features.FeatureDefinition
import dev.freelocs.aetherion.common.features.FeatureRegistry
import dev.freelocs.aetherion.common.features.FeatureStatus
import dev.freelocs.aetherion.common.features.SettingDefinition
import dev.freelocs.aetherion.core.Log
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Profile-aware JSON configuration.
 *
 * - Global features (interface, performance, theme) live in `global`, everything else per profile.
 * - Every value is validated against its [SettingDefinition] when loaded; invalid values fall back to defaults.
 * - Saves are debounced and atomic (temp file + move). A pre-v2 file is backed up and replaced by a clean config;
 *   a corrupt file is quarantined next to the config.
 */
class ConfigStore(private val file: Path) {
    companion object {
        const val SCHEMA = 2
        const val DEFAULT_PROFILE = "default"
        private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        private val saver = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "Aetherion-ConfigSaver").apply { isDaemon = true } }
    }

    private val profiles = LinkedHashMap<String, ProfileData>().apply { put(DEFAULT_PROFILE, ProfileData()) }
    private val globals = HashMap<String, FeatureState>()
    private val favoriteSet = LinkedHashSet<String>()
    private var pendingSave: ScheduledFuture<*>? = null

    var activeProfile: String = DEFAULT_PROFILE
        private set

    /** Bumped whenever the favourites list changes, so views can rebuild lazily. */
    var favoritesVersion = 0
        private set

    /** Result of the last [load]: what happened to the file on disk. */
    var loadOutcome: String = "not loaded"
        private set

    val profileNames: List<String> @Synchronized get() = profiles.keys.toList()
    val favorites: List<String> @Synchronized get() = favoriteSet.toList()
    private val profile: ProfileData get() = profiles.getValue(activeProfile)

    // ── feature state ───────────────────────────────────────────────────────

    private fun state(def: FeatureDefinition, create: Boolean = true): FeatureState? {
        val map = if (def.global) globals else profile.features
        return if (create) map.getOrPut(def.id) { FeatureState() } else map[def.id]
    }

    @Synchronized
    fun isEnabled(def: FeatureDefinition): Boolean = when {
        def.status != FeatureStatus.IMPLEMENTED -> false
        !def.toggleable -> true
        else -> state(def, false)?.enabled ?: def.defaultEnabled
    }

    fun isEnabled(featureId: String): Boolean = FeatureRegistry.feature(featureId)?.let(::isEnabled) ?: false

    @Synchronized
    fun setEnabled(def: FeatureDefinition, enabled: Boolean) {
        if (!def.toggleable || def.status != FeatureStatus.IMPLEMENTED) return
        if (isEnabled(def) == enabled) return
        val st = state(def)!!
        st.enabled = if (enabled == def.defaultEnabled) null else enabled
        markDirty()
        EventBus.post(FeatureToggledEvent(def.id, enabled))
    }

    @Synchronized
    fun get(def: FeatureDefinition, setting: SettingDefinition): Any = state(def, false)?.values?.get(setting.id) ?: setting.default

    fun bool(featureId: String, settingId: String): Boolean = typed(featureId, settingId) as? Boolean ?: false
    fun double(featureId: String, settingId: String): Double = (typed(featureId, settingId) as? Number)?.toDouble() ?: 0.0
    fun string(featureId: String, settingId: String): String = typed(featureId, settingId) as? String ?: ""
    fun color(featureId: String, settingId: String): Int = (typed(featureId, settingId) as? Number)?.toInt() ?: -1

    private fun typed(featureId: String, settingId: String): Any? {
        val def = FeatureRegistry.feature(featureId) ?: return null
        val s = def.setting(settingId) ?: return null
        return get(def, s)
    }

    @Synchronized
    fun set(def: FeatureDefinition, setting: SettingDefinition, value: Any) {
        val coerced = setting.coerce(value) ?: return
        if (get(def, setting) == coerced) return
        val st = state(def)!!
        if (coerced == setting.default) st.values.remove(setting.id) else st.values[setting.id] = coerced
        markDirty()
        EventBus.post(SettingChangedEvent(def.id, setting.id))
    }

    @Synchronized
    fun isModified(def: FeatureDefinition): Boolean {
        val st = state(def, false) ?: return false
        return st.enabled != null || st.values.isNotEmpty()
    }

    @Synchronized
    fun resetFeature(def: FeatureDefinition) {
        val wasEnabled = isEnabled(def)
        val st = state(def, false) ?: return
        val changed = st.values.keys.toList()
        st.values.clear(); st.enabled = null
        markDirty()
        changed.forEach { EventBus.post(SettingChangedEvent(def.id, it)) }
        if (wasEnabled != isEnabled(def)) EventBus.post(FeatureToggledEvent(def.id, isEnabled(def)))
    }

    /** Resets every feature of the active profile (and its HUD layout). With [includeGlobal] also interface/theme/performance. */
    @Synchronized
    fun resetAll(includeGlobal: Boolean) {
        val defs = FeatureRegistry.features.filter { includeGlobal || !it.global }
        defs.forEach(::resetFeature)
        profile.hud = LinkedHashMap()
        markDirty()
        EventBus.post(ProfileChangedEvent(activeProfile))
    }

    // ── favorites ───────────────────────────────────────────────────────────

    @Synchronized
    fun isFavorite(id: String) = id in favoriteSet

    @Synchronized
    fun toggleFavorite(id: String) {
        if (!favoriteSet.remove(id)) favoriteSet.add(id)
        favoritesVersion++
        markDirty()
    }

    // ── profiles ────────────────────────────────────────────────────────────

    @Synchronized
    fun createProfile(requested: String, copyFromActive: Boolean = false): String {
        val name = uniqueName(sanitize(requested))
        profiles[name] = if (copyFromActive) profileFromJson(profileToJson(profile)) else ProfileData()
        markDirty()
        return name
    }

    @Synchronized
    fun switchProfile(name: String): Boolean {
        if (name !in profiles || name == activeProfile) return false
        activeProfile = name
        markDirty()
        EventBus.post(ProfileChangedEvent(name))
        return true
    }

    @Synchronized
    fun deleteProfile(name: String): Boolean {
        if (name == DEFAULT_PROFILE || profiles.remove(name) == null) return false
        if (activeProfile == name) activeProfile = DEFAULT_PROFILE
        markDirty()
        EventBus.post(ProfileChangedEvent(activeProfile))
        return true
    }

    private fun sanitize(name: String) = name.trim().replace(Regex("[^A-Za-z0-9 _.-]"), "").take(24).ifBlank { "profile" }

    private fun uniqueName(base: String): String {
        if (base !in profiles) return base
        var i = 2
        while ("$base $i" in profiles) i++
        return "$base $i"
    }

    // ── HUD layout (active profile) ─────────────────────────────────────────

    val hudLayout: HudLayout @Synchronized get() = profile.hud
    val layoutSlots: Map<String, HudLayout> @Synchronized get() = profile.layoutSlots

    @Synchronized
    fun replaceHudLayout(layout: HudLayout) {
        profile.hud = layout
        markDirty()
    }

    @Synchronized
    fun saveLayoutSlot(name: String) {
        profile.layoutSlots[name] = deepCopy(profile.hud)
        markDirty()
    }

    @Synchronized
    fun restoreLayoutSlot(name: String): Boolean {
        val slot = profile.layoutSlots[name] ?: return false
        profile.hud = deepCopy(slot)
        markDirty()
        return true
    }

    private fun deepCopy(src: HudLayout): HudLayout = LinkedHashMap<String, WidgetPlacement>().also { out -> src.forEach { (k, v) -> out[k] = v.copyOf() } }

    @Synchronized
    fun markHudDirty() = markDirty()

    // ── import / export ─────────────────────────────────────────────────────

    @Synchronized
    fun exportActiveProfile(): String {
        val root = JsonObject()
        root.addProperty("aetherion", "profile-export")
        root.addProperty("schema", SCHEMA)
        root.addProperty("name", activeProfile)
        root.add("profile", profileToJson(profile))
        return gson.toJson(root)
    }

    /** Imports an exported profile as a new profile; returns its name or the failure reason. */
    @Synchronized
    fun importProfile(text: String): Result<String> = runCatching {
        val root = JsonParser.parseString(text.trim()).asJsonObject
        require(root.get("aetherion")?.asString == "profile-export") { "not an Aetherion export" }
        require((root.get("schema")?.asInt ?: 0) <= SCHEMA) { "export was made by a newer version" }
        val data = profileFromJson(root.getAsJsonObject("profile"))
        val name = uniqueName(sanitize(root.get("name")?.asString ?: "imported"))
        profiles[name] = data
        markDirty()
        name
    }

    // ── persistence ─────────────────────────────────────────────────────────

    @Synchronized
    fun load() {
        if (!Files.exists(file)) {
            loadOutcome = "no config, created defaults"
            saveNow()
            return
        }
        val root = try {
            JsonParser.parseString(Files.readString(file)).asJsonObject
        } catch (e: Exception) {
            val quarantine = file.resolveSibling("${file.fileName}.corrupt-${System.currentTimeMillis()}")
            runCatching { Files.move(file, quarantine) }
            Log.error("Config unreadable; moved to ${quarantine.fileName} and reset to defaults", e)
            loadOutcome = "corrupt config quarantined as ${quarantine.fileName}"
            saveNow()
            return
        }
        val version = root.get("schema")?.takeIf { it.isJsonPrimitive }?.asInt ?: 1
        if (version < SCHEMA) {
            val backup = file.resolveSibling("${file.fileName}.v$version-backup")
            runCatching { Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING) }
            Log.info("Legacy config (schema $version) backed up to ${backup.fileName}; starting with a clean v$SCHEMA config")
            loadOutcome = "legacy v$version config backed up as ${backup.fileName}, reset to defaults"
            saveNow()
            return
        }
        if (version > SCHEMA) Log.warn("Config schema $version is newer than supported $SCHEMA; unknown data will be dropped on save")
        parse(root)
        loadOutcome = "loaded schema $version"
    }

    private fun parse(root: JsonObject) {
        profiles.clear(); globals.clear(); favoriteSet.clear(); favoritesVersion++
        root.getAsJsonArray("favorites")?.forEach { e -> e.asString.takeIf { FeatureRegistry.feature(it) != null }?.let(favoriteSet::add) }
        root.getAsJsonObject("global")?.let { g -> featuresFromJson(g, globals, global = true) }
        root.getAsJsonObject("profiles")?.entrySet()?.forEach { (name, el) ->
            if (el.isJsonObject) profiles[name] = profileFromJson(el.asJsonObject)
        }
        if (DEFAULT_PROFILE !in profiles) profiles[DEFAULT_PROFILE] = ProfileData()
        val wanted = root.get("activeProfile")?.asString
        activeProfile = if (wanted != null && wanted in profiles) wanted else DEFAULT_PROFILE
    }

    private fun featuresFromJson(obj: JsonObject, into: MutableMap<String, FeatureState>, global: Boolean) {
        for ((id, el) in obj.entrySet()) {
            val def = FeatureRegistry.feature(id)
            if (def == null || def.global != global || !el.isJsonObject) continue
            val st = FeatureState()
            val o = el.asJsonObject
            if (def.toggleable && def.status == FeatureStatus.IMPLEMENTED) {
                o.get("enabled")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
                    ?.takeIf { it != def.defaultEnabled }?.let { st.enabled = it }
            }
            o.getAsJsonObject("settings")?.entrySet()?.forEach { (sid, sv) ->
                val s = def.setting(sid) ?: return@forEach
                val v = s.fromJson(sv)
                if (v == null) Log.warn("Ignoring invalid value for ${def.id}.$sid")
                else if (v != s.default) st.values[sid] = v
            }
            into[id] = st
        }
    }

    private fun featuresToJson(map: Map<String, FeatureState>): JsonObject {
        val out = JsonObject()
        for ((id, st) in map) {
            val def = FeatureRegistry.feature(id) ?: continue
            if (st.enabled == null && st.values.isEmpty()) continue
            val o = JsonObject()
            st.enabled?.let { o.addProperty("enabled", it) }
            if (st.values.isNotEmpty()) {
                val s = JsonObject()
                for (setting in def.settings) st.values[setting.id]?.let { s.add(setting.id, setting.toJson(it)) }
                o.add("settings", s)
            }
            out.add(id, o)
        }
        return out
    }

    private fun profileToJson(p: ProfileData): JsonObject {
        val o = JsonObject()
        o.add("features", featuresToJson(p.features))
        o.add("hud", layoutToJson(p.hud))
        val slots = JsonObject()
        p.layoutSlots.forEach { (n, l) -> slots.add(n, layoutToJson(l)) }
        o.add("layoutSlots", slots)
        return o
    }

    private fun profileFromJson(o: JsonObject): ProfileData {
        val p = ProfileData()
        o.getAsJsonObject("features")?.let { featuresFromJson(it, p.features, global = false) }
        o.getAsJsonObject("hud")?.let { p.hud = layoutFromJson(it) }
        o.getAsJsonObject("layoutSlots")?.entrySet()?.forEach { (n, l) -> if (l.isJsonObject) p.layoutSlots[n] = layoutFromJson(l.asJsonObject) }
        return p
    }

    private fun layoutToJson(layout: HudLayout): JsonObject {
        val o = JsonObject()
        layout.forEach { (id, w) ->
            o.add(id, JsonObject().apply {
                addProperty("anchor", w.anchor); addProperty("x", w.x); addProperty("y", w.y)
                addProperty("scale", w.scale); addProperty("opacity", w.opacity)
                addProperty("layer", w.layer); addProperty("visible", w.visible)
            })
        }
        return o
    }

    private fun layoutFromJson(o: JsonObject): HudLayout {
        val out = HudLayout()
        for ((id, el) in o.entrySet()) {
            if (!el.isJsonObject) continue
            val w = el.asJsonObject
            fun f(key: String, def: Float, lo: Float, hi: Float) =
                (w.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asFloat ?: def).takeIf { it.isFinite() }?.coerceIn(lo, hi) ?: def
            out[id] = WidgetPlacement(
                anchor = (w.get("anchor")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0).coerceIn(0, 8),
                x = f("x", 0f, -8192f, 8192f), y = f("y", 0f, -8192f, 8192f),
                scale = f("scale", 1f, 0.5f, 3f), opacity = f("opacity", 1f, 0.1f, 1f),
                layer = (w.get("layer")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0).coerceIn(-1000, 1000),
                visible = w.get("visible")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true
            )
        }
        return out
    }

    private fun toJson(): String {
        val root = JsonObject()
        root.addProperty("schema", SCHEMA)
        root.addProperty("activeProfile", activeProfile)
        root.add("favorites", JsonArray().also { a -> favoriteSet.forEach { a.add(JsonPrimitive(it)) } })
        root.add("global", featuresToJson(globals))
        val ps = JsonObject()
        profiles.forEach { (n, p) -> ps.add(n, profileToJson(p)) }
        root.add("profiles", ps)
        return gson.toJson(root)
    }

    @Synchronized
    private fun markDirty() {
        if (pendingSave?.isDone == false) return
        pendingSave = saver.schedule({ saveNow() }, 500, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun saveNow() {
        pendingSave?.cancel(false)
        runCatching {
            Files.createDirectories(file.parent)
            val tmp = file.resolveSibling("${file.fileName}.tmp")
            Files.writeString(tmp, toJson())
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure { Log.error("Failed to save Aetherion config", it) }
    }
}
