package io.iraqgameroute.x1

import android.app.Application
import io.iraqgameroute.x1.vpn.WireGuardController

class IraqRouteApp : Application() {
    val vpn by lazy { WireGuardController(this) }
}
