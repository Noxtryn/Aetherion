package dev.freelocs.aetherion.common

import dev.freelocs.aetherion.common.config.ConfigStore
import dev.freelocs.aetherion.common.events.EventBus
import dev.freelocs.aetherion.common.events.ClientTickEvent
import dev.freelocs.aetherion.common.features.Capability
import dev.freelocs.aetherion.common.features.CategoryDefinition
import dev.freelocs.aetherion.common.features.Feature
import dev.freelocs.aetherion.common.features.FeatureContext
import dev.freelocs.aetherion.common.features.FeatureDefinition
import dev.freelocs.aetherion.common.features.FeatureManager
import dev.freelocs.aetherion.common.features.FeatureRegistry
import dev.freelocs.aetherion.common.features.GroupDefinition
import dev.freelocs.aetherion.common.features.RuntimeStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class FeatureManagerTest {
    @TempDir
    lateinit var dir: Path

    private class Probe : Feature {
        var inits = 0; var activations = 0; var deactivations = 0; var cleanups = 0; var ticks = 0
        override fun onInit(ctx: FeatureContext) { inits++ }
        override fun onActivate(ctx: FeatureContext) {
            activations++
            ctx.subscriptions.on<ClientTickEvent> { ticks++ }
        }
        override fun onDeactivate(ctx: FeatureContext) { deactivations++ }
        override fun onCleanup() { cleanups++ }
    }

    private val probes = HashMap<String, Probe>()
    private var hypixel = false
    private lateinit var config: ConfigStore
    private lateinit var manager: FeatureManager

    private fun def(id: String, deps: List<String> = emptyList(), caps: Set<Capability> = setOf(Capability.CLIENT_ONLY), factory: (() -> Feature)? = { Probe().also { probes[id] = it } }) =
        FeatureDefinition(id, "c", "t", "d", false, dependencies = deps, capabilities = caps, factory = factory)

    @BeforeEach
    fun setUp() {
        FeatureRegistry.clear(); probes.clear(); hypixel = false
        FeatureRegistry.registerGroup(GroupDefinition("g", "g"))
        FeatureRegistry.registerCategory(CategoryDefinition("c", "g", "c", "c", "info"))
        FeatureRegistry.registerFeature(def("base"))
        FeatureRegistry.registerFeature(def("child", listOf("base")))
        FeatureRegistry.registerFeature(def("grandchild", listOf("child")))
        FeatureRegistry.registerFeature(def("online", caps = setOf(Capability.ON_HYPIXEL)))
        FeatureRegistry.registerFeature(def("boom", factory = { object : Feature { override fun onActivate(ctx: FeatureContext) = error("boom") } }))
        config = ConfigStore(dir.resolve("c.json"))
        manager = FeatureManager(config) { cap -> cap == Capability.CLIENT_ONLY || (cap == Capability.ON_HYPIXEL && hypixel) }
    }

    private fun enable(id: String, on: Boolean = true) = manager.setEnabled(FeatureRegistry.feature(id)!!, on)
    private fun status(id: String) = manager.status(FeatureRegistry.feature(id)!!)

    @Test
    fun `enabling activates exactly once even if reconciled repeatedly`() {
        enable("base"); repeat(5) { manager.reconcile() }
        assertTrue(manager.isActive("base"))
        assertEquals(1, probes["base"]!!.inits); assertEquals(1, probes["base"]!!.activations)
    }

    @Test
    fun `a feature waits for its dependency and starts when it appears`() {
        enable("child")
        assertEquals(RuntimeStatus.BLOCKED_BY_DEPENDENCY, status("child"))
        assertFalse(manager.isActive("child"))
        enable("base")
        assertTrue(manager.isActive("child"))
    }

    @Test
    fun `disabling a dependency cascades to dependants and back again`() {
        enable("base"); enable("child"); enable("grandchild")
        assertTrue(manager.isActive("grandchild"))
        enable("base", false)
        assertFalse(manager.isActive("child")); assertFalse(manager.isActive("grandchild"))
        assertEquals(RuntimeStatus.BLOCKED_BY_DEPENDENCY, status("grandchild"))
        enable("base")
        assertTrue(manager.isActive("child")); assertTrue(manager.isActive("grandchild"))
        assertEquals(2, probes["child"]!!.activations)
        assertEquals(1, probes["child"]!!.inits)
    }

    @Test
    fun `event subscriptions are released on deactivate and never duplicated`() {
        enable("base")
        EventBus.post(ClientTickEvent())
        assertEquals(1, probes["base"]!!.ticks)
        val subsActive = EventBus.subscriberCount
        enable("base", false)
        assertEquals(subsActive - 1, EventBus.subscriberCount)
        EventBus.post(ClientTickEvent())
        assertEquals(1, probes["base"]!!.ticks)
        repeat(3) { enable("base"); enable("base", false) }
        assertEquals(subsActive - 1, EventBus.subscriberCount)
    }

    @Test
    fun `capabilities gate activation and are re-evaluated`() {
        enable("online")
        assertEquals(RuntimeStatus.AWAITING_CAPABILITY, status("online"))
        assertEquals(listOf(Capability.ON_HYPIXEL), manager.missingCapabilities(FeatureRegistry.feature("online")!!))
        hypixel = true; manager.reconcile()
        assertTrue(manager.isActive("online"))
        hypixel = false; manager.reconcile()
        assertFalse(manager.isActive("online"))
    }

    @Test
    fun `a crashing feature is isolated and reported as failed`() {
        enable("boom")
        assertEquals(RuntimeStatus.FAILED, status("boom"))
        assertFalse(manager.isActive("boom"))
        enable("base")
        assertTrue(manager.isActive("base"))
    }

    @Test
    fun `shutdown deactivates and cleans up each feature once`() {
        enable("base"); enable("child")
        manager.shutdown(); manager.shutdown()
        for (id in listOf("base", "child")) {
            assertEquals(1, probes[id]!!.deactivations, id)
            assertEquals(1, probes[id]!!.cleanups, id)
            assertFalse(manager.isActive(id))
        }
    }
}
