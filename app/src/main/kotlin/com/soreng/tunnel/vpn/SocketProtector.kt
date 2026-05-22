package com.soreng.tunnel.vpn

import android.net.VpnService
import android.util.Log
import java.lang.ref.WeakReference
import java.net.DatagramSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real VpnService.protect() bridge.
 *
 * Registered with JNI via SorenJniBridge.registerProtectCallback(this).
 * JNI calls protectFd(fd) → VpnService.protect(fd) on any thread.
 * WeakReference prevents leaking the Service instance after onDestroy.
 */
@Singleton
class SocketProtector @Inject constructor() {
    private val TAG = "SocketProtector"
    @Volatile private var ref: WeakReference<VpnService>? = null

    fun register(svc: VpnService)  { ref = WeakReference(svc); Log.d(TAG, "registered") }
    fun unregister()               { ref?.clear(); ref = null;  Log.d(TAG, "unregistered") }
    val isAvailable: Boolean get() = ref?.get() != null

    /** Called from JNI — must be safe from any thread. */
    fun protectFd(fd: Int): Boolean {
        if (fd < 0) return false
        val svc = ref?.get() ?: run { Log.w(TAG, "protectFd($fd): no service"); return false }
        return try {
            val ok = svc.protect(fd)
            if (!ok) Log.w(TAG, "VpnService.protect($fd) returned false")
            ok
        } catch (e: Exception) { Log.e(TAG, "protectFd($fd): ${e.message}"); false }
    }

    fun protect(s: Socket): Boolean {
        val svc = ref?.get() ?: return false
        return try { svc.protect(s) } catch (_: Exception) { false }
    }

    fun protect(s: DatagramSocket): Boolean {
        val svc = ref?.get() ?: return false
        return try { svc.protect(s) } catch (_: Exception) { false }
    }
}
