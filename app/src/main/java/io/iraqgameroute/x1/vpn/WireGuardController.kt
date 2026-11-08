package io.iraqgameroute.x1.vpn

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import io.iraqgameroute.x1.analytics.HealthDecision
import io.iraqgameroute.x1.analytics.AutoRouteSelector
import io.iraqgameroute.x1.analytics.RouteBenchmark
import io.iraqgameroute.x1.analytics.RouteDecision
import io.iraqgameroute.x1.analytics.TunnelHandshake
import io.iraqgameroute.x1.network.HttpsProbe
import io.iraqgameroute.x1.network.NetworkMonitor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Owns exactly one VPN engine, serializes transitions, and avoids silent match-time routing. */
class WireGuardController(private val app: Application) {
    enum class Status { OFF, CONNECTING, VERIFYING, OPTIMIZING, ON, DEGRADED, DISCONNECTING, FAILED }
    data class Update(val status: Status, val message: String)

    @Volatile var current = Update(Status.OFF, "الاتصال المباشر متاح")
        private set
    @Volatile var matchProtection: Boolean = true
        private set

    data class RouteProfile(val title: String, val config: Config)

    private val taskGeneration = AtomicLong(0L)
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "vpn-control") }
    private val watchdog = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "vpn-watchdog") }
    private val probe = HttpsProbe()
    private val monitor = NetworkMonitor(app)
    private var backend: GoBackend? = null
    private val tunnel = object : Tunnel {
        override fun getName(): String = "IGRX1"
        override fun onStateChange(newState: Tunnel.State) = Unit
    }
    private var consecutiveFailures = 0
    @Volatile private var observer: ((Update) -> Unit)? = null

    init {
        watchdog.scheduleWithFixedDelay({
            if (current.status in listOf(Status.ON, Status.DEGRADED)) {
                runCatching { worker.execute { checkTunnel() } }
            }
        }, 35, 35, TimeUnit.SECONDS)
    }

    fun observe(listener: ((Update) -> Unit)?) {
        observer = listener
        val snapshot = current
        if (listener != null) handler.post { if (observer === listener) listener(snapshot) }
    }

    /** Only the Activity that installed a callback may remove it. Rotation-safe. */
    fun unobserve(listener: ((Update) -> Unit)) {
        if (observer === listener) observer = null
    }

    fun setMatchProtection(protected: Boolean) { matchProtection = protected }
    fun isConnected(): Boolean = current.status in listOf(Status.ON, Status.DEGRADED)

    private fun update(status: Status, message: String) {
        val snapshot = Update(status, message)
        current = snapshot
        handler.post { observer?.invoke(snapshot) }
    }

    fun connect(config: Config) {
        val operation = taskGeneration.incrementAndGet()
        worker.execute {
            if (taskGeneration.get() != operation) return@execute
            if (current.status !in listOf(Status.OFF, Status.FAILED)) return@execute
            if (monitor.underlyingNetwork() == null) {
                update(Status.FAILED, "لا توجد شبكة مباشرة متحقّق منها؛ لم يُشغّل VPN")
                return@execute
            }
            // FAILED may mean shutdown could not be confirmed. Never layer a
            // second activation over a possibly still running tunnel.
            val existingUp = try {
                backend?.getState(tunnel) == Tunnel.State.UP
            } catch (_: Exception) { true } catch (_: LinkageError) { true }
            if (existingUp) {
                update(Status.FAILED, "حالة VPN السابق غير مؤكدة؛ أوقفه من إعدادات أندرويد أولاً")
                return@execute
            }
            update(Status.CONNECTING, "جارٍ إنشاء النفق المشفّر…")
            try {
                val engine = backend ?: GoBackend(app).also { backend = it }
                engine.setState(tunnel, Tunnel.State.UP, config)
                update(Status.VERIFYING, "جارٍ التحقق من الإنترنت ومصافحة الخادم…")
                var healthy = false
                for (attempt in 0 until 4) {
                    val response = probe.measure()
                    val handshakeFresh = freshHandshake(engine)
                    if (response != null && handshakeFresh && engine.getState(tunnel) == Tunnel.State.UP) {
                        healthy = true
                        break
                    }
                    if (attempt < 3) Thread.sleep(2000)
                }
                if (!healthy) {
                    val stopped = safeStop()
                    update(Status.FAILED, if (stopped)
                        "تعذّر إثبات صلاحية النفق؛ تم إيقافه. الاتصال المباشر يعتمد على إعدادات النظام"
                    else "تعذّر التحقق من اتصال VPN أو إيقافه؛ افتح إعدادات VPN في أندرويد")
                } else {
                    consecutiveFailures = 0
                    update(Status.ON, "تحققنا من النفق عبر HTTPS ومصافحة WireGuard")
                }
            } catch (e: Exception) {
                val stopped = safeStop()
                update(Status.FAILED, "فشل الاتصال (${safeError(e)})؛ " +
                    (if (stopped) "تم إيقاف النفق" else "تعذّر تأكيد إيقاف النفق، افتح إعدادات VPN"))
            } catch (e: LinkageError) {
                val stopped = safeStop()
                update(Status.FAILED, "مكوّن VPN غير متوافق؛ " +
                    (if (stopped) "تم إيقاف النفق" else "تعذّر تأكيد إيقاف النفق"))
            }
        }
    }

    /**
     * One explicit PRE-MATCH action, after Android VPN permission was granted.
     * Evaluates at most three opted-in, imported profiles, alongside the SAME
     * validated physical network. On insufficient evidence, exits to DIRECT.
     * No remote configs, unapproved relays, silent mid-match rotation, or claims
     * about Konami RTT. Candidate configurations remain in volatile RAM only.
     */
    fun optimizeBeforeMatch(profiles: List<RouteProfile>) {
        if (profiles.isEmpty()) {
            update(Status.FAILED, "لا توجد ملفات خادم موثوقة لاختبارها")
            return
        }
        val operation = taskGeneration.incrementAndGet()
        val candidates = profiles.take(3).toList()
        worker.execute {
            if (taskGeneration.get() != operation) return@execute
            if (current.status !in listOf(Status.OFF, Status.FAILED)) return@execute
            val underlying = monitor.underlyingNetwork()
            if (underlying == null || monitor.snapshot().vpnActive) {
                update(Status.FAILED, "لا تتوفر شبكة مباشرة مستقرة أو يوجد VPN آخر على الجهاز")
                return@execute
            }
            val existingUp = try {
                backend?.getState(tunnel) == Tunnel.State.UP
            } catch (_: Exception) { true } catch (_: LinkageError) { true }
            if (existingUp) {
                update(Status.FAILED, "VPN سابق غير مؤكد الإيقاف؛ افتح إعدادات النظام")
                return@execute
            }
            val sameNetwork = { taskGeneration.get() == operation &&
                monitor.underlyingNetwork() == underlying }
            val measurements = mutableListOf<AutoRouteSelector.Observation>()
            try {
                val engine = backend ?: GoBackend(app).also { backend = it }
                for ((index, profile) in candidates.withIndex()) {
                    if (!sameNetwork()) break
                    update(Status.OPTIMIZING,
                        "فحص ${index + 1}/${candidates.size}: ${profile.title} — لا تفتح مباراة الآن")
                    engine.setState(tunnel, Tunnel.State.UP, profile.config)
                    val ready = verifyTunnel(engine, 3, sameNetwork)
                    if (ready && sameNetwork()) {
                        val viable = {
                            sameNetwork() && engine.getState(tunnel) == Tunnel.State.UP
                        }
                        val benchmark = RouteBenchmark(
                            directProbe = { probe.measure(underlying, 0) },
                            vpnProbe = { probe.measure(null, 0) },
                            isSameConnection = viable
                        ).run(rounds = 4)
                        if (benchmark != null && viable()) {
                            measurements += AutoRouteSelector.Observation(
                                index, benchmark.direct, benchmark.vpn)
                        }
                    }
                    // Close the trial tunnel regardless of whether it performed well.
                    if (!safeStop()) throw IllegalStateException("TUNNEL_STOP_UNCONFIRMED")
                }
                if (!sameNetwork()) {
                    val stopped = safeStop()
                    update(if (stopped) Status.OFF else Status.FAILED,
                        if (stopped) "أُلغيت المقارنة بسبب تغير الشبكة أو طلب الإيقاف؛ اتصال مباشر"
                        else "تعذر تأكيد إيقاف النفق؛ افتح إعدادات VPN")
                    return@execute
                }
                val selected = AutoRouteSelector.choose(measurements)
                if (selected == null) {
                    update(Status.OFF, "لا يوجد تحسين مقنع في HTTPS؛ أبقينا الاتصال المباشر")
                    return@execute
                }
                val winner = candidates[selected.profileIndex]
                update(Status.VERIFYING, "المسار المرشح: ${winner.title}؛ يجري تأكيد الاستقرار مجدداً…")
                engine.setState(tunnel, Tunnel.State.UP, winner.config)
                val recovered = verifyTunnel(engine, 3, sameNetwork)
                val finalCheck = if (recovered && sameNetwork()) RouteBenchmark(
                    directProbe = { probe.measure(underlying, 0) },
                    vpnProbe = { probe.measure(null, 0) },
                    isSameConnection = {
                        sameNetwork() && engine.getState(tunnel) == Tunnel.State.UP
                    }
                ).run(rounds = 4) else null
                if (finalCheck != null && sameNetwork() &&
                    RouteDecision.recommend(finalCheck.direct, finalCheck.vpn) ==
                    RouteDecision.Advice.RELAY_CANDIDATE) {
                    consecutiveFailures = 0
                    matchProtection = true // Never rotate or stop automatically during play.
                    update(Status.ON,
                        "المسار المختار: ${winner.title}. تحسن HTTPS تكرر؛ جودة بيس تحتاج تجربة فعلية")
                } else {
                    val stopped = safeStop()
                    update(if (stopped) Status.OFF else Status.FAILED,
                        if (stopped) "لم يتكرر التحسن بشكل مقنع؛ عاد الاتصال المباشر"
                        else "تعذر تأكيد إيقاف النفق؛ افتح إعدادات VPN")
                }
            } catch (_: Exception) {
                val stopped = safeStop()
                update(if (stopped) Status.OFF else Status.FAILED,
                    if (stopped) "فشل الاختبار الآمن أو تغير الاتصال؛ بقي المسار المباشر"
                    else "تعذر تأكيد إيقاف VPN؛ افتح إعدادات النظام")
            } catch (_: LinkageError) {
                val stopped = safeStop()
                update(if (stopped) Status.OFF else Status.FAILED,
                    if (stopped) "مكوّن النفق غير متوافق؛ أُوقف VPN"
                    else "حالة VPN غير مؤكدة؛ افتح إعدادات النظام")
            }
        }
    }

    /** Verify genuine HTTPS connectivity and WireGuard handshake, not just a VPN icon. */
    private fun verifyTunnel(engine: GoBackend, attempts: Int, keepGoing: () -> Boolean): Boolean {
        repeat(attempts) { attempt ->
            if (!keepGoing()) return false
            try {
                if (engine.getState(tunnel) == Tunnel.State.UP &&
                    probe.measure() != null && freshHandshake(engine)) return true
            } catch (_: Exception) { /* Try again; never expose private config in errors. */ }
            if (attempt < attempts - 1) Thread.sleep(800)
        }
        return false
    }

    fun disconnect() {
        // Cancels a long pre-match trial promptly, rather than waiting for all candidates.
        taskGeneration.incrementAndGet()
        worker.execute {
            if (current.status == Status.OFF) return@execute
            update(Status.DISCONNECTING, "جارٍ إيقاف VPN…")
            val stopped = safeStop()
            update(if (stopped) Status.OFF else Status.FAILED,
                if (stopped) "تم إيقاف VPN؛ المسار المباشر يخضع لإعدادات أندرويد"
                else "تعذّر تأكيد إيقاف VPN؛ افتح إعدادات النظام للتحقق")
        }
    }

    /** A successful API call alone is not enough: check the actual backend state. */
    private fun safeStop(): Boolean {
        val success = try {
            val engine = backend ?: return true
            engine.setState(tunnel, Tunnel.State.DOWN, null)
            engine.getState(tunnel) == Tunnel.State.DOWN
        } catch (_: Exception) { false } catch (_: LinkageError) { false }
        consecutiveFailures = 0
        return success
    }

    private fun freshHandshake(engine: GoBackend): Boolean {
        val stats = engine.getStatistics(tunnel)
        val now = System.currentTimeMillis()
        return stats.peers().any { key ->
            TunnelHandshake.isFresh(stats.peer(key)?.latestHandshakeEpochMillis() ?: 0L, now)
        }
    }

    private fun checkTunnel() {
        if (!isConnected()) return
        val engine = backend ?: return
        val healthy = try {
            engine.getState(tunnel) == Tunnel.State.UP &&
                probe.measure() != null && freshHandshake(engine)
        } catch (_: Exception) { false }
        if (healthy) {
            consecutiveFailures = 0
            if (current.status == Status.DEGRADED) update(Status.ON, "استعاد فحص النفق الاستجابة")
            return
        }
        consecutiveFailures++
        when (HealthDecision.next(consecutiveFailures, matchProtection)) {
            HealthDecision.Action.KEEP -> Unit
            HealthDecision.Action.VERIFY_AGAIN -> update(Status.DEGRADED,
                "فشل $consecutiveFailures من الفحوص المتتالية؛ سنعيد التحقق دون تغيير المسار")
            HealthDecision.Action.WARN_MATCH_PROTECTED -> update(Status.DEGRADED,
                "استمرار فشل اختبار HTTPS؛ حماية المباراة تمنع تبديل المسار تلقائياً")
            HealthDecision.Action.STOP_TUNNEL -> {
                update(Status.DISCONNECTING, "فشل فحص الاتصال ثلاث مرات؛ محاولة استعادة المسار المباشر…")
                val stopped = safeStop()
                update(Status.FAILED, if (stopped)
                    "أوقفنا VPN بعد تكرار فشل الاختبار؛ افحص الاتصال المباشر"
                    else "تعذّر تأكيد إيقاف VPN؛ افتح إعدادات النظام")
            }
        }
    }

    private fun safeError(error: Throwable): String = when (error) {
        is java.net.UnknownHostException -> "تعذّر حل عنوان الخادم"
        is java.net.SocketTimeoutException -> "انتهت مهلة الاتصال"
        is SecurityException -> "إذن VPN غير متاح"
        else -> error.javaClass.simpleName
    }
}
