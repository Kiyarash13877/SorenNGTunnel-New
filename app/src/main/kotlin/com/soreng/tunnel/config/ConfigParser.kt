package com.soreng.tunnel.config

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import java.net.URI
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses all supported VPN URI formats.
 * Returns null (never throws) for malformed input.
 * Validates required fields before returning.
 */
@Singleton
class ConfigParser @Inject constructor() {
    private val TAG  = "ConfigParser"
    private val gson = Gson()

    fun parse(raw: String): ConfigProfile? = try {
        val t = raw.trim()
        val p = when {
            t.startsWith("vmess://")                              -> parseVmess(t)
            t.startsWith("vless://")                              -> parseVless(t)
            t.startsWith("trojan://")                             -> parseTrojan(t)
            t.startsWith("ss://")                                 -> parseSs(t)
            t.startsWith("socks5://") || t.startsWith("socks://") -> parseSocks(t)
            t.startsWith("http://") && hasUserInfoOrPort(t)       -> parseHttp(t)
            t.startsWith("{")                                     -> parseJson(t)
            else -> { Log.d(TAG,"Unknown scheme: ${t.take(30)}"); null }
        }
        p?.let { validate(it) }
    } catch (e: Exception) { Log.w(TAG,"parse error ${raw.take(40)}: ${e.message}"); null }

    private fun validate(p: ConfigProfile): ConfigProfile? {
        if (p.address.isBlank()) { Log.w(TAG,"reject: blank address"); return null }
        if (p.port !in 1..65535)  { Log.w(TAG,"reject: bad port ${p.port}"); return null }
        return when (p.protocol) {
            Protocol.VMESS, Protocol.VLESS ->
                if (p.uuid.isBlank()) { Log.w(TAG,"reject: blank uuid"); null } else p
            Protocol.TROJAN, Protocol.SHADOWSOCKS ->
                if (p.password.isBlank()) { Log.w(TAG,"reject: blank password"); null } else p
            else -> p
        }
    }

    private fun parseVmess(uri: String): ConfigProfile {
        val b64 = uri.removePrefix("vmess://")
        val json = String(Base64.decode(pad(b64), Base64.URL_SAFE or Base64.NO_WRAP))
        val o = gson.fromJson(json, JsonObject::class.java)
            ?: throw JsonParseException("null JSON")
        return ConfigProfile(
            protocol    = Protocol.VMESS,
            name        = o.s("ps","VMess"),
            address     = o.s("add"),
            port        = o.s("port","0").toIntOrNull() ?: o.i("port",0),
            uuid        = o.s("id"),
            alterId     = o.i("aid",0),
            encryption  = o.s("scy","auto"),
            network     = o.s("net","tcp"),
            host        = o.s("host"),
            path        = o.s("path","/"),
            security    = o.s("tls"),
            sni         = o.s("sni"),
            fingerprint = o.s("fp","chrome"),
            rawUri      = uri
        )
    }

    private fun parseVless(uri: String): ConfigProfile {
        val withoutScheme = uri.removePrefix("vless://")
        val (beforeFrag, frag) = splitFrag(withoutScheme)
        val (userHostPort, query) = splitQuery(beforeFrag)
        val (uid, hostPort) = splitAt(userHostPort)
        val (host, portStr) = splitHostPort(hostPort)
        val q = parseQuery(query)
        return ConfigProfile(
            protocol        = Protocol.VLESS,
            name            = dec(frag) ?: "VLESS",
            address         = host,
            port            = portStr.toIntOrNull() ?: 443,
            uuid            = uid,
            network         = q["type"] ?: "tcp",
            security        = q["security"] ?: "none",
            flow            = q["flow"] ?: "",
            sni             = q["sni"] ?: "",
            fingerprint     = q["fp"] ?: "chrome",
            publicKey       = q["pbk"] ?: "",
            shortId         = q["sid"] ?: "",
            spiderX         = q["spx"] ?: "",
            path            = dec(q["path"]) ?: "/",
            host            = q["host"] ?: "",
            grpcServiceName = q["serviceName"] ?: "",
            rawUri          = uri
        )
    }

    private fun parseTrojan(uri: String): ConfigProfile {
        val withoutScheme = uri.removePrefix("trojan://")
        val (beforeFrag, frag) = splitFrag(withoutScheme)
        val (userHostPort, query) = splitQuery(beforeFrag)
        val (pass, hostPort) = splitAt(userHostPort)
        val (host, portStr) = splitHostPort(hostPort)
        val q = parseQuery(query)
        return ConfigProfile(
            protocol    = Protocol.TROJAN,
            name        = dec(frag) ?: "Trojan",
            address     = host,
            port        = portStr.toIntOrNull() ?: 443,
            password    = dec(pass) ?: pass,
            network     = q["type"] ?: "tcp",
            security    = q["security"] ?: "tls",
            sni         = q["sni"] ?: "",
            fingerprint = q["fp"] ?: "",
            path        = dec(q["path"]) ?: "/",
            host        = q["host"] ?: "",
            flow        = q["flow"] ?: "",
            rawUri      = uri
        )
    }

    private fun parseSs(uri: String): ConfigProfile {
        val withoutScheme = uri.removePrefix("ss://")
        val (main, frag) = splitFrag(withoutScheme)
        val name = dec(frag) ?: "Shadowsocks"
        return if ('@' in main) {
            val userInfo = main.substringBefore('@')
            val hostPort = main.substringAfter('@')
            val decoded  = safeB64(userInfo)
            val method   = decoded.substringBefore(':')
            val password = decoded.substringAfter(':')
            val host     = hostPort.substringBeforeLast(':')
            val port     = hostPort.substringAfterLast(':').toIntOrNull() ?: 443
            ConfigProfile(protocol=Protocol.SHADOWSOCKS, name=name, address=host,
                port=port, encryption=method, password=password, rawUri=uri)
        } else {
            val decoded  = safeB64(main)
            val methodPass = decoded.substringBefore('@')
            val hostPart   = decoded.substringAfter('@','/')
            ConfigProfile(protocol=Protocol.SHADOWSOCKS, name=name,
                address=hostPart.substringBeforeLast(':'),
                port=hostPart.substringAfterLast(':').toIntOrNull()?:443,
                encryption=methodPass.substringBefore(':'),
                password=methodPass.substringAfter(':'), rawUri=uri)
        }
    }

    private fun parseSocks(uri: String): ConfigProfile {
        val u = URI(uri.replace("socks5://","http://").replace("socks://","http://"))
        val (user,pass) = splitUserInfo(u.userInfo)
        return ConfigProfile(protocol=Protocol.SOCKS5, name=dec(u.fragment)?:"SOCKS5",
            address=u.host?:"", port=if(u.port>0) u.port else 1080,
            uuid=user, password=pass, rawUri=uri)
    }

    private fun parseHttp(uri: String): ConfigProfile {
        val u = URI(uri)
        val (user,pass) = splitUserInfo(u.userInfo)
        return ConfigProfile(protocol=Protocol.HTTP, name=dec(u.fragment)?:"HTTP",
            address=u.host?:"", port=if(u.port>0) u.port else 8080,
            uuid=user, password=pass, rawUri=uri)
    }

    private fun parseJson(json: String): ConfigProfile {
        val o = gson.fromJson(json, JsonObject::class.java) ?: throw JsonParseException("null")
        return ConfigProfile(protocol=Protocol.fromScheme(o.s("protocol","vless"))?:Protocol.VLESS,
            name=o.s("name","Custom"), address=o.s("address"), port=o.i("port",443),
            uuid=o.s("uuid"), password=o.s("password"),
            network=o.s("network","tcp"), security=o.s("security","tls"), rawUri=json)
    }

    private fun hasUserInfoOrPort(uri: String) = try {
        val u = URI(uri); u.userInfo != null || u.port > 0
    } catch (_: Exception) { false }

    private fun pad(s: String)   = s + "=".repeat((4 - s.length % 4) % 4)
    private fun safeB64(s: String): String = try {
        String(Base64.decode(pad(s), Base64.URL_SAFE or Base64.NO_WRAP))
    } catch (_: Exception) { try { String(Base64.decode(pad(s), Base64.DEFAULT)) } catch (_: Exception) { s } }
    private fun dec(s: String?)  = s?.let { try { URLDecoder.decode(it,"UTF-8") } catch (_:Exception){ it } }
    private fun splitFrag(s: String)  = if('#' in s) s.substringBefore('#') to s.substringAfter('#') else s to null
    private fun splitQuery(s: String) = if('?' in s) s.substringBefore('?') to s.substringAfter('?') else s to ""
    private fun splitAt(s: String)    = if('@' in s) s.substringBefore('@') to s.substringAfterLast('@') else "" to s
    private fun splitHostPort(s: String) = s.substringBeforeLast(':') to s.substringAfterLast(':','/')
    private fun splitUserInfo(info: String?): Pair<String,String> {
        if (info.isNullOrBlank()) return "" to ""
        return (dec(info.substringBefore(':')) ?: "") to (if(':' in info) dec(info.substringAfter(':')) ?: "" else "")
    }
    private fun parseQuery(q: String): Map<String,String> {
        if (q.isBlank()) return emptyMap()
        return q.split("&").mapNotNull { p ->
            val kv = p.split("=",limit=2)
            if (kv.size==2) runCatching { (dec(kv[0])?:kv[0]) to (dec(kv[1])?:kv[1]) }.getOrNull() else null
        }.toMap()
    }
    private fun JsonObject.s(k:String,d:String="") =
        if(has(k)&&!get(k).isJsonNull) runCatching{get(k).asString}.getOrDefault(d) else d
    private fun JsonObject.i(k:String,d:Int=0) =
        if(has(k)&&!get(k).isJsonNull) runCatching{get(k).asInt}.getOrDefault(d) else d
}
