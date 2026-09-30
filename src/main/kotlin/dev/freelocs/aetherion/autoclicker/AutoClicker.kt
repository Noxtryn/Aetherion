package dev.freelocs.aetherion.autoclicker

import dev.freelocs.aetherion.core.ConfigManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import kotlin.random.Random

/**
 * Always-on, mob-targeted auto clicker.
 *
 * Triggers only while the crosshair is on a mob selected in the config (by type or server name)
 * and within attack reach. It then clicks at 15 CPS in bursts of a random 3–5 clicks, each burst
 * followed by an extra 50 ms pause, until that mob dies/despawns, you look away, or you die.
 *
 * Targeting is evaluated per client tick (that's when Minecraft updates what the crosshair is on);
 * clicks are scheduled per rendered frame, so 15 CPS is timed precisely rather than rounded to
 * 50 ms ticks. Each click is queued on the attack key exactly like a physical mouse click, so the
 * vanilla attack path (swing, attack packet, cooldown) handles it.
 */
object AutoClicker {
    private const val CPS = 15
    private const val CLICK_INTERVAL_NS = 1_000_000_000L / CPS
    private const val BURST_PAUSE_NS = 50_000_000L
    private const val BURST_MIN = 3
    private const val BURST_MAX = 5
    private const val DISCOVERY_INTERVAL_TICKS = 20

    /** The selected mob currently being clicked; cleared when it dies/despawns or you die. */
    private var target: Entity? = null
    private var lookingAtTarget = false

    private var clicking = false
    private var clicksLeftInBurst = 0
    private var nextClickAt = 0L
    private var lastClickAt = 0L

    private var tickCounter = 0
    private var discoveryDirty = false

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::onTick)
        LevelRenderEvents.END_MAIN.register { onFrame(Minecraft.getInstance()) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            reset()
            saveDiscoveries()
        }
    }

    private fun onTick(mc: Minecraft) {
        val player = mc.player
        val level = mc.level
        if (player == null || level == null) {
            reset()
            return
        }

        if (++tickCounter % DISCOVERY_INTERVAL_TICKS == 0) discoverMobs(mc)

        // You died: stop and forget the target.
        if (!player.isAlive || player.isDeadOrDying) {
            reset()
            return
        }

        // Target died or despawned: stop.
        target?.let { if (!isAliveMob(it)) target = null }

        lookingAtTarget = false
        val config = ConfigManager.config.autoClicker
        if (!config.enabled || config.selected.isEmpty()) return

        val looked = mc.crosshairPickEntity ?: return
        if (!isAliveMob(looked)) return
        if (MobCatalog.keysOf(looked, level).none { it in config.selected }) return

        target = looked
        lookingAtTarget = true
    }

    private fun onFrame(mc: Minecraft) {
        val player = mc.player
        val shouldClick = lookingAtTarget &&
            target?.let(::isAliveMob) == true &&
            player != null && player.isAlive &&
            mc.screen == null && mc.overlay == null
        if (!shouldClick) {
            clicking = false
            return
        }

        val now = System.nanoTime()
        if (!clicking) {
            // (Re)start with a fresh burst, but never faster than 15 CPS across a restart.
            clicking = true
            clicksLeftInBurst = randomBurst()
            nextClickAt = maxOf(now, lastClickAt + CLICK_INTERVAL_NS)
        }
        if (now < nextClickAt) return

        click(mc)
        lastClickAt = now

        val delay = if (--clicksLeftInBurst <= 0) {
            clicksLeftInBurst = randomBurst()
            CLICK_INTERVAL_NS + BURST_PAUSE_NS
        } else {
            CLICK_INTERVAL_NS
        }
        // Keep the schedule on its grid, but don't fire a catch-up volley after a lag spike.
        nextClickAt = if (now - nextClickAt > CLICK_INTERVAL_NS) now + delay else nextClickAt + delay
    }

    private fun click(mc: Minecraft) {
        KeyMapping.click(KeyMappingHelper.getBoundKeyOf(mc.options.keyAttack))
    }

    private fun randomBurst() = Random.nextInt(BURST_MIN, BURST_MAX + 1)

    private fun isAliveMob(entity: Entity): Boolean =
        MobCatalog.isMob(entity) && !entity.isRemoved && entity.isAlive && !(entity as LivingEntity).isDeadOrDying

    private fun discoverMobs(mc: Minecraft) {
        val level = mc.level ?: return
        val server = MobCatalog.serverKey(mc)
        for (entity in level.entitiesForRendering()) {
            if (MobCatalog.isMob(entity) && MobCatalog.discover(entity, level, server)) discoveryDirty = true
        }
    }

    /** Persists newly seen mobs (called on disconnect and whenever the config screen closes). */
    fun saveDiscoveries() {
        if (!discoveryDirty) return
        discoveryDirty = false
        ConfigManager.save()
    }

    private fun reset() {
        target = null
        lookingAtTarget = false
        clicking = false
    }
}
