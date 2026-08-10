package dev.freelocs.aetherion.core

import dev.freelocs.aetherion.gui.AetherionScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory

object AetherionMod : ClientModInitializer {
    const val MOD_ID = "aetherion"
    const val VERSION = "0.1.0"

    private val logger = LoggerFactory.getLogger("Aetherion")

    override fun onInitializeClient() {
        ConfigManager.load()

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                literal("aetherion").executes {
                    val mc = Minecraft.getInstance()
                    mc.execute { mc.setScreen(AetherionScreen(mc.screen)) }
                    1
                }
            )
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            ConfigManager.save()
        }

        logger.info("Aetherion $VERSION scaffold initialized — open config with /aetherion")
    }
}
