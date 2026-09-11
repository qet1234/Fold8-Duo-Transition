package com.qet1234.fold8duotransition

/** Bounded, unfiltered evidence. Repeated/out-of-order timestamps are rejected per active segment. */
class HingeSamples {
    data class Sample(val segment: Int, val timestampNs: Long, val angle: Float)
    private val buffer = ArrayDeque<Sample>()
    private val distinct = HashSet<Float>()
    private var segment = 0
    private var previousTimestamp = -1L
    var count = 0
        private set
    var rejected = 0
        private set
    var minimum: Float? = null
        private set
    var maximum: Float? = null
        private set
    var last: Float? = null
        private set
    val distinctCount get() = distinct.size
    val samples get() = buffer.toList()

    fun beginSegment() { segment++; previousTimestamp = -1L }

    fun add(angle: Float, timestampNs: Long): Boolean {
        if (!angle.isFinite() || angle < 0f || angle > 360f || timestampNs < 0 || timestampNs <= previousTimestamp) {
            rejected++; return false
        }
        previousTimestamp = timestampNs
        count++
        minimum = minOf(minimum ?: angle, angle)
        maximum = maxOf(maximum ?: angle, angle)
        last = angle
        // Bound distinct values too: this is evidence of variation, not a precision test.
        if (distinct.size < 4096) distinct.add(angle)
        if (buffer.size == 1000) buffer.removeFirst()
        buffer.addLast(Sample(segment, timestampNs, angle))
        return true
    }
}
