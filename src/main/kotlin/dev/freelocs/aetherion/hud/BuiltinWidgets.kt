package dev.freelocs.aetherion.hud

import dev.freelocs.aetherion.core.Aetherion
import dev.freelocs.aetherion.platform.Platform
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val cfg get() = Aetherion.config

/** Frames per second as reported by Minecraft. */
class FpsWidget : HudWidget("hud.fps", "feature.hud.fps.title") {
    override val defaultAnchor = 0
    override val defaultX = 6f
    override val defaultY = 6f
    override val sample = "60 FPS"
    override fun compute(): String = if (cfg.bool("hud.fps", "label")) "${Platform.fps} FPS" else "${Platform.fps}"
    override fun color() = cfg.color("hud.fps", "color")
}

/** Local system time. */
class ClockWidget : HudWidget("hud.clock", "feature.hud.clock.title") {
    override val defaultAnchor = 2
    override val defaultX = -6f
    override val defaultY = 6f
    override val sample = "12:34"
    private val f24 = DateTimeFormatter.ofPattern("HH:mm")
    private val f24s = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val f12 = DateTimeFormatter.ofPattern("hh:mm a")
    private val f12s = DateTimeFormatter.ofPattern("hh:mm:ss a")
    override fun compute(): String {
        val seconds = cfg.bool("hud.clock", "seconds")
        val f = if (cfg.bool("hud.clock", "use24h")) (if (seconds) f24s else f24) else (if (seconds) f12s else f12)
        return LocalTime.now().format(f)
    }
    override fun color() = cfg.color("hud.clock", "color")
}

/** The player's block/precise position. */
class CoordsWidget : HudWidget("hud.coords", "feature.hud.coords.title") {
    override val defaultAnchor = 6
    override val defaultX = 6f
    override val defaultY = -6f
    override val sample = "XYZ 0 64 0"
    override fun compute(): String {
        val p = Platform.mc.player ?: return "XYZ –"
        val d = cfg.double("hud.coords", "decimals").toInt()
        return "XYZ " + fmt(p.x, d) + " " + fmt(p.y, d) + " " + fmt(p.z, d)
    }
    private fun fmt(v: Double, decimals: Int) = if (decimals == 0) Math.floor(v).toLong().toString() else String.format("%.${decimals}f", v)
    override fun color() = cfg.color("hud.coords", "color")
}

class FpsFeature : HudWidgetFeature(FpsWidget())
class ClockFeature : HudWidgetFeature(ClockWidget())
class CoordsFeature : HudWidgetFeature(CoordsWidget())
