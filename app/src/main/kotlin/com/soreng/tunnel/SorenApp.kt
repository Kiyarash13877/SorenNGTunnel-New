package com.soreng.tunnel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.soreng.tunnel.security.SecurityManager
import com.soreng.tunnel.storage.BinaryExtractor
import com.soreng.tunnel.utils.BatteryHelper
import com.soreng.tunnel.utils.OemCompatHelper
import javax.inject.Inject

@HiltAndroidApp
class SorenApp : Application() {
    @Inject lateinit var binExtractor: BinaryExtractor
    @Inject lateinit var security:     SecurityManager
    @Inject lateinit var oemHelper:    OemCompatHelper
    @Inject lateinit var battery:      BatteryHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createChannels()
        scope.launch { binExtractor.extractAll() }
        scope.launch { security.initialize() }
        scope.launch {
            oemHelper.logRomInfo()
            battery.logState()
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_VPN) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_VPN,
                    getString(R.string.channel_vpn),
                    NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false); enableVibration(false)
                })
        }
        if (nm.getNotificationChannel(CHANNEL_ALERT) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERT,
                    getString(R.string.channel_alert),
                    NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    companion object {
        const val CHANNEL_VPN   = "soren_vpn"
        const val CHANNEL_ALERT = "soren_alert"
    }
}
