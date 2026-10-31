package dev.freelocs.aetherion.common.services

import com.google.gson.JsonParser
import dev.freelocs.aetherion.common.events.EventBus
import dev.freelocs.aetherion.common.events.LanguageChangedEvent
import dev.freelocs.aetherion.core.Log

/**
 * Own translation layer so the language can be switched inside Aetherion independent of the game
 * language. Tables are read from the mod's regular `assets/aetherion/lang` JSON resources (the same
 * files Minecraft would use), with English as fallback and the raw key as last resort.
 */
object I18n {
    val supported = listOf("en", "de")
    private val tables = HashMap<String, Map<String, String>>()
    private var current = "en"
    private val cache = HashMap<String, String>()

    /** Active language code ("en" or "de"). */
    val language: String get() = current

    private fun table(code: String): Map<String, String> = tables.getOrPut(code) {
        val path = "/assets/aetherion/lang/${if (code == "de") "de_de" else "en_us"}.json"
        runCatching {
            I18n::class.java.getResourceAsStream(path)!!.use { stream ->
                JsonParser.parseReader(stream.reader(Charsets.UTF_8)).asJsonObject.entrySet().associate { it.key to it.value.asString }
            }
        }.getOrElse { Log.error("Could not load language table $path", it); emptyMap() }
    }

    /** Resolves a user setting (`auto`, `en`, `de`) against the game language and applies it. */
    fun apply(setting: String, gameLanguage: String) {
        val resolved = when (setting) {
            "en", "de" -> setting
            else -> if (gameLanguage.startsWith("de")) "de" else "en"
        }
        if (resolved == current) return
        current = resolved
        cache.clear()
        EventBus.post(LanguageChangedEvent())
    }

    private val extras = HashMap<String, HashMap<String, String>>()

    /** Adds data-driven strings (e.g. from the roadmap catalog) that live outside the lang files. */
    fun extend(lang: String, key: String, text: String) {
        extras.getOrPut(lang) { HashMap() }[key] = text
        cache.clear()
    }

    fun tr(key: String): String = cache.getOrPut(key) {
        table(current)[key] ?: extras[current]?.get(key) ?: table("en")[key] ?: extras["en"]?.get(key) ?: key
    }

    fun tr(key: String, vararg args: Any?): String = tr(key).let { runCatching { String.format(it, *args) }.getOrDefault(it) }

    fun has(key: String) = tr(key) != key

    /** Test hook. */
    internal fun reset() { tables.clear(); cache.clear(); current = "en" }
}
