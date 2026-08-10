package dev.freelocs.aetherion.core

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import dev.freelocs.aetherion.gui.AetherionScreen

/** Makes Aetherion reachable via ModMenu's config icon, in addition to `/aetherion`. */
class AetherionModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
        ConfigScreenFactory { parent -> AetherionScreen(parent) }
}
