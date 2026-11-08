package io.iraqgameroute.x1.analytics

import io.iraqgameroute.x1.network.ProbeSummary

/** Conservative HTTPS comparison; not an in-game latency benchmark. */
object RouteDecision {
    enum class Advice { DIRECT, RELAY_CANDIDATE, INSUFFICIENT_DATA }

    fun recommend(direct: ProbeSummary, relay: ProbeSummary): Advice {
        if (direct.successful < 3 || relay.successful < 3 ||
            direct.attempted < 5 || relay.attempted < 5) return Advice.INSUFFICIENT_DATA
        val d = direct.medianMs ?: return Advice.INSUFFICIENT_DATA
        val r = relay.medianMs ?: return Advice.INSUFFICIENT_DATA
        val directJitter = direct.jitterMs ?: return Advice.INSUFFICIENT_DATA
        val relayJitter = relay.jitterMs ?: return Advice.INSUFFICIENT_DATA
        val directP95 = direct.p95Ms ?: return Advice.INSUFFICIENT_DATA
        val relayP95 = relay.p95Ms ?: return Advice.INSUFFICIENT_DATA
        return if (r <= d * .85 && relayP95 <= directP95 &&
            relay.httpFailurePercent <= direct.httpFailurePercent &&
            relayJitter <= directJitter) Advice.RELAY_CANDIDATE else Advice.DIRECT
    }
}
