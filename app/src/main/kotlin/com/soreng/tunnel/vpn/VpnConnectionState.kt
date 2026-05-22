package com.soreng.tunnel.vpn

sealed class VpnConnectionState {
    object Disconnected  : VpnConnectionState()
    object Connecting    : VpnConnectionState()
    /** Set ONLY after ConnectivityVerifier confirms real end-to-end traffic. */
    data class Connected(val connectedAt: Long, val probeLatencyMs: Long = -1L) : VpnConnectionState()
    object Disconnecting : VpnConnectionState()
    data class Error(val message: String) : VpnConnectionState()

    val isActive: Boolean get() = this is Connected || this is Connecting
    val label: String get() = when (this) {
        is Disconnected  -> "DISCONNECTED"
        is Connecting    -> "CONNECTING..."
        is Connected     -> "CONNECTED"
        is Disconnecting -> "DISCONNECTING"
        is Error         -> "ERROR"
    }
}
