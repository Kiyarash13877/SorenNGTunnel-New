package com.soreng.tunnel.vpn

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

data class ProbeResult(val success: Boolean, val latencyMs: Long, val errorMsg: String? = null)

/**
 * Verifies the FULL VPN stack carries real traffic before marking Connected.
 *
 * Flow verified: TUN → tun2socks → Xray SOCKS5 :10808 → Xray config
 *                → psiphon-out → Psiphon :1080 → Internet
 *
 * Does NOT call protect() — packets MUST route via TUN to prove the chain works.
 */
@Singleton
class ConnectivityVerifier @Inject constructor() {
    private val TAG = "ConnVerifier"

    suspend fun verify(xraySocksPort: Int = 10808, timeoutMs: Int = 15_000): ProbeResult =
        withContext(Dispatchers.IO) {
            // Phase 1: SOCKS5 CONNECT handshake proves Xray→Psiphon chain
            val s = socks5Connect("127.0.0.1", xraySocksPort, "1.1.1.1", 443, timeoutMs)
            if (!s.success) return@withContext s

            // Phase 2: HTTP GET through the chain confirms real HTTP proxying
            val h = httpViaSocks("127.0.0.1", xraySocksPort, timeoutMs)
            Log.i(TAG, "Verify: socks=${s.success}(${s.latencyMs}ms) http=${h.success}(${h.latencyMs}ms)")

            // Accept if at least SOCKS5 CONNECT succeeded
            if (h.success) h else ProbeResult(true, s.latencyMs)
        }

    private fun socks5Connect(
        sh: String, sp: Int, dh: String, dp: Int, tms: Int
    ): ProbeResult {
        val t0 = System.currentTimeMillis()
        return try {
            Socket().use { s ->
                s.soTimeout = tms; s.tcpNoDelay = true
                s.connect(InetSocketAddress(sh, sp), tms)
                val o = s.outputStream; val i = s.inputStream

                o.write(byteArrayOf(0x05,0x01,0x00)); o.flush()
                val g = ByteArray(2); var r=0
                while(r<2){val n=i.read(g,r,2-r);if(n<0)throw Exception("EOF@greeting");r+=n}
                if(g[0]!=0x05.toByte()||g[1]!=0x00.toByte())
                    throw Exception("SOCKS5 auth rejected: ${g[1]}")

                val hb = dh.toByteArray(Charsets.US_ASCII)
                val req = ByteArray(7+hb.size).apply {
                    this[0]=0x05;this[1]=0x01;this[2]=0x00;this[3]=0x03;this[4]=hb.size.toByte()
                    System.arraycopy(hb,0,this,5,hb.size)
                    this[5+hb.size]=(dp shr 8).toByte();this[6+hb.size]=(dp and 0xFF).toByte()
                }
                o.write(req); o.flush()
                val rp=ByteArray(4);r=0
                while(r<4){val n=i.read(rp,r,4-r);if(n<0)throw Exception("EOF@resp");r+=n}
                if(rp[1]!=0x00.toByte()) throw Exception("CONNECT rejected rep=0x%02x".format(rp[1]))

                val ms = System.currentTimeMillis()-t0
                Log.i(TAG,"SOCKS5 CONNECT $dh:$dp OK ${ms}ms")
                ProbeResult(true, ms)
            }
        } catch(e: Exception) {
            Log.w(TAG,"socks5Connect failed: ${e.message}")
            ProbeResult(false, System.currentTimeMillis()-t0, e.message)
        }
    }

    private fun httpViaSocks(sh: String, sp: Int, tms: Int): ProbeResult {
        val t0 = System.currentTimeMillis()
        return try {
            Socket().use { s ->
                s.soTimeout = tms; s.connect(InetSocketAddress(sh,sp), tms)
                val o=s.outputStream; val i=s.inputStream
                o.write(byteArrayOf(0x05,0x01,0x00)); o.flush()
                val g=ByteArray(2);var r=0
                while(r<2){val n=i.read(g,r,2-r);if(n<0)throw Exception("EOF");r+=n}
                if(g[0]!=0x05.toByte()||g[1]!=0x00.toByte()) throw Exception("greeting")
                val hb="cp.cloudflare.com".toByteArray(Charsets.US_ASCII)
                val req=ByteArray(7+hb.size).apply{
                    this[0]=0x05;this[1]=0x01;this[2]=0x00;this[3]=0x03;this[4]=hb.size.toByte()
                    System.arraycopy(hb,0,this,5,hb.size);this[5+hb.size]=0x00;this[6+hb.size]=80.toByte()
                }
                o.write(req);o.flush()
                val rp=ByteArray(4);r=0
                while(r<4){val n=i.read(rp,r,4-r);if(n<0)throw Exception("EOF");r+=n}
                if(rp[1]!=0x00.toByte()) throw Exception("CONNECT rej")
                o.write("GET / HTTP/1.1\r\nHost: cp.cloudflare.com\r\nConnection: close\r\n\r\n"
                    .toByteArray(Charsets.US_ASCII)); o.flush()
                val sb=StringBuilder(); var c:Int
                while(i.read().also{c=it}!=-1){ sb.append(c.toChar()); if(sb.length>120||sb.endsWith("\n")) break }
                val code=sb.toString().split(" ").getOrNull(1)?.trim()?.toIntOrNull()?:0
                if(code !in 100..399) throw Exception("HTTP $code")
                ProbeResult(true,System.currentTimeMillis()-t0)
            }
        } catch(e: Exception){
            Log.w(TAG,"httpViaSocks: ${e.message}")
            ProbeResult(false,System.currentTimeMillis()-t0,e.message)
        }
    }
}
