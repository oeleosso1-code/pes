package io.iraqgameroute.x1.vpn

import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.net.Inet4Address
import java.nio.charset.StandardCharsets

/** Safe per-app policy: never silently convert an imported profile into a device-wide VPN. */
object ConfigPolicy {
    const val EFOOTBALL_PACKAGE = "jp.konami.pesam"
    const val MAX_CONFIG_BYTES = 64 * 1024

    fun parseProfile(raw: ByteArray, ownPackage: String): Config {
        require(raw.isNotEmpty() && raw.size <= MAX_CONFIG_BYTES) { "Invalid configuration size" }
        require(ownPackage.matches(Regex("[a-zA-Z_][a-zA-Z0-9_.]*"))) { "Invalid application identifier" }
        val original = Config.parse(ByteArrayInputStream(raw))
        require(original.peers.isNotEmpty()) { "Missing WireGuard peer" }
        // Both conditions must hold on the same peer. A v6-only default is not a v4 game route.
        require(original.peers.any { peer ->
            peer.endpoint.isPresent && peer.allowedIps.any { route ->
                route.mask == 0 && route.address is Inet4Address
            }
        }) { "No IPv4 default route with an endpoint" }
        require(original.`interface`.includedApplications.isEmpty() &&
            original.`interface`.excludedApplications.isEmpty()) {
            "Imported configuration contains app routing rules"
        }
        val limited = original.toWgQuickString().replaceFirst(
            "[Interface]\n",
            "[Interface]\nIncludedApplications = $EFOOTBALL_PACKAGE, $ownPackage\n"
        )
        return Config.parse(ByteArrayInputStream(limited.toByteArray(StandardCharsets.UTF_8)))
    }
}
