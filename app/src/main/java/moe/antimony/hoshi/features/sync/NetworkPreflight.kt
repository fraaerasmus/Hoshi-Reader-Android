package moe.antimony.hoshi.features.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class NetworkUnavailableException(val reason: Reason) : IOException(reason.name) {
    enum class Reason { Offline, VpnRequired }
}

/** Tailscale addresses (CGNAT 100.64.0.0/10 or MagicDNS) are only reachable while the VPN is up. */
internal fun isTailscaleHost(host: String?): Boolean {
    if (host.isNullOrBlank()) return false
    if (host.endsWith(".ts.net", ignoreCase = true)) return true
    val octets = host.split('.').takeIf { it.size == 4 }?.map { it.toIntOrNull() ?: return false } ?: return false
    return octets[0] == 100 && octets[1] in 64..127
}

internal fun networkPreflightFailure(
    hasActiveNetwork: Boolean,
    hasInternetCapability: Boolean,
    hasValidatedCapability: Boolean,
    requiresVpn: Boolean,
    hasVpnTransport: Boolean,
): NetworkUnavailableException.Reason? =
    when {
        !shouldAttemptDriveRequest(hasActiveNetwork, hasInternetCapability, hasValidatedCapability) ->
            NetworkUnavailableException.Reason.Offline
        requiresVpn && !hasVpnTransport -> NetworkUnavailableException.Reason.VpnRequired
        else -> null
    }

/** Shared "is it worth opening a socket" check for every sync backend. */
@Singleton
class NetworkPreflight @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val connectivityManager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun check(host: String? = null) {
        val network = connectivityManager?.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        val reason = networkPreflightFailure(
            hasActiveNetwork = capabilities != null,
            hasInternetCapability = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            hasValidatedCapability = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            requiresVpn = isTailscaleHost(host),
            hasVpnTransport = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
        )
        if (reason != null) throw NetworkUnavailableException(reason)
    }
}
