package dev.freelocs.aetherion.core

import com.mojang.brigadier.Command
import dev.freelocs.aetherion.common.events.ClientTickEvent
import dev.freelocs.aetherion.common.events.EventBus
import dev.freelocs.aetherion.common.features.FeatureRegistry
import dev.freelocs.aetherion.gui.editor.AuroraHudEditor
import dev.freelocs.aetherion.gui.screens.AuroraScreen
import dev.freelocs.aetherion.hud.HudManager
import dev.freelocs.aetherion.platform.Platform
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

/** Fabric client entry point: initialises services, registers `/aetherion`, the HUD layer and the open-key handler. */
object AetherionMod : ClientModInitializer {
    const val MOD_ID = Aetherion.ID

    private var openKeyWasDown = false
    private var editorKeyWasDown = false

    override fun onInitializeClient() {
        Aetherion.init()
        HudManager.install()

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ -> registerCommands(dispatcher) }

        ClientTickEvents.END_CLIENT_TICK.register {
            pollKeys()
            EventBus.post(ClientTickEvent())
            Aetherion.features.reconcileIfCapabilitiesChanged()
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register { Aetherion.shutdown() }

        DevHarness.install()
        Log.info("Aetherion ${Aetherion.version} ready (${FeatureRegistry.features.size} catalog entries). Open with /aetherion")
    }

    /** `/aetherion` opens the config screen, `/aetherion hud` the HUD layout editor. */
    fun registerCommands(dispatcher: com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>) {
        dispatcher.register(
            literal("aetherion")
                .executes { openConfig(); Command.SINGLE_SUCCESS }
                .then(literal("hud").executes { openEditor(); Command.SINGLE_SUCCESS })
        )
    }

    fun openConfig(parent: net.minecraft.client.gui.screens.Screen? = null) {
        val mc = Platform.mc
        mc.execute { Platform.openScreen(AuroraScreen(parent ?: Platform.currentScreen())) }
    }

    fun openEditor() {
        val mc = Platform.mc
        mc.execute { Platform.openScreen(AuroraHudEditor(Platform.currentScreen())) }
    }

    /** Rising-edge detection so holding the key opens the screen once. Only active while no screen is open. */
    private fun pollKeys() {
        if (Platform.currentScreen() != null) { openKeyWasDown = true; editorKeyWasDown = true; return }
        val open = Platform.isKeyDown(Aetherion.config.string("general.interface", "openKey"))
        if (open && !openKeyWasDown) openConfig()
        openKeyWasDown = open
        val editor = Platform.isKeyDown(Aetherion.config.string("hud.core", "editorKey"))
        if (editor && !editorKeyWasDown) openEditor()
        editorKeyWasDown = editor
    }
}
