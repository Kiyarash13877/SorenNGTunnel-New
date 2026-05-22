package com.soreng.tunnel.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helps users exempt the app from battery optimization.
 * Required to survive background kills on MIUI/HyperOS/EMUI/ColorOS.
 */
@Singleton
class BatteryHelper @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val TAG = "BatteryHelper"

    fun isExempt(): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun getExemptIntent(): Intent? {
        if (isExempt()) return null
        return try {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${ctx.packageName}"))
        } catch (e: Exception) {
            Log.w(TAG,"exemptIntent: ${e.message}")
            try { Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
            catch (_: Exception) { null }
        }
    }

    fun logState() {
        if (!isExempt()) Log.w(TAG,"NOT exempt from battery optimization — VPN may be killed by OEM")
        else Log.i(TAG,"Battery optimization exempt")
    }
}
