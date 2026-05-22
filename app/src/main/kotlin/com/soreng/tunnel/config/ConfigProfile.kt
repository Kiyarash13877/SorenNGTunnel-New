package com.soreng.tunnel.config

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "config_profiles")
data class ConfigProfile(
    @PrimaryKey(autoGenerate = true) val id:     Long    = 0,
    val name:             String  = "",
    val protocol:         Protocol = Protocol.VLESS,
    val address:          String  = "",
    val port:             Int     = 443,
    val uuid:             String  = "",
    val password:         String  = "",
    val network:          String  = "tcp",
    val security:         String  = "tls",
    val flow:             String  = "",
    val path:             String  = "/",
    val host:             String  = "",
    val sni:              String  = "",
    val fingerprint:      String  = "chrome",
    val publicKey:        String  = "",
    val shortId:          String  = "",
    val spiderX:          String  = "",
    val grpcServiceName:  String  = "",
    val alterId:          Int     = 0,
    val encryption:       String  = "auto",
    val remarks:          String  = "",
    val rawUri:           String  = "",
    val isFavorite:       Boolean = false,
    val subscriptionId:   Long    = -1L,
    val groupName:        String  = "",
    val latencyMs:        Long    = -1L,
    val createdAt:        Long    = System.currentTimeMillis(),
    val updatedAt:        Long    = System.currentTimeMillis()
)

enum class Protocol(val scheme: String) {
    VMESS("vmess"), VLESS("vless"), TROJAN("trojan"),
    SHADOWSOCKS("ss"), SOCKS5("socks"), HTTP("http");
    companion object {
        fun fromScheme(s: String) = values().find { it.scheme.equals(s, true) }
    }
}
