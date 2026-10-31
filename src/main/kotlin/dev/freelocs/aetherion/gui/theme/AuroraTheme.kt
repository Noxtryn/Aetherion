package dev.freelocs.aetherion.gui.theme

import dev.freelocs.aetherion.gui.animations.Tween

/** Spacing scale in GUI units. */
object Space { const val XS = 4; const val SM = 8; const val MD = 12; const val LG = 16; const val XL = 24 }

/** Corner radii in GUI units (each maps to a generated sprite). */
object Radius { const val SM = 4; const val MD = 8; const val LG = 12; const val XL = 16; const val PILL = 20 }

/** Animation durations in seconds. */
object Duration { const val FAST = 0.12f; const val NORMAL = 0.22f; const val SLOW = 0.38f }

/** Reusable alpha values (0..1). */
object Opacity { const val DISABLED = 0.45f; const val HOVER = 0.07f; const val PRESSED = 0.12f; const val SCRIM = 0.55f; const val GLOW = 0.28f }

/** Fixed layout metrics. */
object Metrics {
    const val SIDEBAR_W = 212
    const val SIDEBAR_COLLAPSED_W = 56
    const val HEADER_H = 52
    const val DETAIL_W = 268
    const val NAV_ITEM_H = 30
    const val CONTROL_H = 22
    const val ROW_H = 40
    const val PANEL_MAX_W = 1120
    const val PANEL_MAX_H = 640
}

object Colors {
    // Palette (spec)
    const val BACKGROUND = 0xFF080D19.toInt()
    const val SURFACE = 0xFF0B1020.toInt()
    const val SIDEBAR = 0xFF101729.toInt()
    const val CARD = 0xFF171F34.toInt()
    const val PINK = 0xFFF591D4.toInt()
    const val ICE = 0xFF79D7FF.toInt()
    const val VIOLET = 0xFFA895FF.toInt()
    const val TEXT = 0xFFF5F8FF.toInt()
    const val TEXT_SECONDARY = 0xFF9CAEC9.toInt()
    const val BORDER = 0xFF29354B.toInt()

    // Status colors (derived, same saturation range as the palette)
    const val SUCCESS = 0xFF7BE0A8.toInt()
    const val WARNING = 0xFFFFC978.toInt()
    const val DANGER = 0xFFFF7A8C.toInt()

    fun alpha(color: Int, a: Float): Int {
        val na = (((color ushr 24) and 0xFF) * a.coerceIn(0f, 1f) + 0.5f).toInt()
        return (na shl 24) or (color and 0xFFFFFF)
    }

    fun withAlpha(color: Int, a: Int) = (a.coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)

    fun lerp(a: Int, b: Int, t: Float): Int {
        if (t <= 0f) return a
        if (t >= 1f) return b
        val it = 1f - t
        val aa = (((a ushr 24) and 0xFF) * it + ((b ushr 24) and 0xFF) * t).toInt()
        val ar = (((a shr 16) and 0xFF) * it + ((b shr 16) and 0xFF) * t).toInt()
        val ag = (((a shr 8) and 0xFF) * it + ((b shr 8) and 0xFF) * t).toInt()
        val ab = ((a and 0xFF) * it + (b and 0xFF) * t).toInt()
        return (aa shl 24) or (ar shl 16) or (ag shl 8) or ab
    }

    fun luminance(c: Int): Float =
        (0.2126f * ((c shr 16) and 0xFF) + 0.7152f * ((c shr 8) and 0xFF) + 0.0722f * (c and 0xFF)) / 255f

    // HSV helpers for the color picker
    fun hsvToRgb(h: Float, s: Float, v: Float): Int {
        val hh = ((h % 1f) + 1f) % 1f * 6f
        val i = hh.toInt()
        val f = hh - i
        val p = v * (1 - s); val q = v * (1 - s * f); val t = v * (1 - s * (1 - f))
        val r: Float; val g: Float; val b: Float
        when (i) {
            0 -> { r = v; g = t; b = p }
            1 -> { r = q; g = v; b = p }
            2 -> { r = p; g = v; b = t }
            3 -> { r = p; g = q; b = v }
            4 -> { r = t; g = p; b = v }
            else -> { r = v; g = p; b = q }
        }
        return (0xFF shl 24) or ((r * 255 + 0.5f).toInt() shl 16) or ((g * 255 + 0.5f).toInt() shl 8) or (b * 255 + 0.5f).toInt()
    }

    fun rgbToHsv(c: Int, out: FloatArray) {
        val r = ((c shr 16) and 0xFF) / 255f; val g = ((c shr 8) and 0xFF) / 255f; val b = (c and 0xFF) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> ((g - b) / d % 6f) / 6f
            max == g -> ((b - r) / d + 2f) / 6f
            else -> ((r - g) / d + 4f) / 6f
        }
        out[0] = if (h < 0) h + 1f else h
        out[1] = if (max == 0f) 0f else d / max
        out[2] = max
    }
}

/** Accent selections available in the theme controls. */
enum class AccentPreset(val id: String, val primary: Int, val secondary: Int) {
    PINK("pink", Colors.PINK, Colors.VIOLET),
    ICE("ice", Colors.ICE, Colors.VIOLET),
    VIOLET("violet", Colors.VIOLET, Colors.ICE);

    companion object { fun byId(id: String) = entries.firstOrNull { it.id == id } }
}

/**
 * Live, semantic theme values read by every component each frame. Mutated only by [AuroraThemeManager],
 * which animates the accent so theme changes cross-fade instead of popping.
 */
object Theme {
    var background = Colors.BACKGROUND
    var surface = Colors.SURFACE
    var sidebar = Colors.SIDEBAR
    var card = Colors.CARD
    var cardHover = 0xFF1D2740.toInt()
    var border = Colors.BORDER
    var text = Colors.TEXT
    var textSecondary = Colors.TEXT_SECONDARY
    var accent = Colors.PINK
    var accentSecondary = Colors.VIOLET
    var scrim = 0x99060913.toInt()

    /** 0..1 multiplier for glow strength (user setting). */
    var glow = 0.6f
}

/** Applies theme settings and animates accent transitions. */
object AuroraThemeManager {
    private var fromPrimary = Colors.PINK
    private var fromSecondary = Colors.VIOLET
    private var toPrimary = Colors.PINK
    private var toSecondary = Colors.VIOLET
    private val fade = Tween(1f, 0.35f)

    fun setAccent(primary: Int, secondary: Int) {
        if (primary == toPrimary && secondary == toSecondary) return
        fromPrimary = Theme.accent; fromSecondary = Theme.accentSecondary
        toPrimary = primary; toSecondary = secondary
        fade.snap(0f)
        fade.animateTo(1f)
    }

    fun snapAccent(primary: Int, secondary: Int) {
        toPrimary = primary; toSecondary = secondary; fromPrimary = primary; fromSecondary = secondary
        fade.snap(1f)
        Theme.accent = primary; Theme.accentSecondary = secondary
    }

    fun setVariant(id: String) {
        when (id) {
            "midnight" -> {
                Theme.background = 0xFF04070F.toInt(); Theme.surface = 0xFF070B16.toInt(); Theme.sidebar = 0xFF0A101F.toInt()
                Theme.card = 0xFF121A2C.toInt(); Theme.cardHover = 0xFF18223A.toInt(); Theme.border = 0xFF222D42.toInt()
            }
            else -> {
                Theme.background = Colors.BACKGROUND; Theme.surface = Colors.SURFACE; Theme.sidebar = Colors.SIDEBAR
                Theme.card = Colors.CARD; Theme.cardHover = 0xFF1D2740.toInt(); Theme.border = Colors.BORDER
            }
        }
    }

    /** Call once per frame. */
    fun update() {
        val t = fade.update()
        if (t < 1f || Theme.accent != toPrimary) {
            Theme.accent = Colors.lerp(fromPrimary, toPrimary, t)
            Theme.accentSecondary = Colors.lerp(fromSecondary, toSecondary, t)
        }
    }
}
