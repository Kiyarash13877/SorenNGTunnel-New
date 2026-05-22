package com.soreng.tunnel.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.ds: DataStore<Preferences> by preferencesDataStore("soren_prefs")

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val ds = ctx.ds
    companion object {
        val K_AUTO_START    = booleanPreferencesKey("auto_start")
        val K_AUTO_RECONNECT= booleanPreferencesKey("auto_reconnect")
        val K_KILL_SWITCH   = booleanPreferencesKey("kill_switch")
        val K_FAKE_DNS      = booleanPreferencesKey("fake_dns")
        val K_UDP           = booleanPreferencesKey("udp")
        val K_IPV6          = booleanPreferencesKey("ipv6")
        val K_ANTI_DPI      = booleanPreferencesKey("anti_dpi")
        val K_DNS_1         = stringPreferencesKey("dns_primary")
        val K_DNS_2         = stringPreferencesKey("dns_secondary")
        val K_BYPASS_APPS   = stringSetPreferencesKey("bypass_apps")
        val K_LAST_CFG      = longPreferencesKey("last_config_id")
        val K_FIRST_LAUNCH  = booleanPreferencesKey("first_launch")
        val K_SCREENSHOTS   = booleanPreferencesKey("allow_screenshots")
    }
    suspend fun isAutoStartEnabled()    = ds.data.first()[K_AUTO_START]     ?: false
    suspend fun isAutoReconnectEnabled()= ds.data.first()[K_AUTO_RECONNECT] ?: true
    suspend fun isKillSwitchEnabled()   = ds.data.first()[K_KILL_SWITCH]    ?: true
    suspend fun isFakeDnsEnabled()      = ds.data.first()[K_FAKE_DNS]       ?: false
    suspend fun isUdpEnabled()          = ds.data.first()[K_UDP]            ?: true
    suspend fun isIPv6Enabled()         = ds.data.first()[K_IPV6]           ?: false
    suspend fun getLastConfigId()       = ds.data.first()[K_LAST_CFG]       ?: -1L
    suspend fun getBypassApps()         = ds.data.first()[K_BYPASS_APPS]    ?: emptySet()
    suspend fun getDnsPrimary()         = ds.data.first()[K_DNS_1]          ?: "1.1.1.1"
    suspend fun getDnsSecondary()       = ds.data.first()[K_DNS_2]          ?: "8.8.8.8"
    suspend fun isFirstLaunch()         = ds.data.first()[K_FIRST_LAUNCH]   ?: true
    fun autoStartFlow()     : Flow<Boolean>     = ds.data.map { it[K_AUTO_START]     ?: false }
    fun autoReconnectFlow() : Flow<Boolean>     = ds.data.map { it[K_AUTO_RECONNECT] ?: true  }
    fun killSwitchFlow()    : Flow<Boolean>     = ds.data.map { it[K_KILL_SWITCH]    ?: true  }
    fun fakeDnsFlow()       : Flow<Boolean>     = ds.data.map { it[K_FAKE_DNS]       ?: false }
    fun udpFlow()           : Flow<Boolean>     = ds.data.map { it[K_UDP]            ?: true  }
    fun ipv6Flow()          : Flow<Boolean>     = ds.data.map { it[K_IPV6]           ?: false }
    fun bypassAppsFlow()    : Flow<Set<String>> = ds.data.map { it[K_BYPASS_APPS]    ?: emptySet() }
    fun dnsPrimaryFlow()    : Flow<String>      = ds.data.map { it[K_DNS_1]          ?: "1.1.1.1" }
    fun dnsSecondaryFlow()  : Flow<String>      = ds.data.map { it[K_DNS_2]          ?: "8.8.8.8" }
    suspend fun setAutoStart(v: Boolean)        = ds.edit { it[K_AUTO_START]     = v }
    suspend fun setAutoReconnect(v: Boolean)    = ds.edit { it[K_AUTO_RECONNECT] = v }
    suspend fun setKillSwitch(v: Boolean)       = ds.edit { it[K_KILL_SWITCH]    = v }
    suspend fun setFakeDns(v: Boolean)          = ds.edit { it[K_FAKE_DNS]       = v }
    suspend fun setUdp(v: Boolean)              = ds.edit { it[K_UDP]            = v }
    suspend fun setIPv6(v: Boolean)             = ds.edit { it[K_IPV6]           = v }
    suspend fun setLastConfigId(id: Long)       = ds.edit { it[K_LAST_CFG]       = id }
    suspend fun setFirstLaunch(v: Boolean)      = ds.edit { it[K_FIRST_LAUNCH]   = v }
    suspend fun setBypassApps(s: Set<String>)   = ds.edit { it[K_BYPASS_APPS]    = s }
    suspend fun setDnsPrimary(v: String)        = ds.edit { it[K_DNS_1]          = v }
    suspend fun setDnsSecondary(v: String)      = ds.edit { it[K_DNS_2]          = v }
}
