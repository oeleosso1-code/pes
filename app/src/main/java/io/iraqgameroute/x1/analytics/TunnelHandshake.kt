package io.iraqgameroute.x1.analytics

/** WireGuard peer timestamps are epoch milliseconds. A historical handshake is not proof of
 * current reachability. A little tolerance allows for clock adjustments. */
object TunnelHandshake {
    const val MAX_AGE_MS: Long = 180_000L

    fun isFresh(lastHandshakeEpochMillis: Long, nowEpochMillis: Long): Boolean {
        if (lastHandshakeEpochMillis <= 0L || nowEpochMillis <= 0L) return false
        val age = nowEpochMillis - lastHandshakeEpochMillis
        return age in -5_000L..MAX_AGE_MS
    }
}
