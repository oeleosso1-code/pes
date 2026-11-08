package io.iraqgameroute.x1.vpn

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import io.iraqgameroute.x1.analytics.HealthDecision
import io.iraqgameroute.x1.analytics.TunnelHandshake
import io.iraqgameroute.x1.network.HttpsProbe
import io.iraqgameroute.x1.network.NetworkMonitor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Owns exactly one VPN engine, serializes transitions, and avoids silent match-time routing. */
class WireGuardController(private val app: Application) {
    enum class Status { OFF, CONNECTING, VERIFYING, ON, DEGRADED, DISCONNECTING, FAILED }
    data class Update(val status: Status, val message: String)

    @Volatile var current = Update(Status.OFF, "الاتصال المباشر متاح")
        private set
    @Volatile var matchProtection: Boolean = true
        private set

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
        worker.execute {
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

    fun disconnect() {
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
