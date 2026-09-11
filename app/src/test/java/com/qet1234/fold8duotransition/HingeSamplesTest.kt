package com.qet1234.fold8duotransition

import org.junit.Assert.*
import org.junit.Test

class HingeSamplesTest {
    @Test fun rejectsInvalidEvidenceWithoutChangingMeasuredRange() {
        val s = HingeSamples()
        assertTrue(s.add(90f, 10))
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 361f).forEach { assertFalse(s.add(it, 20)) }
        assertFalse(s.add(120f, 10))
        assertFalse(s.add(120f, 9))
        assertEquals(1, s.count)
        assertEquals(90f, s.minimum!!, 0f)
        assertEquals(90f, s.maximum!!, 0f)
    }
    @Test fun pauseSeparatesEvidenceAndPreservesReversal() {
        val s = HingeSamples()
        s.add(60f, 100); s.add(90f, 200); s.add(60f, 300)
        s.beginSegment(); assertTrue(s.add(180f, 400))
        assertEquals(listOf(60f, 90f, 60f, 180f), s.samples.map { it.angle })
        assertNotEquals(s.samples[2].segment, s.samples[3].segment)
        assertEquals(3, s.distinctCount)
    }
    @Test fun longSessionsKeepBoundedHistoryAndFullObservedRange() {
        val s = HingeSamples()
        repeat(5000) { assertTrue(s.add((it % 181).toFloat(), it.toLong())) }
        assertEquals(1000, s.samples.size)
        assertEquals(5000, s.count)
        assertEquals(0f, s.minimum!!, 0f)
        assertEquals(180f, s.maximum!!, 0f)
        assertEquals(4000L, s.samples.first().timestampNs)
    }
}
