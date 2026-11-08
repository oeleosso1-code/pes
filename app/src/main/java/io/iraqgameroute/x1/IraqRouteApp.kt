package io.iraqgameroute.x1

import android.app.Application
import com.wireguard.config.Config
import io.iraqgameroute.x1.vpn.WireGuardController

/** Process-only profile store: survives rotation, deliberately never persists private keys. */
class IraqRouteApp : Application() {
    data class NamedProfile(val title: String, val config: Config)
    val profiles = mutableListOf<NamedProfile>()
    var pendingVpnProfile: Config? = null
    var pendingAutoProfiles: List<WireGuardController.RouteProfile>? = null
    val vpn by lazy { WireGuardController(this) }
}
