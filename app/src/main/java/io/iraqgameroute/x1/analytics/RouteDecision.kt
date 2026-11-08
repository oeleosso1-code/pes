package io.iraqgameroute.x1.analytics

import io.iraqgameroute.x1.network.ProbeSummary

/** Conservative decision on repeated HTTPS transactions, not game network traffic. */
object RouteDecision {
    enum class Advice { DIRECT, RELAY_CANDIDATE, INSUFFICIENT_DATA }

    /** Require 8 trials per path, at least 7 successes, and consistently lower tail latency. */
    fun recommend(direct: ProbeSummary, relay: ProbeSummary): Advice {
        if (direct.attempted < 8 || relay.attempted < 8 ||
            direct.successful < 7 || relay.successful < 7) return Advice.INSUFFICIENT_DATA
        val d = direct.medianMs ?: return Advice.INSUFFICIENT_DATA
        val r = relay.medianMs ?: return Advice.INSUFFICIENT_DATA
        val dJitter = direct.jitterMs ?: return Advice.INSUFFICIENT_DATA
        val rJitter = relay.jitterMs ?: return Advice.INSUFFICIENT_DATA
        val dP95 = direct.p95Ms ?: return Advice.INSUFFICIENT_DATA
        val rP95 = relay.p95Ms ?: return Advice.INSUFFICIENT_DATA

        return if (d > 0 && r <= d * .85 && (d - r) >= 8 &&
            rP95 <= dP95 && rJitter <= maxOf(dJitter, 4L) &&
            relay.httpFailurePercent <= direct.httpFailurePercent
        ) Advice.RELAY_CANDIDATE else Advice.DIRECT
    }
}
