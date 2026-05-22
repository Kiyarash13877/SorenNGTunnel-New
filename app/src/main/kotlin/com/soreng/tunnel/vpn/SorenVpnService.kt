package com.soreng.tunnel.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.soreng.tunnel.notifications.VpnNotificationManager
import com.soreng.tunnel.psiphon.PsiphonManager
import com.soreng.tunnel.stats.StatsManager
import com.soreng.tunnel.storage.AppPreferences
import com.soreng.tunnel.storage.BinaryExtractor
import com.soreng.tunnel.storage.SplitTunnelCache
import com.soreng.tunnel.tunnel.Tun2SocksManager
import com.soreng.tunnel.utils.OemCompatHelper
import com.soreng.tunnel.utils.VpnKeepAliveWorker
import com.soreng.tunnel.xray.XrayManager
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

@AndroidEntryPoint
class SorenVpnService : VpnService() {

    @Inject lateinit var psiphon:      PsiphonManager
    @Inject lateinit var xray:         XrayManager
    @Inject lateinit var tun2socks:    Tun2SocksManager
    @Inject lateinit var stats:        StatsManager
    @Inject lateinit var notif:        VpnNotificationManager
    @Inject lateinit var prefs:        AppPreferences
    @Inject lateinit var binExtractor: BinaryExtractor
    @Inject lateinit var splitCache:   SplitTunnelCache
    @Inject lateinit var protector:    SocketProtector
    @Inject lateinit var verifier:     ConnectivityVerifier
    @Inject lateinit var watchdog:     WatchdogSupervisor
    @Inject lateinit var reconnMgr:    ReconnectManager
    @Inject lateinit var wakeLock:     WakeLockManager
    @Inject lateinit var procGuard:    ProcessGuard
    @Inject lateinit var oemHelper:    OemCompatHelper

    private val svcScope     = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cleanupMutex = Mutex()
    private val jni          = SorenJniBridge()

    @Volatile private var tunPfd:       ParcelFileDescriptor? = null
    @Volatile private var currentCfgId: Long = -1L
    @Volatile private var startGuard    = false

    companion object {
        const val ACTION_START    = "com.soreng.tunnel.START_VPN"
        const val ACTION_STOP     = "com.soreng.tunnel.STOP_VPN"
        const val EXTRA_CONFIG_ID = "config_id"
        const val PSIPHON_PORT    = 1080
        const val XRAY_SOCKS_PORT = 10808
        private const val NOTIF_ID = 1337
        private const val TAG = "SorenVpnService"

        /** Single source of truth — UI only reads this. */
        val state = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
    }

    override fun onCreate() {
        super.onCreate()
        // Register real protect() bridge BEFORE any sockets are created
        protector.register(this)
        jni.registerProtectCallback(protector)
        oemHelper.logRomInfo()
        Log.i(TAG, "onCreate: protect() bridge active [${jni.getVersion()}]")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START -> {
                val cfgId = intent.getLongExtra(EXTRA_CONFIG_ID, -1L)
                if (cfgId >= 0 && !state.value.isActive && !startGuard) {
                    startGuard = true
                    reconnMgr.reset()
                    startForeground(NOTIF_ID, notif.buildConnecting())
                    svcScope.launch { try { doStart(cfgId) } finally { startGuard = false } }
                } else {
                    Log.w(TAG, "Start ignored: cfgId=$cfgId active=${state.value.isActive} guard=$startGuard")
                }
                START_STICKY
            }
            ACTION_STOP -> {
                reconnMgr.markUserStop()
                VpnKeepAliveWorker.cancel(this)
                svcScope.launch { doShutdown() }
                START_NOT_STICKY
            }
            null -> {
                // Android restarted sticky service after OEM kill
                if (currentCfgId >= 0 && !reconnMgr.isUserStop()) {
                    Log.i(TAG, "Sticky restart — reconnecting cfgId=$currentCfgId")
                    svcScope.launch { reconnMgr.reconnect { doStart(currentCfgId) } }
                }
                START_STICKY
            }
            else -> START_NOT_STICKY
        }
    }

    // ── STRICT 6-STEP STARTUP ────────────────────────────────
    private suspend fun doStart(cfgId: Long) {
        // Pre-flight cleanup
        procGuard.killZombies()
        binExtractor.extractAll()
        splitCache.load()
        currentCfgId = cfgId

        // Save for WorkManager keepalive
        getSharedPreferences("soren_keepalive_prefs", MODE_PRIVATE)
            .edit().putLong("last_cfg_id", cfgId).apply()

        try {
            state.value = VpnConnectionState.Connecting
            postNotif(notif.buildConnecting())

            // [1/6] Psiphon — MUST start first
            Log.i(TAG, "[1/6] Starting Psiphon...")
            psiphon.start()

            // [2/6] Verify Psiphon SOCKS5
            Log.i(TAG, "[2/6] Verifying Psiphon SOCKS5 :$PSIPHON_PORT")
            awaitSocks5(PSIPHON_PORT, 35_000, "Psiphon")

            // [3/6] Xray — all outbound forced via Psiphon SOCKS5
            Log.i(TAG, "[3/6] Starting Xray...")
            xray.start(cfgId, PSIPHON_PORT)

            // [4/6] Verify Xray SOCKS5
            Log.i(TAG, "[4/6] Verifying Xray SOCKS5 :$XRAY_SOCKS_PORT")
            awaitSocks5(XRAY_SOCKS_PORT, 20_000, "Xray")

            // [5/6] TUN + tun2socks
            Log.i(TAG, "[5/6] Building TUN + tun2socks...")
            val pfd = buildTun()
                ?: throw IllegalStateException("VPN establish() null — permission revoked or concurrent call?")
            tunPfd = pfd
            jni.setTunFd(pfd.fd)
            tun2socks.start(
                tunFd     = pfd.fd,
                socksPort = XRAY_SOCKS_PORT,
                mtu       = 1500,
                udp       = prefs.isUdpEnabled()
            )
            delay(600)
            if (!tun2socks.isRunning())
                throw IllegalStateException("tun2socks died immediately after start — check binary")

            // [6/6] End-to-end verification
            // ── UI MUST NOT show CONNECTED until this passes ──
            Log.i(TAG, "[6/6] End-to-end connectivity verification...")
            val probe = verifier.verify(XRAY_SOCKS_PORT, 15_000)
            if (!probe.success) throw IllegalStateException(
                "End-to-end probe FAILED: ${probe.errorMsg}. " +
                "Refusing Connected state — no real traffic confirmed.")

            // ── ALL STEPS PASSED ──────────────────────────────
            Log.i(TAG, "VPN established — latency=${probe.latencyMs}ms")
            state.value = VpnConnectionState.Connected(
                connectedAt    = System.currentTimeMillis(),
                probeLatencyMs = probe.latencyMs
            )
            wakeLock.acquire()
            postNotif(notif.buildConnected())
            stats.startSession()

            // Schedule WorkManager keepalive for OEM ROM survival
            VpnKeepAliveWorker.schedule(this)

            // Start watchdog
            watchdog.start(cfgId, svcScope) { id ->
                val ok = reconnMgr.reconnect { doCleanup(); doStart(id) }
                if (!ok) { doCleanup(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            }

        } catch (e: CancellationException) {
            Log.i(TAG, "doStart cancelled")
            doCleanup()
        } catch (e: Exception) {
            Log.e(TAG, "VPN start FAILED: ${e.message}", e)
            state.value = VpnConnectionState.Error(e.message ?: "Unknown error")
            postNotif(notif.buildError(e.message ?: "Failed"))
            doCleanup()
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private suspend fun buildTun(): ParcelFileDescriptor? {
        val ipv6 = prefs.isIPv6Enabled()
        val b = Builder()
            .setSession("SorenNG")
            .setMtu(1500)
            .setBlocking(false)
            .addAddress("10.89.0.1", 30)
            .addDnsServer("198.18.0.2")     // FakeDNS virtual address
            .addDnsServer("1.1.1.1")
            .addRoute("0.0.0.0", 0)          // All IPv4 through VPN
            .addDisallowedApplication(packageName) // Prevent self-routing loop

        if (ipv6) {
            b.addAddress("fd00:1:2:3::1", 128)
             .addRoute("::", 0)
             .addDnsServer("2606:4700:4700::1111")
        } else {
            // Route IPv6 into VPN to prevent leaks — Xray discards if not needed
            for ((addr, prefix) in listOf("2000::" to 3, "fc00::" to 7, "fe80::" to 10)) {
                try { b.addRoute(addr, prefix) } catch (_: Exception) {}
            }
        }

        for (pkg in splitCache.getBypassPackages()) {
            try { b.addDisallowedApplication(pkg) }
            catch (e: Exception) { Log.w(TAG, "bypass $pkg: ${e.message}") }
        }

        // Android 14+ requires explicit permission
        if (Build.VERSION.SDK_INT >= 34) {
            try { b.setMetered(false) } catch (_: Exception) {}
        }

        return b.establish()
    }

    private suspend fun doShutdown() {
        doCleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun doCleanup() = cleanupMutex.withLock {
        Log.i(TAG, "doCleanup: ordered shutdown")
        state.value = VpnConnectionState.Disconnecting
        watchdog.stop()
        wakeLock.release()
        stats.stopSession()
        safeStop("tun2socks")  { tun2socks.stop() }
        safeStop("xray")       { xray.stop() }
        safeStop("psiphon")    { psiphon.stop() }
        try {
            tunPfd?.close()
        } catch (e: Exception) { Log.w(TAG, "tunPfd close: ${e.message}") }
        finally { tunPfd = null }
        jni.cleanup()
        safeStop("zombies") { procGuard.killZombies() }
        state.value = VpnConnectionState.Disconnected
        Log.i(TAG, "doCleanup: done")
    }

    private suspend fun safeStop(n: String, b: suspend () -> Unit) =
        try { b() } catch (e: Exception) { Log.w(TAG, "safeStop[$n]: ${e.message}") }

    /**
     * Await SOCKS5 readiness.
     * ALWAYS protects the probe socket to prevent self-routing into TUN.
     */
    private suspend fun awaitSocks5(port: Int, timeoutMs: Long, label: String) =
        withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + timeoutMs
            var attempts = 0; var lastErr = "timeout"
            while (System.currentTimeMillis() < deadline) {
                attempts++
                try {
                    Socket().use { s ->
                        protector.protect(s)     // CRITICAL — prevent self-routing
                        s.soTimeout   = 1_500
                        s.tcpNoDelay  = true
                        s.connect(InetSocketAddress("127.0.0.1", port), 1_500)
                    }
                    Log.i(TAG, "$label SOCKS5 ready ($attempts attempts)")
                    return@withContext
                } catch (e: Exception) { lastErr = e.message ?: "err"; delay(600) }
            }
            throw IllegalStateException(
                "$label :$port not ready after ${timeoutMs}ms ($attempts attempts). Last: $lastErr")
        }

    private fun postNotif(n: android.app.Notification) = try {
        getSystemService(android.app.NotificationManager::class.java).notify(NOTIF_ID, n)
    } catch (e: Exception) { Log.w(TAG, "postNotif: ${e.message}") }

    override fun onRevoke() {
        Log.w(TAG, "VPN revoked by system")
        reconnMgr.markUserStop()
        VpnKeepAliveWorker.cancel(this)
        svcScope.launch { doCleanup() }
        super.onRevoke()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        reconnMgr.markUserStop()
        jni.unregisterProtectCallback()
        protector.unregister()
        runBlocking { withTimeoutOrNull(5_000) { doCleanup() } }
        svcScope.cancel()
        super.onDestroy()
    }
}
