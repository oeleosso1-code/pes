package io.iraqgameroute.x1.vpn

import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/** Adds an explicit per-app allowlist; never allows a config to change the whole phone's route. */
object ConfigPolicy {
    const val EFOOTBALL_PACKAGE = "jp.konami.pesam"

    fun parseProfile(raw: ByteArray, ownPackage: String): Config {
        require(raw.isNotEmpty() && raw.size <= 64 * 1024) { "ملف الإعدادات فارغ أو كبير جداً" }
        val original = Config.parse(ByteArrayInputStream(raw))
        require(original.peers.isNotEmpty()) { "ملف WireGuard لا يحتوي على خادم Peer" }
        require(original.peers.any { p -> p.allowedIps.any { it.mask == 0 } }) {
            "يجب أن يتضمن AllowedIPs مساراً عاماً مثل 0.0.0.0/0"
        }
        require(original.peers.any { it.endpoint.isPresent }) { "إعداد الخادم Endpoint مفقود" }
        require(original.`interface`.includedApplications.isEmpty() &&
                original.`interface`.excludedApplications.isEmpty()) {
            "أزل IncludedApplications وExcludedApplications من الملف: التطبيق يضبطهما بأمان"
        }
        // Build an immutable WireGuard config from canonical, validated content.
        // The diagnostic app is also included so its HTTPS probe actually checks the VPN path.
        val canonical = original.toWgQuickString()
        val limited = canonical.replaceFirst(
            "[Interface]\n",
            "[Interface]\nIncludedApplications = $EFOOTBALL_PACKAGE, $ownPackage\n"
        )
        return Config.parse(ByteArrayInputStream(limited.toByteArray(StandardCharsets.UTF_8)))
    }
}
