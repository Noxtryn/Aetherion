package dev.freelocs.aetherion.gui.engine

/**
 * Rolling measurement of the config screen's own render cost, taken with System.nanoTime around the
 * whole frame body. Feeds the performance page; nothing here is estimated.
 */
object PerfStats {
    const val CAPACITY = 90
    private val samplesNs = LongArray(CAPACITY)
    private var count = 0
    private var head = 0

    var lastDrawCalls = 0
        private set

    val sampleCount get() = count

    fun record(frameNs: Long, drawCalls: Int) {
        samplesNs[head] = frameNs
        head = (head + 1) % CAPACITY
        if (count < CAPACITY) count++
        lastDrawCalls = drawCalls
    }

    fun averageMs(): Float {
        if (count == 0) return 0f
        var sum = 0L
        for (i in 0 until count) sum += samplesNs[i]
        return sum / count / 1_000_000f
    }

    fun maxMs(): Float {
        var m = 0L
        for (i in 0 until count) if (samplesNs[i] > m) m = samplesNs[i]
        return m / 1_000_000f
    }

    /** Sample [i] in chronological order (0 = oldest) in milliseconds. */
    fun sampleMs(i: Int): Float {
        val idx = if (count < CAPACITY) i else (head + i) % CAPACITY
        return samplesNs[idx] / 1_000_000f
    }

    fun reset() { count = 0; head = 0; lastDrawCalls = 0 }
}
