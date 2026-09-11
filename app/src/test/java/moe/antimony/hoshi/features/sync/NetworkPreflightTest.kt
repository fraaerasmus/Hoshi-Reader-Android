package moe.antimony.hoshi.features.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPreflightTest {
    @Test
    fun tailscaleHostsAreCgnatAddressesOrMagicDnsNames() {
        assertTrue(isTailscaleHost("100.98.70.32"))
        assertTrue(isTailscaleHost("100.127.255.1"))
        assertTrue(isTailscaleHost("kobo.tailnet-name.ts.net"))
        assertFalse(isTailscaleHost("100.63.0.1"))
        assertFalse(isTailscaleHost("100.128.0.1"))
        assertFalse(isTailscaleHost("192.168.1.10"))
        assertFalse(isTailscaleHost("www.googleapis.com"))
        assertFalse(isTailscaleHost(null))
    }

    @Test
    fun offlineWinsOverVpnAndVpnIsOnlyRequiredForTailscaleHosts() {
        assertEquals(
            NetworkUnavailableException.Reason.Offline,
            networkPreflightFailure(hasActiveNetwork = false, hasInternetCapability = false, hasValidatedCapability = false, requiresVpn = true, hasVpnTransport = false),
        )
        assertEquals(
            NetworkUnavailableException.Reason.VpnRequired,
            networkPreflightFailure(hasActiveNetwork = true, hasInternetCapability = true, hasValidatedCapability = true, requiresVpn = true, hasVpnTransport = false),
        )
        assertNull(networkPreflightFailure(hasActiveNetwork = true, hasInternetCapability = true, hasValidatedCapability = false, requiresVpn = true, hasVpnTransport = true))
        assertNull(networkPreflightFailure(hasActiveNetwork = true, hasInternetCapability = true, hasValidatedCapability = false, requiresVpn = false, hasVpnTransport = false))
    }
}
