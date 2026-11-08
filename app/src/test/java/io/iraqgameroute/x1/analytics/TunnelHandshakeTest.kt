package io.iraqgameroute.x1.analytics

import org.junit.Assert.*
import org.junit.Test

class TunnelHandshakeTest {
    @Test fun initialHandshakeIsNotHealthy() {
        assertFalse(TunnelHandshake.isFresh(0L, 1700000000000L))
    }
    @Test fun oldHandshakeIsNotHealthy() {
        assertFalse(TunnelHandshake.isFresh(1699999000000L, 1700000000000L))
    }
    @Test fun currentHandshakeIsHealthy() {
        assertTrue(TunnelHandshake.isFresh(1699999999000L, 1700000000000L))
    }
    @Test fun majorClockErrorIsRejected() {
        assertFalse(TunnelHandshake.isFresh(1700000200000L, 1700000000000L))
    }
}
