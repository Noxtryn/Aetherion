package dev.freelocs.aetherion.hud

import dev.freelocs.aetherion.common.features.Feature
import dev.freelocs.aetherion.common.features.FeatureContext

/**
 * A movable HUD element. Content is plain text refreshed by [HudManager] at the configured interval
 * (never per frame), so steady-state rendering does no string building.
 */
abstract class HudWidget(val id: String, val titleKey: String) {
    /** 0..8, row-major: 0 top-left, 1 top-centre, 2 top-right, 3 mid-left, 4 centre, 5 mid-right, 6 bottom-left, 7 bottom-centre, 8 bottom-right. */
    open val defaultAnchor: Int = 0
    open val defaultX: Float = 6f
    open val defaultY: Float = 6f

    /** Current text; called at the refresh interval. */
    abstract fun compute(): String

    /** Text shown in the editor when there is no live data (e.g. not in a world). */
    abstract val sample: String

    abstract fun color(): Int
}

/** Registers a [HudWidget] while the owning feature is active. */
abstract class HudWidgetFeature(private val widget: HudWidget) : Feature {
    override fun onActivate(ctx: FeatureContext) = HudManager.register(widget)
    override fun onDeactivate(ctx: FeatureContext) = HudManager.unregister(widget.id)
}
