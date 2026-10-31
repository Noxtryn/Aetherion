package dev.freelocs.aetherion.common.features

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import kotlin.math.roundToLong

/**
 * Typed, schema-validated description of one feature setting. The GUI builds controls purely from
 * these definitions; stored values are always passed through [coerce] so a hand-edited or stale
 * config can never put a value outside the declared schema.
 *
 * Value representations: Toggle=[Boolean], Slider=[Double], Choice=[String] (option id),
 * Color=[Int] (ARGB), Keybind=[String] (Minecraft key name such as `key.keyboard.k`, "" = unbound).
 */
sealed class SettingDefinition(val id: String, val titleKey: String, val descriptionKey: String?) {
    abstract val default: Any

    /** Returns [raw] if it satisfies the schema (normalised, e.g. clamped), otherwise null. */
    abstract fun coerce(raw: Any?): Any?
    abstract fun toJson(value: Any): JsonElement
    abstract fun fromJson(element: JsonElement): Any?

    class Toggle(id: String, titleKey: String, descriptionKey: String? = null, override val default: Boolean) :
        SettingDefinition(id, titleKey, descriptionKey) {
        override fun coerce(raw: Any?): Any? = raw as? Boolean
        override fun toJson(value: Any) = JsonPrimitive(value as Boolean)
        override fun fromJson(element: JsonElement): Any? =
            element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
    }

    class Slider(
        id: String, titleKey: String, descriptionKey: String? = null,
        val min: Double, val max: Double, val step: Double, default: Double,
        val decimals: Int = 0, val suffix: String = ""
    ) : SettingDefinition(id, titleKey, descriptionKey) {
        override val default: Any = default

        init {
            require(min < max && step > 0.0 && default in min..max) { "Invalid slider schema for '$id'" }
        }

        override fun coerce(raw: Any?): Any? {
            val v = (raw as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: return null
            val snapped = min + ((v.coerceIn(min, max) - min) / step).roundToLong() * step
            return snapped.coerceIn(min, max)
        }

        override fun toJson(value: Any) = JsonPrimitive(value as Double)
        override fun fromJson(element: JsonElement): Any? =
            element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble?.let(::coerce)
    }

    class Choice(
        id: String, titleKey: String, descriptionKey: String? = null,
        val options: List<String>, override val default: String, val optionKeyPrefix: String
    ) : SettingDefinition(id, titleKey, descriptionKey) {
        init {
            require(options.isNotEmpty() && default in options) { "Invalid choice schema for '$id'" }
        }

        fun optionKey(option: String) = "$optionKeyPrefix.$option"
        override fun coerce(raw: Any?): Any? = (raw as? String)?.takeIf { it in options }
        override fun toJson(value: Any) = JsonPrimitive(value as String)
        override fun fromJson(element: JsonElement): Any? =
            element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let(::coerce)
    }

    class Color(id: String, titleKey: String, descriptionKey: String? = null, override val default: Int) :
        SettingDefinition(id, titleKey, descriptionKey) {
        override fun coerce(raw: Any?): Any? = (raw as? Number)?.toInt()?.let { it or (0xFF shl 24) }
        override fun toJson(value: Any) = JsonPrimitive(String.format("#%06X", (value as Int) and 0xFFFFFF))
        override fun fromJson(element: JsonElement): Any? {
            if (!element.isJsonPrimitive) return null
            val p = element.asJsonPrimitive
            if (p.isNumber) return coerce(p.asInt)
            val s = p.asString.removePrefix("#")
            return if (s.length == 6) s.toIntOrNull(16)?.let(::coerce) else null
        }
    }

    class Keybind(id: String, titleKey: String, descriptionKey: String? = null, override val default: String = "") :
        SettingDefinition(id, titleKey, descriptionKey) {
        override fun coerce(raw: Any?): Any? = (raw as? String)?.takeIf { it.isEmpty() || it.startsWith("key.") }
        override fun toJson(value: Any) = JsonPrimitive(value as String)
        override fun fromJson(element: JsonElement): Any? =
            element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let(::coerce)
    }
}
