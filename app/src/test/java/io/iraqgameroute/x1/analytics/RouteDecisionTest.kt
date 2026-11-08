package io.iraqgameroute.x1.analytics

import io.iraqgameroute.x1.network.ProbeSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteDecisionTest {
    @Test fun onlyRecommendsClearlyBetterStableRoute() {
        val direct = ProbeSummary.fromMeasurements(listOf(100, 102, 98, 100, 100, 101, 99, 100))
        val relay = ProbeSummary.fromMeasurements(listOf(70, 72, 68, 70, 70, 71, 69, 70))
        assertEquals(RouteDecision.Advice.RELAY_CANDIDATE, RouteDecision.recommend(direct, relay))
    }
    @Test fun noImprovementDefaultsToDirect() {
        val direct = ProbeSummary.fromMeasurements(listOf(90, 91, 92, 90, 91, 91, 90, 92))
        val relay = ProbeSummary.fromMeasurements(listOf(89, 100, 91, 90, 89, 95, 94, 90))
        assertEquals(RouteDecision.Advice.DIRECT, RouteDecision.recommend(direct, relay))
    }
    @Test fun missingDataCannotBeCalledImprovement() {
        val direct = ProbeSummary.fromMeasurements(listOf(90, 92, 91))
        val relay = ProbeSummary.fromMeasurements(listOf(null, null, 30))
        assertEquals(RouteDecision.Advice.INSUFFICIENT_DATA, RouteDecision.recommend(direct, relay))
    }
    @Test fun worseTailLatencyMustNotBeRecommended() {
        val direct = ProbeSummary.fromMeasurements(listOf(100, 102, 98, 100, 100, 101, 99, 100))
        val relay = ProbeSummary.fromMeasurements(listOf(50, 50, 50, 50, 50, 50, 50, 200))
        assertEquals(RouteDecision.Advice.DIRECT, RouteDecision.recommend(direct, relay))
    }
    @Test fun tooManyFailuresMakeEvidenceInsufficient() {
        val direct = ProbeSummary.fromMeasurements(List(8) { 100L })
        val relay = ProbeSummary.fromMeasurements(listOf(40, null, 40, null, 40, 40, 40, 40))
        assertEquals(RouteDecision.Advice.INSUFFICIENT_DATA, RouteDecision.recommend(direct, relay))
    }
    @Test fun tinyAbsoluteGainCannotBeAdvertisedAsBetter() {
        val direct = ProbeSummary.fromMeasurements(List(8) { 30L })
        val relay = ProbeSummary.fromMeasurements(List(8) { 25L })
        assertEquals(RouteDecision.Advice.DIRECT, RouteDecision.recommend(direct, relay))
    }
}
