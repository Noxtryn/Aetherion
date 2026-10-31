package dev.freelocs.aetherion.common.features

import dev.freelocs.aetherion.common.config.ConfigStore
import dev.freelocs.aetherion.common.events.Subscriptions
import dev.freelocs.aetherion.core.Log

/** Why a feature is (not) running right now. Drives the status chip in the detail panel. */
enum class RuntimeStatus { ACTIVE, DISABLED, BLOCKED_BY_DEPENDENCY, AWAITING_CAPABILITY, PLANNED, REVIEW_REQUIRED, FAILED }

fun interface CapabilityProbe {
    fun isAvailable(capability: Capability): Boolean
}

/**
 * Drives feature lifecycles from configuration. A feature is active iff it is implemented, enabled,
 * all its dependencies are active and all its capabilities are available. [reconcile] is idempotent,
 * so toggling, profile switches and capability changes all funnel through the same path and a feature
 * can never be activated twice or leak its subscriptions.
 */
class FeatureManager(private val config: ConfigStore, private val probe: CapabilityProbe) {
    private class Runtime(val def: FeatureDefinition) {
        var feature: Feature? = null
        var initialised = false
        var active = false
        var failed = false
        val subscriptions = Subscriptions()
        val context = FeatureContext(def, subscriptions)
    }

    private val runtimes = LinkedHashMap<String, Runtime>()

    private fun runtime(def: FeatureDefinition) = runtimes.getOrPut(def.id) { Runtime(def) }

    fun isActive(id: String) = runtimes[id]?.active == true

    fun status(def: FeatureDefinition): RuntimeStatus {
        if (def.status == FeatureStatus.PLANNED) return RuntimeStatus.PLANNED
        if (def.status == FeatureStatus.REVIEW_REQUIRED) return RuntimeStatus.REVIEW_REQUIRED
        val rt = runtimes[def.id]
        if (rt?.failed == true) return RuntimeStatus.FAILED
        if (rt?.active == true) return RuntimeStatus.ACTIVE
        if (!config.isEnabled(def)) return RuntimeStatus.DISABLED
        if (def.dependencies.any { id -> FeatureRegistry.feature(id)?.let { !isActive(it.id) } != false }) return RuntimeStatus.BLOCKED_BY_DEPENDENCY
        if (!capabilitiesMet(def)) return RuntimeStatus.AWAITING_CAPABILITY
        return RuntimeStatus.DISABLED
    }

    /** Capabilities the feature needs that are currently unavailable. */
    fun missingCapabilities(def: FeatureDefinition) = def.capabilities.filterNot { probe.isAvailable(it) }

    private fun capabilitiesMet(def: FeatureDefinition) = def.capabilities.all { probe.isAvailable(it) }

    fun setEnabled(def: FeatureDefinition, enabled: Boolean) {
        config.setEnabled(def, enabled)
        reconcile()
    }

    /** Brings runtime state in line with configuration. Safe to call at any time, any number of times. */
    fun reconcile() {
        val desired = LinkedHashSet<String>()
        for (def in FeatureRegistry.features) {
            if (def.status == FeatureStatus.IMPLEMENTED && config.isEnabled(def) && capabilitiesMet(def)) desired += def.id
        }
        var changed = true
        while (changed) {
            changed = desired.removeAll { id ->
                FeatureRegistry.feature(id)!!.dependencies.any { it !in desired }
            }
        }
        for (rt in runtimes.values.reversed()) {
            if (rt.active && rt.def.id !in desired) deactivate(rt)
        }
        for (def in FeatureRegistry.features) {
            if (def.id in desired) activate(runtime(def))
        }
    }

    private fun activate(rt: Runtime) {
        if (rt.active || rt.failed) return
        safely(rt, "activate") {
            if (!rt.initialised) {
                rt.feature = rt.def.factory?.invoke()
                rt.feature?.onInit(rt.context)
                rt.initialised = true
            }
            rt.feature?.onActivate(rt.context)
            rt.active = true
        }
    }

    private fun deactivate(rt: Runtime) {
        if (!rt.active) return
        rt.active = false
        safely(rt, "deactivate") { rt.feature?.onDeactivate(rt.context) }
        rt.subscriptions.cancelAll()
    }

    private var lastCapabilities = -1
    private var tickCounter = 0

    /** Cheap periodic check (once per second) that re-reconciles only when capability availability changed. */
    fun reconcileIfCapabilitiesChanged() {
        if (++tickCounter % 20 != 0) return
        var mask = 0
        for (c in Capability.entries) if (probe.isAvailable(c)) mask = mask or (1 shl c.ordinal)
        if (mask != lastCapabilities) { lastCapabilities = mask; reconcile() }
    }

    /** Deactivates and cleans up everything. Call on client shutdown. */
    fun shutdown() {
        for (rt in runtimes.values.reversed()) {
            deactivate(rt)
            if (rt.initialised) safely(rt, "cleanup") { rt.feature?.onCleanup() }
            rt.initialised = false
        }
    }

    private inline fun safely(rt: Runtime, phase: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            rt.failed = true
            rt.active = false
            rt.subscriptions.cancelAll()
            Log.error("Feature '${rt.def.id}' failed during $phase; it has been disabled for this session", t)
        }
    }
}
