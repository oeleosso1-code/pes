package io.iraqgameroute.x1.analytics

import io.iraqgameroute.x1.network.ProbeSummary

/**
 * Deterministic, conservative selector for PRE-MATCH HTTPS measurements.
 * It never claims to measure Konami RTT. A missing/uncertain result means DIRECT.
 */
object AutoRouteSelector {
    data class Observation(
        val profileIndex: Int,
        val direct: ProbeSummary,
        val relay: ProbeSummary
    )

    fun choose(results: List<Observation>): Observation? = results
        .filter { it.profileIndex >= 0 && RouteDecision.recommend(it.direct, it.relay) ==
            RouteDecision.Advice.RELAY_CANDIDATE }
        .minWithOrNull(compareBy<Observation> { it.relay.p95Ms ?: Long.MAX_VALUE }
            .thenBy { it.relay.medianMs ?: Long.MAX_VALUE }
            .thenBy { it.relay.jitterMs ?: Long.MAX_VALUE }
            .thenBy { it.profileIndex })
}
