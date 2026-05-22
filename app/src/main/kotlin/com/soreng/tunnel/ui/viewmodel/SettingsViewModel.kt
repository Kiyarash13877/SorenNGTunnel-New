package com.soreng.tunnel.ui.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.soreng.tunnel.storage.AppPreferences
import com.soreng.tunnel.storage.SplitTunnelCache
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    app: Application,
    private val prefs:      AppPreferences,
    private val splitCache: SplitTunnelCache
) : AndroidViewModel(app) {

    val autoStart    = prefs.autoStartFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val autoReconnect= prefs.autoReconnectFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val killSwitch   = prefs.killSwitchFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val fakeDns      = prefs.fakeDnsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val udp          = prefs.udpFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val ipv6         = prefs.ipv6Flow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val dnsPrimary   = prefs.dnsPrimaryFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "1.1.1.1")
    val dnsSecondary = prefs.dnsSecondaryFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "8.8.8.8")
    val bypassApps   = prefs.bypassAppsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun setAutoStart(v: Boolean)    = viewModelScope.launch { prefs.setAutoStart(v) }
    fun setAutoReconnect(v: Boolean)= viewModelScope.launch { prefs.setAutoReconnect(v) }
    fun setKillSwitch(v: Boolean)   = viewModelScope.launch { prefs.setKillSwitch(v) }
    fun setFakeDns(v: Boolean)      = viewModelScope.launch { prefs.setFakeDns(v) }
    fun setUdp(v: Boolean)          = viewModelScope.launch { prefs.setUdp(v) }
    fun setIpv6(v: Boolean)         = viewModelScope.launch { prefs.setIPv6(v) }
    fun setDnsPrimary(v: String)    = viewModelScope.launch { prefs.setDnsPrimary(v) }
    fun setDnsSecondary(v: String)  = viewModelScope.launch { prefs.setDnsSecondary(v) }

    fun addBypassApp(pkg: String) = viewModelScope.launch {
        val cur = prefs.getBypassApps().toMutableSet()
        cur.add(pkg); prefs.setBypassApps(cur); splitCache.invalidate()
    }
    fun removeBypassApp(pkg: String) = viewModelScope.launch {
        val cur = prefs.getBypassApps().toMutableSet()
        cur.remove(pkg); prefs.setBypassApps(cur); splitCache.invalidate()
    }

    fun getInstalledApps(): List<Pair<String,String>> {
        val pm = getApplication<Application>().packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != getApplication<Application>().packageName }
            .map { it.packageName to (pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.second }
    }
}
