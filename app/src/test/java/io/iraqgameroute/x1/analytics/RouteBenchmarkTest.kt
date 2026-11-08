package io.iraqgameroute.x1.analytics

import org.junit.Assert.*
import org.junit.Test

class RouteBenchmarkTest {
    @Test fun testsBothPathsEqually() {
        var a = 0
        var b = 0
        val r = RouteBenchmark({ a++; 60L }, { b++; 45L }, { true }).run()!!
        assertEquals(8, a)
        assertEquals(8, b)
        assertEquals(8, r.direct.attempted)
        assertEquals(8, r.vpn.attempted)
    }

    @Test fun alternatesToCancelTrendBias() {
        var tick = 0L
        val visited = StringBuilder()
        val r = RouteBenchmark(
            { visited.append("D"); ++tick },
            { visited.append("V"); ++tick },
            { true }
        ).run()!!
        assertEquals("DVVDVDDVDVVDVDDV", visited.toString())
        assertEquals(8, r.direct.successful)
    }

    @Test fun dropsAllDataWhenNetworkChanges() {
        var calls = 0
        val r = RouteBenchmark({ calls++; 40L }, { calls++; 20L }, { calls < 5 }).run()
        assertNull(r)
    }

    @Test fun doesNotCountFailuresAsSuccess() {
        val r = RouteBenchmark({ null }, { 10L }, { true }).run()!!
        assertEquals(0, r.direct.successful)
        assertEquals(100.0, r.direct.httpFailurePercent, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInsufficientRounds() {
        RouteBenchmark({ 1L }, { 2L }, { true }).run(2)
    }
}
