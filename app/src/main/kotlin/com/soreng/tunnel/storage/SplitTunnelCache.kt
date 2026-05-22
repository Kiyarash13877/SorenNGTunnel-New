package com.soreng.tunnel.storage

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton

/** Thread-safe cache of bypass app packages. Validates packages exist before caching. */
@Singleton
class SplitTunnelCache @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: AppPreferences
) {
    private val TAG   = "SplitTunnelCache"
    private val cache = CopyOnWriteArraySet<String>()
    @Volatile private var loaded = false

    suspend fun load() = withContext(Dispatchers.IO) {
        val raw = prefs.getBypassApps()
        val pm  = ctx.packageManager
        val valid = raw.filter { pkg ->
            try { pm.getPackageInfo(pkg, 0); true }
            catch (e: PackageManager.NameNotFoundException) { Log.w(TAG,"skip $pkg: not installed"); false }
        }
        cache.clear(); cache.addAll(valid); loaded = true
        Log.i(TAG,"Loaded ${cache.size} bypass packages")
    }

    fun getBypassPackages(): Set<String> = cache.toSet()
    fun invalidate()                     { cache.clear(); loaded = false }
    val isLoaded: Boolean get()          = loaded
}
