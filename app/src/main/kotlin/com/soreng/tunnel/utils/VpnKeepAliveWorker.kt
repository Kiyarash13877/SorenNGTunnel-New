package com.soreng.tunnel.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import com.soreng.tunnel.vpn.SorenVpnService
import com.soreng.tunnel.vpn.VpnConnectionState
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based keepalive for aggressive OEM battery managers.
 *
 * MIUI/HyperOS/EMUI/ColorOS kill background processes within minutes.
 * This Worker runs every 15 minutes (minimum WorkManager interval) and
 * restarts the VPN if it was killed without user intent.
 *
 * Schedule via VpnKeepAliveWorker.schedule(context).
 */
class VpnKeepAliveWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    companion object {
        private const val TAG       = "VpnKeepAliveWorker"
        private const val WORK_NAME = "soren_vpn_keepalive"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<VpnKeepAliveWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
            Log.i(TAG, "Keepalive worker scheduled")
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Keepalive worker cancelled")
        }
    }

    override suspend fun doWork(): Result {
        val state = SorenVpnService.state.value
        Log.d(TAG, "Keepalive check: state=$state")

        // Only restart if was Connected but process is now dead (OEM killed it)
        if (state is VpnConnectionState.Disconnected) {
            // Check if there's a last config to reconnect with
            val prefs = applicationContext.getSharedPreferences(
                "soren_keepalive_prefs", Context.MODE_PRIVATE)
            val lastCfgId = prefs.getLong("last_cfg_id", -1L)
            if (lastCfgId >= 0) {
                Log.i(TAG, "Keepalive: restarting VPN for config $lastCfgId")
                applicationContext.startForegroundService(
                    Intent(applicationContext, SorenVpnService::class.java).apply {
                        action = SorenVpnService.ACTION_START
                        putExtra(SorenVpnService.EXTRA_CONFIG_ID, lastCfgId)
                    })
            }
        }
        return Result.success()
    }
}
