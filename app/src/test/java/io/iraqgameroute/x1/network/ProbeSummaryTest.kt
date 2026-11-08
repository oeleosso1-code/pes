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
        assertEquals(20L, summary.jitterMs)
        assertEquals(100.0 / 6, summary.httpFailurePercent, 0.001)
    }

    @Test fun emptyInputsAreSupported() {
        assertEquals(0, ProbeSummary.fromMeasurements(emptyList()).attempted)
    }
}
