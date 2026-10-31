package dev.freelocs.aetherion.common.config

/** Mutable state of one feature: an explicit enabled flag (null = use schema default) plus setting values. */
class FeatureState {
    var enabled: Boolean? = null
    val values = HashMap<String, Any>()
}

/** Placement of one HUD widget, stored per profile. Offsets are relative to [anchor] in GUI units. */
data class WidgetPlacement(
    var anchor: Int = 0,
    var x: Float = 0f,
    var y: Float = 0f,
    var scale: Float = 1f,
    var opacity: Float = 1f,
    var layer: Int = 0,
    var visible: Boolean = true
) {
    fun copyOf() = WidgetPlacement(anchor, x, y, scale, opacity, layer, visible)
}

typealias HudLayout = LinkedHashMap<String, WidgetPlacement>

/** Everything that differs between profiles. */
class ProfileData {
    val features = HashMap<String, FeatureState>()
    var hud: HudLayout = LinkedHashMap()
    val layoutSlots = LinkedHashMap<String, HudLayout>()
}
