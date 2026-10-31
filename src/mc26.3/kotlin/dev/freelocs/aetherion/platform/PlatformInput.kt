package dev.freelocs.aetherion.platform

import com.mojang.blaze3d.platform.InputConstants

/** Minecraft 26.3: key state is queried without a window handle (SDL based input). */
internal object PlatformInput {
    fun isKeyDown(keyValue: Int): Boolean = InputConstants.isKeyDown(keyValue)
}
