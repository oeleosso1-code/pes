package io.iraqgameroute.x1.analytics

import io.iraqgameroute.x1.network.ProbeSummary

/** Diagnostic-only advice. Never changes the VPN while the game is running. */
object RouteDecision {
    enum class Advice { DIRECT, RELAY_CANDIDATE, INSUFFICIENT_DATA }

    /** Conservative: demand both lower median and non-worse failure ratio. */
    fun recommend(direct: ProbeSummary, relay: ProbeSummary): Advice {
        if (direct.successful < 3 || relay.successful < 3) return Advice.INSUFFICIENT_DATA
        val d = direct.medianMs ?: return Advice.INSUFFICIENT_DATA
        val r = relay.medianMs ?: return Advice.INSUFFICIENT_DATA
        return if (r <= d * .85 && relay.httpFailurePercent <= direct.httpFailurePercent &&
            (relay.jitterMs ?: Long.MAX_VALUE) <= (direct.jitterMs ?: Long.MAX_VALUE)) {
            Advice.RELAY_CANDIDATE
        } else Advice.DIRECT
    }
}
