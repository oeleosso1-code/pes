package io.iraqgameroute.x1.analytics

import io.iraqgameroute.x1.network.ProbeSummary

/**
 * ABBA/BAAB repeated HTTPS transaction benchmark.
 * Interleaving reduces time-order bias when the underlying ISP gets better or worse.
 * This is NOT an eFootball RTT, UDP packet loss, or a latency guarantee.
 *
 * Returning null means the network/VPN was changed during sampling; partial results
 * must never be presented as if they described one stable route.
 */
class RouteBenchmark(
    private val directProbe: () -> Long?,
    private val vpnProbe: () -> Long?,
    private val isSameConnection: () -> Boolean
) {
    data class Result(val direct: ProbeSummary, val vpn: ProbeSummary)

    fun run(rounds: Int = 4): Result? {
        require(rounds in 3..8) { "Rounds must be 3..8" }
        val directSamples = ArrayList<Long?>(rounds * 2)
        val vpnSamples = ArrayList<Long?>(rounds * 2)
        repeat(rounds) { round ->
            // Alternate ABBA and BAAB, to avoid favoring the first measured path.
            val order = if (round % 2 == 0) intArrayOf(0, 1, 1, 0)
                        else intArrayOf(1, 0, 0, 1)
            for (route in order) {
                if (!isSameConnection()) return null
                val measured = if (route == 0) directProbe() else vpnProbe()
                if (!isSameConnection()) return null
                if (route == 0) directSamples.add(measured) else vpnSamples.add(measured)
            }
        }
        return Result(
            ProbeSummary.fromMeasurements(directSamples),
            ProbeSummary.fromMeasurements(vpnSamples)
        )
    }
}
