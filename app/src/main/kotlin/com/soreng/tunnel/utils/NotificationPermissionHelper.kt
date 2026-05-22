package com.soreng.tunnel.utils

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Android 13+ (API 33) requires POST_NOTIFICATIONS runtime permission.
 * Without it, the VPN foreground notification is silently suppressed,
 * which causes the service to be killed as a background service.
 *
 * Call from MainActivity.onCreate() before starting any VPN operation.
 */
object NotificationPermissionHelper {

    fun requestIfNeeded(activity: ComponentActivity, onResult: (Boolean) -> Unit = {}) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(true); return
        }
        if (ContextCompat.checkSelfPermission(
                activity, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED) {
            onResult(true); return
        }
        val launcher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> onResult(granted) }
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun isGranted(activity: ComponentActivity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            activity, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
