package com.soreng.tunnel.vpn

import android.util.Log
import kotlinx.coroutines.*
import com.soreng.tunnel.psiphon.PsiphonManager
import com.soreng.tunnel.tunnel.Tun2SocksManager
import com.soreng.tunnel.xray.XrayManager
import com.soreng.tunnel.storage.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors all tunnel daemons.
 *
 * Tick schedule:
 *   Every 10s: process.isAlive() check
 *   Every 30s: real SOCKS5+HTTP health check (HealthChecker)
 *   Every 60s: log status summary
 *
 * On failure: calls onFail(configId) → ReconnectManager.reconnect()
 * Uses isActive coroutine check — no infinite while(true) that leaks.
 */
@Singleton
class WatchdogSupervisor @Inject constructor(
    private val psiphon:   PsiphonManager,
    private val xray:      XrayManager,
    private val tun2socks: Tun2SocksManager,
    private val health:    HealthChecker,
    private val prefs:     AppPreferences
) {
    private val TAG = "WatchdogSupervisor"
    @Volatile private var job: Job? = null

    fun start(cfgId: Long, scope: CoroutineScope, onFail: suspend (Long) -> Unit) {
        stop()
        job = scope.launch {
            var tick = 0
            while (isActive) {
                delay(10_000)
                tick++
                if (!SorenVpnService.state.value.isActive) break

                // Process liveness check every 10s
                val alive = psiphon.isRunning() && xray.isRunning() && tun2socks.isRunning()
                if (!alive) {
                    Log.w(TAG, "Process down — p=${psiphon.isRunning()} x=${xray.isRunning()} t=${tun2socks.isRunning()}")
                    if (prefs.isAutoReconnectEnabled()) { onFail(cfgId); return@launch }
                    else {
                        SorenVpnService.state.value = VpnConnectionState.Error("Tunnel process died")
                        break
                    }
                }

                // Full health check every 30s
                if (tick % 3 == 0) {
                    val h = health.checkAll()
                    if (!h.allHealthy) {
                        Log.w(TAG, "Health check failed: $h")
                        if (prefs.isAutoReconnectEnabled()) { onFail(cfgId); return@launch }
                        else {
                            SorenVpnService.state.value = VpnConnectionState.Error("Health check failed")
                            break
                        }
                    }
                }

                // Status summary every 60s
                if (tick % 6 == 0) {
                    Log.d(TAG, "Status OK: tick=$tick p=${psiphon.isRunning()} x=${xray.isRunning()} t=${tun2socks.isRunning()}")
                }
            }
        }
        Log.i(TAG, "Watchdog started for cfgId=$cfgId")
    }

    fun stop() {
        job?.cancel(); job = null
        Log.d(TAG, "Watchdog stopped")
    }
}
