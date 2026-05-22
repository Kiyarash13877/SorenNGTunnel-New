package com.soreng.tunnel.vpn

import android.util.Log

class SorenJniBridge {
    private val TAG = "SorenJniBridge"
    private var loaded = false

    init {
        try {
            System.loadLibrary("sorenjni")
            loaded = true
            Log.i(TAG, "sorenjni loaded: ${getVersion()}")
        } catch (e: UnsatisfiedLinkError) { Log.e(TAG, "sorenjni load failed: ${e.message}") }
    }

    fun registerProtectCallback(p: SocketProtector): Int {
        if (!loaded) return -1
        return try { nativeRegisterProtectCallback(p) }
        catch (e: Exception) { Log.e(TAG, "register: ${e.message}"); -1 }
    }

    fun unregisterProtectCallback() {
        if (!loaded) return
        try { nativeUnregisterProtectCallback() } catch (e: Exception) { Log.w(TAG, e.message) }
    }

    fun protectFd(fd: Int): Boolean {
        if (!loaded || fd < 0) return false
        return try { nativeProtectFd(fd) } catch (e: Exception) { Log.e(TAG, "protectFd: ${e.message}"); false }
    }

    fun setTunFd(fd: Int): Int {
        if (!loaded || fd < 0) return -1
        return try { nativeSetTunFd(fd) } catch (e: Exception) { Log.e(TAG, e.message); -1 }
    }

    fun setSocketMark(fd: Int, mark: Int): Int {
        if (!loaded || fd < 0) return -1
        return try { nativeSetSocketMark(fd, mark) } catch (_: Exception) { -1 }
    }

    fun closeFd(fd: Int) {
        if (!loaded || fd < 0) return
        try { nativeCloseFd(fd) } catch (_: Exception) {}
    }

    fun getVersion(): String = try { if (loaded) nativeGetVersion() else "unavailable" } catch (_: Exception) { "?" }
    fun isRunning():  Boolean = try { loaded && nativeIsRunning() }       catch (_: Exception) { false }
    fun createSocketPair(fds: IntArray): Int = try { if (loaded) nativeCreateSocketPair(fds) else -1 } catch (_: Exception) { -1 }

    fun cleanup() {
        if (!loaded) return
        try { nativeCleanup() } catch (e: Exception) { Log.w(TAG, "cleanup: ${e.message}") }
    }

    private external fun nativeRegisterProtectCallback(p: SocketProtector): Int
    private external fun nativeUnregisterProtectCallback()
    private external fun nativeProtectFd(fd: Int): Boolean
    private external fun nativeSetTunFd(fd: Int): Int
    private external fun nativeSetSocketMark(fd: Int, mark: Int): Int
    private external fun nativeCloseFd(fd: Int)
    private external fun nativeGetVersion(): String
    private external fun nativeIsRunning(): Boolean
    private external fun nativeCreateSocketPair(fds: IntArray): Int
    private external fun nativeCleanup()
}
