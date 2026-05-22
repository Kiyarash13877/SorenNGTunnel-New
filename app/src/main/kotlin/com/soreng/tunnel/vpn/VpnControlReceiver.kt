package com.soreng.tunnel.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class VpnControlReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val svcIntent = Intent(ctx, SorenVpnService::class.java)
        when (intent.action) {
            "com.soreng.tunnel.VPN_CONNECT" -> {
                val cfgId = intent.getLongExtra(SorenVpnService.EXTRA_CONFIG_ID, -1L)
                if (cfgId < 0) return
                svcIntent.action = SorenVpnService.ACTION_START
                svcIntent.putExtra(SorenVpnService.EXTRA_CONFIG_ID, cfgId)
                ctx.startForegroundService(svcIntent)
            }
            "com.soreng.tunnel.VPN_DISCONNECT" -> {
                svcIntent.action = SorenVpnService.ACTION_STOP
                ctx.startService(svcIntent)
            }
        }
    }
}
