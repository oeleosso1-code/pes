package io.iraqgameroute.x1.network

import android.net.Network
import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL

/** Performs consent-driven HTTPS reachability tests to public endpoints, never to game servers. */
class HttpsProbe {
    private val urls = listOf(
        URL("https://www.google.com/generate_204"),
        URL("https://www.cloudflare.com/cdn-cgi/trace")
    )

    /** HTTP(S) request duration including connection/TLS/response headers, not ICMP ping. */
    fun measure(network: Network? = null): Long? {
        for (url in urls) {
            val started = SystemClock.elapsedRealtimeNanos()
            val conn = try {
                (network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
            } catch (_: Exception) {
                continue
            }
            try {
                conn.connectTimeout = 2600
                conn.readTimeout = 2600
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                conn.setRequestProperty("User-Agent", "IraqGameRouteX1/0.1")
                conn.setRequestProperty("Connection", "close")
                conn.requestMethod = "GET"
                val status = conn.responseCode
                if (status in 200..299) {
                    return (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000L
                }
            } catch (_: Exception) {
                // Try the second independent endpoint. Never silently claim success.
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    fun sample(network: Network?, attempts: Int = 5): ProbeSummary {
        require(attempts in 1..10)
        return ProbeSummary.fromMeasurements(List(attempts) { measure(network) })
    }
}
