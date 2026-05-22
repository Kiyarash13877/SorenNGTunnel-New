package com.soreng.tunnel.security

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import com.soreng.tunnel.storage.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: AppPreferences
) {
    private val TAG = "SecurityManager"

    suspend fun initialize() {
        Log.i(TAG, "Security initialized")
    }

    /** Call from Activity.onCreate() to block screenshots. */
    fun applyWindowSecurity(activity: Activity) {
        try {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE)
        } catch (e: Exception) { Log.w(TAG,"window security: ${e.message}") }
    }

    fun clearMemory() { Runtime.getRuntime().gc(); System.gc() }
}
