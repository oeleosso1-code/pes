package io.iraqgameroute.x1.network

import kotlin.math.ceil
import kotlin.math.roundToLong

/** HTTPS transaction latency; it is NOT eFootball RTT or UDP packet loss. */
data class ProbeSummary(
    val attempted: Int,
    val successful: Int,
    val medianMs: Long?,
    val p95Ms: Long?,
    val jitterMs: Long?,
    val httpFailurePercent: Double
) {
    companion object {
        fun fromMeasurements(samples: List<Long?>): ProbeSummary {
            val values = samples.filterNotNull().sorted()
            val count = samples.size
            if (count == 0) return ProbeSummary(0, 0, null, null, null, 0.0)
            val median = if (values.isEmpty()) null else percentile(values, .5)
            val p95 = if (values.isEmpty()) null else percentile(values, .95)
            // Only genuinely adjacent successful transactions are compared.
            // Do not bridge a failed transaction: it would produce fictitious jitter.
            val adjacent = samples.zipWithNext().mapNotNull { (a, b) ->
                if (a != null && b != null) kotlin.math.abs(b - a) else null
            }
            val jitter = adjacent.takeIf { it.isNotEmpty() }?.average()?.roundToLong()
            return ProbeSummary(count, values.size, median, p95, jitter,
                (count - values.size) * 100.0 / count)
        }

        private fun percentile(sorted: List<Long>, fraction: Double): Long {
            val index = (ceil(sorted.size * fraction).toInt() - 1).coerceIn(0, sorted.size - 1)
            return sorted[index]
        }
    }
}
