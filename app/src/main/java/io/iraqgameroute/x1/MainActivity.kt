package io.iraqgameroute.x1

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.wireguard.config.Config
import io.iraqgameroute.x1.network.HttpsProbe
import io.iraqgameroute.x1.analytics.RouteComparison
import io.iraqgameroute.x1.analytics.RouteBenchmark
import io.iraqgameroute.x1.network.NetworkMonitor
import io.iraqgameroute.x1.vpn.ConfigPolicy
import io.iraqgameroute.x1.vpn.WireGuardController
import java.util.concurrent.Executors

/** Arabic-first native UI, no elevated privileges or modification of game processes. */
class MainActivity : ComponentActivity() {
    private val navy = Color.rgb(11, 20, 35)
    private val panel = Color.rgb(24, 40, 61)
    private val pale = Color.rgb(193, 209, 225)
    private val green = Color.rgb(58, 221, 175)
    private val warning = Color.rgb(255, 192, 99)
    private val io = Executors.newSingleThreadExecutor { job -> Thread(job, "diagnostics") }
    private val monitor by lazy { NetworkMonitor(this) }
    private val vpn by lazy { (application as IraqRouteApp).vpn }
    private val profiles get() = (application as IraqRouteApp).profiles
    private val vpnListener: (WireGuardController.Update) -> Unit = { change -> showState(change) }
    private var pendingConfig: Config?
        get() = (application as IraqRouteApp).pendingVpnProfile
        set(value) { (application as IraqRouteApp).pendingVpnProfile = value }
    private lateinit var root: LinearLayout
    private lateinit var networkText: TextView
    private lateinit var vpnText: TextView
    private lateinit var metricsText: TextView
    private lateinit var profileSpinner: Spinner
    private lateinit var checkButton: Button
    private lateinit var compareButton: Button
    private lateinit var guardButton: Button
    private lateinit var autoButton: Button
    private lateinit var comparisonText: TextView
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val cm by lazy { getSystemService(ConnectivityManager::class.java) }

    private val importFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(4096)
                while (output.size() <= 65536) {
                    val count = stream.read(chunk)
                    if (count == -1) break
                    output.write(chunk, 0, count)
                }
                output.toByteArray()
            } ?: throw IllegalArgumentException("تعذرت قراءة الملف")
            if (bytes.size > 65536) throw IllegalArgumentException("حجم الملف أكبر من 64KB")
            val config = ConfigPolicy.parseProfile(bytes, packageName)
            val filename = "ملف ${profiles.size + 1}"
            profiles.add(IraqRouteApp.NamedProfile(filename, config))
            refreshProfiles()
            showVpnText("استُورد $filename في ذاكرة التطبيق فقط، ولم يُفعّل VPN", green)
        } catch (_: Exception) {
            // Never render a parser exception: offending tokens can include a private key.
            showVpnText("تعذّر استيراد الملف. تحقّق من صحة WireGuard ووجود خادم، واحذف قوائم Included/ExcludedApplications.", warning)
        }
    }

    private val requestVpn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val chosen = pendingConfig
        val autoProfiles = (application as IraqRouteApp).pendingAutoProfiles
        pendingConfig = null
        (application as IraqRouteApp).pendingAutoProfiles = null
        if (result.resultCode == Activity.RESULT_OK && autoProfiles != null)
            vpn.optimizeBeforeMatch(autoProfiles)
        else if (result.resultCode == Activity.RESULT_OK && chosen != null)
            vpn.connect(chosen)
        else showVpnText("لم يُمنح إذن VPN، وظلّ الاتصال المباشر بدون تغيير", warning)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        buildScreen()
        refreshNetwork()
        vpn.observe(vpnListener)
    }

    override fun onResume() {
        super.onResume()
        refreshNetwork()
        updateGuardButton()
        if (networkCallback == null) {
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { runOnUiThread { refreshNetwork() } }
                override fun onLost(network: Network) { runOnUiThread { refreshNetwork() } }
                override fun onCapabilitiesChanged(network: Network, capabilities: android.net.NetworkCapabilities) {
                    runOnUiThread { refreshNetwork() }
                }
            }
            try { cm.registerDefaultNetworkCallback(cb); networkCallback = cb }
            catch (_: Exception) { /* Still refresh when screen resumes */ }
        }
    }

    override fun onPause() {
        networkCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        networkCallback = null
        super.onPause()
    }

    override fun onDestroy() {
        vpn.unobserve(vpnListener)
        io.shutdownNow()
        super.onDestroy()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(navy)
            isFillViewport = true
            clipToPadding = false
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(24), dp(18), dp(36))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(text("IRAQ GAME ROUTE  •  X1", 14, green, true))
        root.addView(text("مركز استقرار الإنترنت", 26, Color.WHITE, true).margin(bottom = 6))
        root.addView(text("eFootball Mobile — وضع آمن يعتمد على القياسات، بدون Root.", 14, pale).margin(bottom = 22))

        root.card {
            addView(text("🌐  الشبكة الحالية", 17, Color.WHITE, true).margin(bottom = 10))
            networkText = text("جارٍ قراءة حالة الشبكة…", 15, pale)
            addView(networkText)
        }
        root.card {
            addView(text("🛡  حالة النفق", 17, Color.WHITE, true).margin(bottom = 10))
            vpnText = text("غير مفعّل — اتصال مباشر", 15, pale)
            addView(vpnText)
        }
        root.card {
            addView(text("📊  اختبار الإنترنت المباشر", 17, Color.WHITE, true).margin(bottom = 10))
            metricsText = text("لا توجد قياسات بعد. الاختبار HTTPS وليس Ping خوادم كونامي.", 14, pale)
            addView(metricsText.margin(bottom = 14))
            checkButton = button("فحص الاتصال الآن", green, navy) { runDiagnostics() }
            addView(checkButton)
        }
        root.card {
            addView(text("⚖️  مقارنة المسار المباشر وVPN", 17, Color.WHITE, true).margin(bottom = 8))
            addView(text("نقيس 8 معاملات HTTPS لكل مسار بترتيب متناوب ABBA/BAAB؛ نوقف المقارنة إذا تغيرت الشبكة أو توقف النفق. هذه ليست Ping كونامي.", 14, pale).margin(bottom = 10))
            comparisonText = text("لم تُجرَ مقارنة بعد.", 14, pale)
            addView(comparisonText.margin(bottom = 10))
            compareButton = button("مقارنة المسارين", green, navy) { compareRoutes() }
            addView(compareButton)
        }
        root.card {
            addView(text("⚙️  إعداد خادم WireGuard", 17, Color.WHITE, true).margin(bottom = 8))
            addView(text("استورد ملف .conf من خادم موثوق. لا يحتوي التطبيق على خادم سحري مجاني.", 14, pale).margin(bottom = 12))
            profileSpinner = Spinner(this@MainActivity)
            refreshProfiles()
            addView(profileSpinner)
            addView(button("استيراد ملف خادم جديد", green, navy) { importFile.launch("*/*") }.margin(top = 10))
            addView(button("تشغيل المسار المشفّر", Color.rgb(57, 104, 139), Color.WHITE) { askToConnect() }.margin(top = 10))
            addView(text("⚡ التحسين التلقائي قبل المباراة يفحص حتى 3 خوادم مستوردة. يختار المسار المباشر إذا لم يثبت أن النفق أفضل مرتين. لا تبدأ المباراة حتى انتهاء الاختبارات، ولن يغيّر المسار أثناء اللعب.", 13, pale).margin(top = 14, bottom = 6))
            autoButton = button("⚡ اختيار أفضل مسار تلقائياً قبل المباراة", green, navy) { askToOptimize() }
            addView(autoButton)
            addView(button("إيقاف VPN والعودة للمسار المباشر", Color.rgb(119, 67, 68), Color.WHITE) { vpn.disconnect() }.margin(top = 10))
        }
        root.card {
            addView(text("🎮  حماية المباراة من تبديل المسار", 17, Color.WHITE, true).margin(bottom = 8))
            addView(text("فعّل هذا الوضع قبل بدء المباراة: يتوقف الإيقاف التلقائي عند فشل الاختبارات أثناء اللعب، لكن لا يمنع عطل شبكة المزود أو الخادم.", 14, pale).margin(bottom = 8))
            guardButton = button("", Color.rgb(57, 104, 139), Color.WHITE) {
                vpn.setMatchProtection(!vpn.matchProtection)
                updateGuardButton()
            }
            addView(guardButton)
            updateGuardButton()
        }
        root.card {
            addView(text("🚦  حماية من حالة عدم الاتصال", 16, Color.WHITE, true).margin(bottom = 10))
            addView(text("خارج وضع حماية المباراة، عند فشل ثلاثة اختبارات متتالية، يحاول التطبيق إيقافه. إذا فعّلت «حظر الاتصالات بدون VPN» في إعدادات أندرويد فقد يبقى الإنترنت محجوباً. افتح الإعدادات للتحقق.", 14, pale).margin(bottom = 12))
            addView(button("فتح إعدادات VPN في أندرويد", panel, Color.WHITE) {
                startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
            })
        }
        root.addView(text("تنبيه: قياسات التطبيق ليست زمن استجابة خوادم eFootball، والتحسين غير مضمون. لا تجمع النسخة الأولى بيانات شخصية ولا تخزن مفاتيح VPN في ملفات.", 12, pale).margin(top = 10))
    }

    private fun refreshProfiles() {
        if (!::profileSpinner.isInitialized) return
        val names = profiles.map { it.title } .ifEmpty { listOf("لا توجد ملفات مستوردة") }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        profileSpinner.adapter = adapter
        if (profiles.isNotEmpty()) profileSpinner.setSelection(profiles.size - 1)
    }

    private fun refreshNetwork() {
        if (!::networkText.isInitialized) return
        val snap = monitor.snapshot()
        networkText.text = "النوع: ${snap.transport}\nتحقق أندرويد من الإنترنت: ${if (snap.validated) "نعم" else "لا"}\nVPN نشط على الجهاز: ${if (snap.vpnActive) "نعم" else "لا"}"
        networkText.setTextColor(if (snap.validated) green else warning)
    }

    private fun runDiagnostics() {
        if (checkButton.isEnabled.not()) return
        checkButton.isEnabled = false
        metricsText.text = "يجري فحص 5 طلبات HTTPS عبر اتصال Wi-Fi أو البيانات الأساسي…"
        io.execute {
            val underlying = monitor.underlyingNetwork()
            val result = if (underlying != null) HttpsProbe().sample(underlying, 5) else null
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                checkButton.isEnabled = true
                metricsText.text = if (result == null) "لا توجد شبكة مباشرة متحقّق منها، لذلك لم يبدأ الفحص." else {
                    "نجاح الطلبات: ${result.successful}/${result.attempted}\n" +
                        "وسيط زمن HTTPS: ${result.medianMs?.let { "$it ms" } ?: "—"}\n" +
                        "P95: ${result.p95Ms?.let { "$it ms" } ?: "—"}\n" +
                        "متوسط تغير الزمن: ${result.jitterMs?.let { "$it ms" } ?: "—"}\n" +
                        "فشل طلبات HTTPS: ${"%.0f".format(result.httpFailurePercent)}%\n" +
                        "هذه ليست قياسات UDP أو اتصال كونامي."
                }
            }
        }
    }

    private fun compareRoutes() {
        if (!vpn.isConnected()) {
            comparisonText.text = "فعّل VPN صالحاً وتأكد من نجاح الاتصال قبل المقارنة."
            return
        }
        compareButton.isEnabled = false
        comparisonText.text = "جارٍ قياس HTTPS على المسارين، دون أي تغيير في الاتصال…"
        io.execute {
            val underlying = monitor.underlyingNetwork()
            val comparison = if (underlying != null && vpn.isConnected()) {
                val tester = HttpsProbe()
                // Keep the exact same physical network and tunnel throughout.
                // A comparison crossing a Wi-Fi/cellular switch is invalid.
                val consistent = {
                    !Thread.currentThread().isInterrupted && vpn.isConnected() &&
                        monitor.underlyingNetwork() == underlying
                }
                fun compareEndpoint(endpoint: Int): RouteBenchmark.Result? = RouteBenchmark(
                    directProbe = { tester.measure(underlying, endpoint) },
                    vpnProbe = { tester.measure(null, endpoint) },
                    isSameConnection = consistent
                ).run(rounds = 4)
                val first = compareEndpoint(0)
                // Fallback if either path cannot reach the primary endpoint and
                // the actual connection is still unchanged.
                val chosen = if (first != null &&
                    (first.direct.successful >= 7 && first.vpn.successful >= 7)) first
                else if (consistent()) compareEndpoint(1) else null
                if (chosen != null && consistent())
                    RouteComparison(chosen.direct, chosen.vpn) else null
            } else null
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                compareButton.isEnabled = true
                comparisonText.text = comparison?.arabicReport()
                    ?: "أُلغيت المقارنة أو تعذّر القياس: ربما تغيرت شبكة Wi-Fi/البيانات أو توقف النفق."
            }
        }
    }

    private fun updateGuardButton() {
        if (!::guardButton.isInitialized) return
        guardButton.text = if (vpn.matchProtection)
            "حماية المباراة: مفعّلة ✅ (اضغط لإيقافها)"
        else "حماية المباراة: غير مفعّلة (اضغط لتفعيلها)"
    }

    /** Explicit user action: this can change VPN route, so only run BEFORE a match. */
    private fun askToOptimize() {
        if (profiles.isEmpty()) {
            showVpnText("استورد إعداد خادم WireGuard موثوقاً أولاً. لا يمكن تحسين المسار بلا خوادم", warning)
            return
        }
        try {
            packageManager.getPackageInfo(ConfigPolicy.EFOOTBALL_PACKAGE, 0)
        } catch (_: Exception) {
            showVpnText("يجب تثبيت تطبيق eFootball الرسمي أولاً", warning)
            return
        }
        if (vpn.current.status !in listOf(WireGuardController.Status.OFF, WireGuardController.Status.FAILED)) {
            showVpnText("يجب إيقاف النفق الحالي قبل البحث التلقائي", warning)
            return
        }
        if (monitor.snapshot().vpnActive) {
            showVpnText("يوجد VPN نشط على الجهاز، أوقفه من إعدادات أندرويد أولاً", warning)
            return
        }
        val choices = profiles.take(3).map { WireGuardController.RouteProfile(it.title, it.config) }
        (application as IraqRouteApp).pendingAutoProfiles = choices
        try {
            val consent = VpnService.prepare(this)
            if (consent != null) requestVpn.launch(consent)
            else {
                (application as IraqRouteApp).pendingAutoProfiles = null
                vpn.optimizeBeforeMatch(choices)
            }
        } catch (_: SecurityException) {
            (application as IraqRouteApp).pendingAutoProfiles = null
            showVpnText("تعذّر طلب صلاحية VPN. تحقق من إعدادات النظام", warning)
        }
    }

    private fun askToConnect() {
        if (profiles.isEmpty()) {
            showVpnText("يجب استيراد ملف خادم WireGuard صالح أولاً", warning)
            return
        }
        try {
            packageManager.getPackageInfo(ConfigPolicy.EFOOTBALL_PACKAGE, 0)
        } catch (_: Exception) {
            showVpnText("تطبيق eFootball الرسمي غير موجود؛ لم يُنشأ VPN", warning)
            return
        }
        if (vpn.current.status !in listOf(WireGuardController.Status.OFF, WireGuardController.Status.FAILED)) {
            showVpnText("أوقف النفق الحالي أولاً", warning)
            return
        }
        val selectedConfig = profiles.getOrNull(profileSpinner.selectedItemPosition)?.config
        if (selectedConfig == null) {
            showVpnText("يجب اختيار ملف خادم أولاً", warning)
            return
        }
        pendingConfig = selectedConfig
        if (monitor.snapshot().vpnActive && !vpn.isConnected()) {
            pendingConfig = null
            showVpnText("يبدو أن VPN آخر يعمل على الهاتف. أوقفه من إعدادات النظام أولاً.", warning)
            return
        }
        try {
            val consent = VpnService.prepare(this)
            if (consent != null) requestVpn.launch(consent) else {
                pendingConfig = null
                vpn.connect(selectedConfig)
            }
        } catch (_: SecurityException) {
            pendingConfig = null
            showVpnText("لم يتمكن التطبيق من طلب إذن VPN. تحقق من قيود أندرويد.", warning)
        }
    }

    private fun showState(status: WireGuardController.Update) {
        if (isFinishing || isDestroyed) return
        val tint = if (status.status == WireGuardController.Status.ON) green else if (
            status.status == WireGuardController.Status.FAILED) warning else pale
        showVpnText("${status.status}: ${status.message}", tint)
        if (::autoButton.isInitialized) autoButton.isEnabled =
            status.status in listOf(WireGuardController.Status.OFF, WireGuardController.Status.FAILED)
        refreshNetwork()
    }

    private fun showVpnText(value: String, color: Int) {
        if (!::vpnText.isInitialized) return
        vpnText.text = value
        vpnText.setTextColor(color)
    }

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value + .5f).toInt()

    private fun text(s: String, size: Int, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            textSize = size.toFloat()
            setTextColor(color)
            gravity = Gravity.START
            setLineSpacing(dp(3).toFloat(), 1f)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun button(s: String, bg: Int, fg: Int, tap: () -> Unit): Button = Button(this).apply {
        text = s
        textSize = 14f
        isAllCaps = false
        setTextColor(fg)
        minHeight = dp(52)
        background = rounded(bg, 12)
        setOnClickListener { tap() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        setColor(color)
    }

    private fun LinearLayout.card(content: LinearLayout.() -> Unit) {
        val card = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(panel, 18)
            content()
        }
        addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(13) })
    }

    private fun <T: View> T.margin(top: Int = 0, bottom: Int = 0): T = apply {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }
    }
}
