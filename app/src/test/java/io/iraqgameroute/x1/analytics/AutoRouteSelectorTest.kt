package io.iraqgameroute.x1.analytics

import io.iraqgameroute.x1.network.ProbeSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoRouteSelectorTest {
    private fun summary(vararg values: Long?): ProbeSummary =
        ProbeSummary.fromMeasurements(values.toList())
    private fun flat(n: Long): ProbeSummary = summary(*Array<Long?>(8) { n })

    @Test fun noProfilesKeepDirect() = assertNull(AutoRouteSelector.choose(emptyList()))
    @Test fun worseVpnKeepsDirect() = assertNull(AutoRouteSelector.choose(listOf(
        AutoRouteSelector.Observation(0, flat(70), flat(95))
    )))
    @Test fun onlyClearGainIsEligible() {
        val selected = AutoRouteSelector.choose(listOf(
            AutoRouteSelector.Observation(0, flat(100), flat(95)),
            AutoRouteSelector.Observation(1, flat(100), flat(70))
        ))
        assertEquals(1, selected?.profileIndex)
    }
    @Test fun prefersLowerTailLatencyOverLowerMedian() {
        val d = flat(160)
        val stable = summary(83, 81, 80, 82, 81, 80, 82, 81)
        val spiky = summary(50, 50, 50, 50, 50, 50, 50, 130)
        // The spiky candidate must first pass the regular policy: no worse than direct tail.
        val selected = AutoRouteSelector.choose(listOf(
            AutoRouteSelector.Observation(0, d, spiky),
            AutoRouteSelector.Observation(1, d, stable)
        ))
        assertEquals(1, selected?.profileIndex)
    }
    @Test fun missingResultsNeverWin() {
        assertNull(AutoRouteSelector.choose(listOf(
            AutoRouteSelector.Observation(2, flat(100), summary(null, null, 20, null, null, 20, null, 20))
        )))
    }
}
