package com.soreng.tunnel.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.soreng.tunnel.storage.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var prefs: AppPreferences

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action !in listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.LOCKED_BOOT_COMPLETED")) return
        val autoStart = runBlocking { prefs.isAutoStartEnabled() }
        if (!autoStart) return
        val cfgId = runBlocking { prefs.getLastConfigId() }
        if (cfgId < 0) return
        ctx.startForegroundService(
            Intent(ctx, SorenVpnService::class.java).apply {
                action = SorenVpnService.ACTION_START
                putExtra(SorenVpnService.EXTRA_CONFIG_ID, cfgId)
            })
    }
}
