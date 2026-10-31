package dev.freelocs.aetherion.platform

import com.mojang.blaze3d.platform.InputConstants
import dev.freelocs.aetherion.common.features.Capability
import dev.freelocs.aetherion.common.features.CapabilityProbe
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent

/**
 * The only place (besides the GUI renderer and the per-version `PlatformInput`) that talks to
 * Minecraft APIs whose shape changed between 26.2 and 26.3. Everything above this layer works with
 * plain Kotlin types, so version differences stay contained here.
 *
 * Known divergences (verified by diffing the 26.2 and 26.3 jars):
 * - Screen handling moved to `Minecraft.gui` in both versions (`gui.setScreen`, `gui.screen()`).
 * - Keyboard key codes are GLFW codes in 26.2 and SDL scancodes in 26.3, so keys are never persisted
 *   as numbers - only as Minecraft key names (`key.keyboard.k`), and compared via `InputConstants.KEY_*`.
 * - `InputConstants.isKeyDown` takes a Window in 26.2 but not in 26.3 (see `PlatformInput`).
 */
object Platform : CapabilityProbe {
    val mc: Minecraft get() = Minecraft.getInstance()

    fun openScreen(screen: Screen?) = mc.gui.setScreen(screen)
    fun currentScreen(): Screen? = mc.gui.screen()

    val guiScale: Int get() = mc.window.guiScale
    val fps: Int get() = mc.fps

    var clipboard: String
        get() = mc.keyboardHandler.clipboard
        set(value) { mc.keyboardHandler.clipboard = value }

    /** Language code configured in Minecraft (e.g. `de_de`). */
    val gameLanguage: String get() = mc.options.languageCode

    fun serverAddress(): String? = mc.currentServer?.ip

    fun isOnHypixel(): Boolean = serverAddress()?.lowercase()?.let { ip ->
        val host = ip.substringBefore(':')
        host == "hypixel.net" || host.endsWith(".hypixel.net")
    } == true

    override fun isAvailable(capability: Capability): Boolean = when (capability) {
        Capability.CLIENT_ONLY -> true
        Capability.ON_HYPIXEL -> isOnHypixel()
        Capability.SKYBLOCK_DATA, Capability.NETWORK -> false // no data services exist yet
    }

    // ── keys ────────────────────────────────────────────────────────────────

    fun keyName(event: KeyEvent): String = InputConstants.getKey(event).name

    /** Human readable label for a stored key name, or "" if unbound/unknown. */
    fun keyLabel(name: String): String = if (name.isEmpty()) "" else runCatching { InputConstants.getKey(name).displayName.string }.getOrDefault(name)

    private val keyValues = HashMap<String, Int>()

    /** Polled every tick, so key names are resolved once and cached (-1 = unknown name). */
    fun isKeyDown(name: String): Boolean {
        if (name.isEmpty()) return false
        val value = keyValues.getOrPut(name) { runCatching { InputConstants.getKey(name).value }.getOrDefault(-1) }
        return value >= 0 && PlatformInput.isKeyDown(value)
    }
}

/** Key constants resolved at compile time for the active target, so values are always correct for it. */
object Keys {
    const val ESCAPE = InputConstants.KEY_ESCAPE
    const val ENTER = InputConstants.KEY_RETURN
    const val ENTER_NUMPAD = InputConstants.KEY_NUMPADENTER
    const val TAB = InputConstants.KEY_TAB
    const val BACKSPACE = InputConstants.KEY_BACKSPACE
    const val DELETE = InputConstants.KEY_DELETE
    const val LEFT = InputConstants.KEY_LEFT
    const val RIGHT = InputConstants.KEY_RIGHT
    const val UP = InputConstants.KEY_UP
    const val DOWN = InputConstants.KEY_DOWN
    const val HOME = InputConstants.KEY_HOME
    const val END = InputConstants.KEY_END
    const val PAGE_UP = InputConstants.KEY_PAGEUP
    const val PAGE_DOWN = InputConstants.KEY_PAGEDOWN
    const val A = InputConstants.KEY_A
    const val C = InputConstants.KEY_C
    const val V = InputConstants.KEY_V
    const val X = InputConstants.KEY_X
    const val F = InputConstants.KEY_F
    const val Z = InputConstants.KEY_Z
    const val LSHIFT = InputConstants.KEY_LSHIFT
    const val LCONTROL = InputConstants.KEY_LCONTROL
}
