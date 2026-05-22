package com.soreng.tunnel.vpn

import android.content.Context
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages a PARTIAL_WAKE_LOCK for the VPN tunnel.
 *
 * Rules:
 *  - Acquired only while VPN is actively routing traffic.
 *  - 4-hour safety cap prevents runaway battery drain.
 *  - Reference-counted=false prevents double-release crashes.
 *  - Always released in VPN cleanup path.
 *  - Thread-safe via synchronized block.
 */
@Singleton
class WakeLockManager @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val TAG = "WakeLockManager"
    private val pm  = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    @Volatile private var lock: PowerManager.WakeLock? = null
    private val mutex = Any()

    fun acquire() = synchronized(mutex) {
        if (lock?.isHeld == true) { Log.d(TAG,"already held"); return@synchronized }
        val wl = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "com.soreng.tunnel:VpnWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1_000L)   // 4h safety cap
        }
        lock = wl
        Log.i(TAG, "WakeLock acquired")
    }

    fun release() = synchronized(mutex) {
        val wl = lock ?: return@synchronized
        try { if (wl.isHeld) { wl.release(); Log.i(TAG,"WakeLock released") } }
        catch (e: Exception) { Log.w(TAG,"release: ${e.message}") }
        finally { lock = null }
    }

    val isHeld: Boolean get() = synchronized(mutex) { lock?.isHeld == true }
}
