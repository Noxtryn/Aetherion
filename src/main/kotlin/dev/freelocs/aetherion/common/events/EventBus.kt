package dev.freelocs.aetherion.common.events

import java.util.concurrent.CopyOnWriteArrayList

/** Marker for events published on [EventBus]. */
interface AetherionEvent

class ClientTickEvent : AetherionEvent
class FeatureToggledEvent(val featureId: String, val enabled: Boolean) : AetherionEvent
class SettingChangedEvent(val featureId: String, val settingId: String) : AetherionEvent
class ProfileChangedEvent(val profile: String) : AetherionEvent
class LanguageChangedEvent : AetherionEvent

/** A handle that removes its handler from the bus. */
fun interface Subscription {
    fun cancel()
}

/**
 * Minimal synchronous typed event bus. Handlers run on the publishing thread (the client thread in
 * practice). Subscribe through a [Subscriptions] owner so lifetimes are bounded and never leak.
 */
object EventBus {
    private class Entry(val type: Class<*>, val handler: (Any) -> Unit)

    private val entries = CopyOnWriteArrayList<Entry>()

    fun <T : AetherionEvent> subscribe(type: Class<T>, handler: (T) -> Unit): Subscription {
        @Suppress("UNCHECKED_CAST")
        val entry = Entry(type) { handler(it as T) }
        entries += entry
        return Subscription { entries.remove(entry) }
    }

    inline fun <reified T : AetherionEvent> on(noinline handler: (T) -> Unit): Subscription =
        subscribe(T::class.java, handler)

    fun post(event: AetherionEvent) {
        for (e in entries) if (e.type.isInstance(event)) e.handler(event)
    }

    val subscriberCount: Int get() = entries.size
}

/** Owns a set of subscriptions and releases them together. Idempotent. */
class Subscriptions {
    private val active = ArrayList<Subscription>()

    fun add(subscription: Subscription): Subscription {
        active += subscription
        return subscription
    }

    inline fun <reified T : AetherionEvent> on(noinline handler: (T) -> Unit): Subscription = add(EventBus.on(handler))

    fun cancelAll() {
        active.forEach { it.cancel() }
        active.clear()
    }

    val size: Int get() = active.size
}
