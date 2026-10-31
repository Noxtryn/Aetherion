package dev.freelocs.aetherion.core

import org.slf4j.LoggerFactory

/** Central logger so every subsystem reports under the same "Aetherion" tag. */
object Log {
    private val logger = LoggerFactory.getLogger("Aetherion")

    fun info(message: String) = logger.info(message)
    fun warn(message: String) = logger.warn(message)
    fun error(message: String, t: Throwable? = null) = if (t != null) logger.error(message, t) else logger.error(message)
    fun debug(message: String) = logger.debug(message)
}
