package com.soreng.tunnel.psiphon

import android.util.Log

/**
 * Bridge to gomobile-compiled Psiphon tunnel-core library.
 * Built via: gomobile bind -v -target android/arm64 -androidapi 26
 *              -o app/libs/psiphon.aar ./MobileLibrary/psi/
 * from https://github.com/Psiphon-Labs/psiphon-tunnel-core
 */
object PsiphonLibBridge {
    private const val TAG = "PsiphonLibBridge"
    @Volatile private var loaded = false

    init {
        try {
            System.loadLibrary("psiphon")
            loaded = true
            Log.i(TAG, "libpsiphon.so loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "libpsiphon.so not available: ${e.message}")
        }
    }

    fun start(port: Int): Boolean {
        if (!loaded) return false
        return try { nativeStart(port); true }
        catch (e: Exception) { Log.e(TAG, "start: ${e.message}"); false }
    }

    fun stop() {
        if (!loaded) return
        try { nativeStop() } catch (e: Exception) { Log.w(TAG, "stop: ${e.message}") }
    }

    fun isConnected(): Boolean {
        if (!loaded) return false
        return try { nativeIsConnected() } catch (_: Exception) { false }
    }

    private external fun nativeStart(port: Int)
    private external fun nativeStop()
    private external fun nativeIsConnected(): Boolean
}
