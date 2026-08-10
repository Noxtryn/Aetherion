package dev.freelocs.aetherion.core

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

/** Loads/saves [AetherionConfig] as pretty-printed JSON in the Fabric config folder. */
object ConfigManager {
    private val logger = LoggerFactory.getLogger("Aetherion/Config")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configFile get() = FabricLoader.getInstance().configDir.resolve("aetherion.json").toFile()

    var config: AetherionConfig = AetherionConfig()

    fun load() {
        if (!configFile.exists()) {
            save()
            return
        }
        runCatching {
            config = gson.fromJson(configFile.readText(), AetherionConfig::class.java) ?: AetherionConfig()
        }.onFailure {
            logger.error("Failed to load Aetherion config, using defaults", it)
            config = AetherionConfig()
        }
    }

    fun save() {
        runCatching {
            configFile.parentFile?.mkdirs()
            configFile.writeText(gson.toJson(config))
        }.onFailure {
            logger.error("Failed to save Aetherion config", it)
        }
    }
}
