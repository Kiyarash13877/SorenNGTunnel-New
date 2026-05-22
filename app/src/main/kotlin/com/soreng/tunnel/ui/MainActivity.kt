package com.soreng.tunnel.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import com.soreng.tunnel.security.SecurityManager
import com.soreng.tunnel.ui.theme.Black
import com.soreng.tunnel.ui.theme.SorenTheme
import com.soreng.tunnel.utils.NotificationPermissionHelper
import com.soreng.tunnel.utils.BatteryHelper
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var security: SecurityManager
    @Inject lateinit var battery:  BatteryHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        security.applyWindowSecurity(this)

        // Android 13+: request notification permission (required for VPN foreground notification)
        NotificationPermissionHelper.requestIfNeeded(this) { granted ->
            if (!granted) {
                android.util.Log.w("MainActivity",
                    "POST_NOTIFICATIONS denied — VPN foreground service may not persist")
            }
        }

        setContent {
            SorenTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Black) {
                    SorenNavHost()
                }
            }
        }
    }
}
