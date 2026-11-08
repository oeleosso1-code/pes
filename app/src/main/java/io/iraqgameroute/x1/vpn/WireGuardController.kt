package io.iraqgameroute.x1.vpn

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import io.iraqgameroute.x1.network.HttpsProbe
import io.iraqgameroute.x1.network.NetworkMonitor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Owns exactly one WireGuard engine; avoids reconnect loops and route changes mid-game. */
class WireGuardController(private val app: Application) {
    enum class Status { OFF, CONNECTING, VERIFYING, ON, DISCONNECTING, FAILED }
    data class Update(val status: Status, val message: String)

    @Volatile var current = Update(Status.OFF, "الاتصال المباشر متاح")
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
    private var observer: ((Update) -> Unit)? = null

    init {
        watchdog.scheduleWithFixedDelay({
            // Do not race backend calls against connect / disconnect.
            if (current.status == Status.ON) worker.execute { checkTunnel() }
        }, 35, 35, TimeUnit.SECONDS)
    }

    fun observe(listener: ((Update) -> Unit)?) {
        observer = listener
        listener?.invoke(current)
    }

    private fun update(status: Status, message: String) {
        current = Update(status, message)
        handler.post { observer?.invoke(current) }
    }

    fun connect(config: Config) {
        worker.execute {
            if (current.status !in listOf(Status.OFF, Status.FAILED)) return@execute
            if (monitor.underlyingNetwork() == null) {
                update(Status.FAILED, "لا توجد شبكة مباشرة متحقَّق منها. لم يُشغّل VPN")
                return@execute
            }
            update(Status.CONNECTING, "جاري إنشاء نفق WireGuard…")
            try {
                val engine = backend ?: GoBackend(app).also { backend = it }
                engine.setState(tunnel, Tunnel.State.UP, config)
                update(Status.VERIFYING, "فحص الإنترنت والمصافحة داخل النفق…")
                // HTTPS from this app is routed through the VPN by ConfigPolicy.
                // Success + actual handshake are needed; an UP state alone is NOT enough.
                var healthy = false
                for (attempt in 0 until 3) {
                    val response = probe.measure()
                    val stats = engine.getStatistics(tunnel)
                    val handshakeSeen = stats.peers().any { key ->
                        (stats.peer(key)?.latestHandshakeEpochMillis() ?: 0L) > 0L
                    }
                    if (response != null && handshakeSeen) healthy = true
                    if (healthy) break
                    if (attempt < 2) Thread.sleep(1200)
                }
                if (!healthy) {
                    runCatching { engine.setState(tunnel, Tunnel.State.DOWN, null) }
                    update(Status.FAILED, "لم ينجح اتصال HTTPS عبر النفق؛ أوقفنا VPN لتجنّب حجب الإنترنت")
                } else {
                    consecutiveFailures = 0
                    update(Status.ON, "النفق يعمل، وتحقّقنا من الإنترنت والمصافحة")
                }
            } catch (e: Exception) {
                safeStop()
                update(Status.FAILED, "فشل الاتصال: ${safeError(e)}. أوقفنا النفق")
            } catch (e: LinkageError) {
                safeStop()
                update(Status.FAILED, "مكوّن WireGuard غير متوافق مع الجهاز: ${safeError(e)}")
            }
        }
    }

    fun disconnect() {
        worker.execute {
            if (current.status == Status.OFF) return@execute
            update(Status.DISCONNECTING, "جاري إيقاف النفق…")
            safeStop()
            update(Status.OFF, "تم إيقاف VPN؛ الاتصال المباشر يخضع لإعدادات أندرويد")
        }
    }

    private fun safeStop() {
        try { backend?.setState(tunnel, Tunnel.State.DOWN, null) } catch (_: Exception) { /* best effort */ }
        consecutiveFailures = 0
    }

    private fun checkTunnel() {
        if (current.status != Status.ON) return
        val engine = backend ?: return
        try {
            if (engine.getState(tunnel) != Tunnel.State.UP || probe.measure() == null) {
                consecutiveFailures++
            } else consecutiveFailures = 0
        } catch (_: Exception) { consecutiveFailures++ }
        if (consecutiveFailures >= 2) {
            update(Status.DISCONNECTING, "فشل فحص الاتصال مرتين؛ إيقاف VPN لتجنّب فقد الإنترنت…")
            safeStop()
            update(Status.FAILED, "توقف VPN بعد فشل التحقق. استخدم الاتصال المباشر إذا كان متاحاً")
        }
    }

    // No stack traces, endpoint details, or secrets in user-facing errors.
    private fun safeError(error: Throwable): String = when (error) {
        is java.net.UnknownHostException -> "تعذّر حل عنوان الخادم"
        is java.net.SocketTimeoutException -> "انتهت مهلة الاتصال"
        is SecurityException -> "صلاحية VPN غير متاحة"
        else -> error.javaClass.simpleName
    }
}
