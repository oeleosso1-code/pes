package io.iraqgameroute.x1.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthDecisionTest {
    @Test fun firstFailedCheckDoesNotDisconnect() {
        assertEquals(HealthDecision.Action.VERIFY_AGAIN, HealthDecision.next(1, false))
    }
    @Test fun repeatedChecksStopTunnelOutsideProtectedMatch() {
        assertEquals(HealthDecision.Action.STOP_TUNNEL, HealthDecision.next(2, false))
    }
    @Test fun matchProtectionOverridesAutomaticDisconnection() {
        assertEquals(HealthDecision.Action.WARN_MATCH_PROTECTED, HealthDecision.next(9, true))
    }
    @Test fun healthySessionIsRetained() {
        assertEquals(HealthDecision.Action.KEEP, HealthDecision.next(0, false))
    }
}
