package dev.freelocs.aetherion.platform

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft

/** Minecraft 26.2: `InputConstants.isKeyDown` requires the window handle. */
internal object PlatformInput {
    fun isKeyDown(keyValue: Int): Boolean = InputConstants.isKeyDown(Minecraft.getInstance().window, keyValue)
}
