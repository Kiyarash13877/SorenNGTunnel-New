package com.soreng.tunnel.vpn

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serializes reconnect attempts. Prevents concurrent reconnect races.
 * Exponential backoff. Max 5 failures before giving up.
 */
@Singleton
class ReconnectManager @Inject constructor() {
    private val TAG    = "ReconnectManager"
    private val mutex  = Mutex()
    private val fails  = AtomicInteger(0)
    private val inProg = AtomicBoolean(false)
    private val uStop  = AtomicBoolean(false)

    companion object {
        private val BACKOFF = longArrayOf(2_000,5_000,10_000,20_000,40_000)
        private const val MAX = 5
    }

    suspend fun reconnect(action: suspend () -> Unit): Boolean {
        if (uStop.get())  { Log.i(TAG,"suppressed — user stopped"); return false }
        if (inProg.get()) { Log.i(TAG,"suppressed — already reconnecting"); return false }
        if (fails.get() >= MAX) { Log.e(TAG,"max reconnects ($MAX) reached"); return false }
        return mutex.withLock {
            if (uStop.get() || inProg.get()) return@withLock false
            inProg.set(true)
            try {
                val delay = BACKOFF.getOrElse(fails.get()) { BACKOFF.last() }
                Log.i(TAG,"reconnect #${fails.get()+1} in ${delay}ms"); delay(delay)
                action(); fails.set(0); true
            } catch (e: Exception) {
                fails.incrementAndGet()
                Log.e(TAG,"reconnect failed (${fails.get()}): ${e.message}"); false
            } finally { inProg.set(false) }
        }
    }

    fun markUserStop() { uStop.set(true); inProg.set(false) }
    fun reset()        { fails.set(0); inProg.set(false); uStop.set(false) }
    fun isUserStop()   = uStop.get()
}
