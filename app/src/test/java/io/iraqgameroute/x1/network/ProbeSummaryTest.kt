package io.iraqgameroute.x1.network

import org.junit.Assert.*
import org.junit.Test

class ProbeSummaryTest {
    @Test fun allRequestsFailedDoesNotProduceFakeLatency() {
        val summary = ProbeSummary.fromMeasurements(listOf(null, null, null))
        assertNull(summary.medianMs)
        assertEquals(100.0, summary.httpFailurePercent, 0.001)
        assertEquals(0, summary.successful)
    }

    @Test fun summarizesSuccessAndFailure() {
        val summary = ProbeSummary.fromMeasurements(listOf(30, 40, null, 50, 70, 110))
        assertEquals(5, summary.successful)
        assertEquals(50L, summary.medianMs)
        assertEquals(110L, summary.p95Ms)
        assertEquals(23L, summary.jitterMs)
        assertEquals(100.0 / 6, summary.httpFailurePercent, 0.001)
    }

    @Test fun interruptionsDoNotFakeAdjacentJitter() {
        val s = ProbeSummary.fromMeasurements(listOf(10, null, 90, null, 30))
        assertNull(s.jitterMs)
    }
    @Test fun nearbySuccessesUseOnlyValidPairs() {
        val s = ProbeSummary.fromMeasurements(listOf(10, 25, null, 100, 110))
        assertEquals(13L, s.jitterMs)
    }
    @Test fun emptyInputsAreSupported() {
        assertEquals(0, ProbeSummary.fromMeasurements(emptyList()).attempted)
    }
}
