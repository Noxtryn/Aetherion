package dev.freelocs.aetherion.core

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import dev.freelocs.aetherion.gui.screens.AuroraScreen

/** Makes Aetherion reachable via ModMenu's config button, in addition to `/aetherion` and the open key. */
class AetherionModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> = ConfigScreenFactory { parent -> AuroraScreen(parent) }
}
