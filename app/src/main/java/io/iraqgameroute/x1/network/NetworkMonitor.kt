package io.iraqgameroute.x1.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    data class Snapshot(val transport: String, val validated: Boolean, val vpnActive: Boolean)

    fun snapshot(): Snapshot {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps == null) return Snapshot("غير متصل", false, false)
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "بيانات الهاتف"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "شبكة أخرى"
        }
        return Snapshot(
            type,
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        )
    }

    /** Explicitly pick an underlying validated Wi-Fi or cellular network for DIRECT baseline. */
    fun underlyingNetwork(): Network? = cm.allNetworks.firstOrNull { network ->
        val caps = cm.getNetworkCapabilities(network) ?: return@firstOrNull false
        !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
