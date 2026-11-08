package io.iraqgameroute.x1.analytics

import io.iraqgameroute.x1.network.ProbeSummary

/** Diagnostic-only HTTPS route comparison; never claims to measure eFootball RTT. */
data class RouteComparison(val direct: ProbeSummary, val relay: ProbeSummary) {
    val advice: RouteDecision.Advice = RouteDecision.recommend(direct, relay)

    fun arabicReport(): String = buildString {
        append("مقارنة عينات HTTPS (ليست Ping كونامي)\n")
        append("مباشر: ${fmt(direct)}\n")
        append("عبر VPN: ${fmt(relay)}\n")
        append("التوصية التجريبية: ")
        append(when (advice) {
            RouteDecision.Advice.RELAY_CANDIDATE -> "النفق مرشح للاختبار أثناء اللعب، وليس مضموناً"
            RouteDecision.Advice.DIRECT -> "المسار المباشر أفضل أو لم يُثبت تحسن النفق"
            RouteDecision.Advice.INSUFFICIENT_DATA -> "عينات غير كافية للمقارنة"
        })
    }

    private fun fmt(summary: ProbeSummary): String =
        "${summary.successful}/${summary.attempted} نجاح، وسيط ${summary.medianMs?.toString() ?: "—"} ms، " +
        "P95 ${summary.p95Ms?.toString() ?: "—"} ms، " +
        "تذبذب HTTPS ${summary.jitterMs?.toString() ?: "—"} ms، " +
        "فشل ${"%.0f".format(java.util.Locale.US, summary.httpFailurePercent)}%"
}
