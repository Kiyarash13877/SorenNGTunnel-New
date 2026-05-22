package com.soreng.tunnel.vpn

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

data class HealthReport(
    val psiphon: Boolean, val xray: Boolean, val http: Boolean,
    val psiphonMs: Long=-1, val xrayMs: Long=-1, val httpMs: Long=-1
) {
    val allHealthy = psiphon && xray
    override fun toString() = "Health[p=$psiphon(${psiphonMs}ms) x=$xray(${xrayMs}ms) h=$http(${httpMs}ms)]"
}

@Singleton
class HealthChecker @Inject constructor(private val protector: SocketProtector) {
    private val TAG = "HealthChecker"

    suspend fun checkAll(psiphonPort: Int=1080, xrayPort: Int=10808): HealthReport =
        withContext(Dispatchers.IO) {
            val (pOk,pMs) = tcpProbe(psiphonPort)
            val (xOk,xMs) = tcpProbe(xrayPort)
            val (hOk,hMs) = if (xOk) httpProbe(xrayPort) else false to -1L
            HealthReport(pOk,xOk,hOk,pMs,xMs,hMs).also { Log.d(TAG,it.toString()) }
        }

    /** Protected TCP probe — bypasses VPN so we can check local ports. */
    private fun tcpProbe(port: Int): Pair<Boolean,Long> {
        val t=System.currentTimeMillis()
        return try {
            Socket().use { s ->
                protector.protect(s)
                s.soTimeout=3_000; s.tcpNoDelay=true
                s.connect(InetSocketAddress("127.0.0.1",port),3_000)
            }
            true to (System.currentTimeMillis()-t)
        } catch(e: Exception){ Log.w(TAG,"tcpProbe :$port: ${e.message}"); false to -1L }
    }

    /** HTTP probe through Xray — no protect(), validates real traffic path. */
    private fun httpProbe(xrayPort: Int): Pair<Boolean,Long> {
        val t=System.currentTimeMillis()
        return try {
            Socket().use { s ->
                s.soTimeout=8_000; s.connect(InetSocketAddress("127.0.0.1",xrayPort),3_000)
                val o=s.outputStream; val i=s.inputStream
                o.write(byteArrayOf(0x05,0x01,0x00));o.flush()
                val g=ByteArray(2);var r=0
                while(r<2){val n=i.read(g,r,2-r);if(n<0)throw Exception("EOF");r+=n}
                if(g[0]!=0x05.toByte()||g[1]!=0x00.toByte()) throw Exception("greeting")
                // CONNECT 1.1.1.1:443
                val req=byteArrayOf(0x05,0x01,0x00,0x01,1,1,1,1,0x01,0xBB.toByte())
                o.write(req);o.flush()
                val rp=ByteArray(4);r=0
                while(r<4){val n=i.read(rp,r,4-r);if(n<0)throw Exception("EOF");r+=n}
                if(rp[1]!=0x00.toByte()) throw Exception("CONNECT rej")
                true to (System.currentTimeMillis()-t)
            }
        } catch(e: Exception){ false to -1L }
    }
}
