package dev.freelocs.aetherion.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.sqrt

/**
 * Small rendering helpers shared by Aetherion's GUI screens.
 *
 * Minecraft's [GuiGraphicsExtractor] has no native rounded-rectangle or backdrop-blur primitive, so
 * the mockup's rounded/glass look is approximated here: [fillRounded] draws a real rounded
 * rect (not a fake corner-cut) by masking each corner row with a circular offset, and
 * [drawGlowBorder] fakes a soft glow with a couple of nested, increasingly transparent
 * border outlines instead of a true gaussian blur.
 */
object GuiUtil {

    /** Fills a rect with quarter-circle corners of [radius] px, background-agnostic (no fill outside the shape). */
    fun fillRounded(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int, radius: Int) {
        val r = radius.coerceAtMost(minOf(w, h) / 2)
        if (r <= 0) {
            ctx.fill(x, y, x + w, y + h, color)
            return
        }
        ctx.fill(x, y + r, x + w, y + h - r, color)
        for (i in 0 until r) {
            val dy = r - i
            val dx = r - sqrt((r * r - dy * dy).toDouble()).toInt()
            ctx.fill(x + dx, y + i, x + w - dx, y + i + 1, color)
            ctx.fill(x + dx, y + h - 1 - i, x + w - dx, y + h - i, color)
        }
    }

    /** Outline version of [fillRounded] — draws just a [thickness]px rounded border. */
    fun drawRoundedBorder(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int, radius: Int, thickness: Int = 1) {
        fillRounded(ctx, x, y, w, h, color, radius)
        if (w - thickness * 2 > 0 && h - thickness * 2 > 0) {
            fillRounded(ctx, x + thickness, y + thickness, w - thickness * 2, h - thickness * 2, 0, (radius - thickness).coerceAtLeast(0))
        }
    }

    /** Soft accent glow approximation: a few nested, fading rounded outlines around a rect. */
    fun drawGlowBorder(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, glowColor: Int, radius: Int) {
        val alpha = (glowColor ushr 24) and 0xFF
        val rgb = glowColor and 0x00FFFFFF
        val steps = 3
        for (i in steps downTo 1) {
            val stepAlpha = (alpha / (i + 1)).coerceIn(0, 255)
            val c = (stepAlpha shl 24) or rgb
            drawRoundedBorder(ctx, x - i, y - i, w + i * 2, h + i * 2, c, radius + i, 1)
        }
    }
}
