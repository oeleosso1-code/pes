package io.iraqgameroute.x1.analytics

/** Pure, testable safety policy. Does not inspect or modify game traffic. */
object HealthDecision {
    enum class Action { KEEP, VERIFY_AGAIN, WARN_MATCH_PROTECTED, STOP_TUNNEL }

    /** A single failed sample must never drop an established gaming session. */
    fun next(consecutiveFailures: Int, matchProtected: Boolean): Action {
        require(consecutiveFailures >= 0)
        return when {
            consecutiveFailures == 0 -> Action.KEEP
            matchProtected -> Action.WARN_MATCH_PROTECTED
            consecutiveFailures < 3 -> Action.VERIFY_AGAIN
            else -> Action.STOP_TUNNEL
        }
    }
}
