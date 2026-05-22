package com.soreng.tunnel.config

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the Xray runtime JSON config.
 *
 * ENFORCED traffic flow:
 *   tun2socks → Xray SOCKS5 :10808
 *   → routing → outbound tag="proxy"
 *   → proxySettings.tag="psiphon-out"   ← ALL traffic forced through Psiphon
 *   → Psiphon SOCKS5 127.0.0.1:1080
 *   → Internet
 *
 * DNS:
 *   FakeDNS at 198.18.0.0/15 captures DNS queries.
 *   Real DNS resolved via Xray dns-out → proxy → Psiphon (never direct).
 *   IPv6 queryStrategy=UseIPv4 when IPv6 disabled → prevents IPv6 DNS leaks.
 *
 * No "direct" outbound — ALL traffic routes through Psiphon.
 */
@Singleton
class RuntimeConfigBuilder @Inject constructor() {

    fun build(p: ConfigProfile, psiphonPort: Int): JsonObject = JsonObject().apply {
        add("log",      log())
        add("dns",      dns())
        add("inbounds", inbounds())
        add("outbounds",outbounds(p, psiphonPort))
        add("routing",  routing())
        add("policy",   policy())
        add("stats",    JsonObject())
    }

    private fun log() = jo { addProperty("loglevel","warning"); addProperty("access","none") }

    private fun dns() = jo {
        add("servers", ja {
            add(jo {  // FakeDNS captures all domains
                addProperty("address","fakedns")
                add("domains", ja { add("geosite:geolocation-!cn"); add("regexp:.*") })
            })
            add(jo {  // Real DNS via DoH — routes through Xray proxy → Psiphon
                addProperty("address","https://1.1.1.1/dns-query"); addProperty("port",443)
            })
            add(jo { addProperty("address","https://8.8.8.8/dns-query"); addProperty("port",443) })
        })
        addProperty("fakeIp","198.18.0.0/15")
        addProperty("queryStrategy","UseIPv4")   // IPv6 DNS leak prevention
        add("hosts", JsonObject())
    }

    private fun inbounds() = ja {
        add(jo {  // SOCKS5 — tun2socks connects here
            addProperty("tag","socks-in"); addProperty("port",10808)
            addProperty("listen","127.0.0.1"); addProperty("protocol","socks")
            add("settings", jo { addProperty("auth","noauth"); addProperty("udp",true); addProperty("ip","127.0.0.1") })
            add("sniffing", sniff())
        })
        add(jo {  // HTTP — optional
            addProperty("tag","http-in"); addProperty("port",10809)
            addProperty("listen","127.0.0.1"); addProperty("protocol","http")
            add("settings", jo { addProperty("allowTransparent",false) })
            add("sniffing", sniff())
        })
        add(jo {  // DNS inbound for FakeDNS hijacking
            addProperty("tag","dns-in"); addProperty("port",5353)
            addProperty("listen","127.0.0.1"); addProperty("protocol","dokodemo-door")
            add("settings", jo {
                addProperty("address","1.1.1.1"); addProperty("port",53)
                addProperty("network","tcp,udp"); addProperty("followRedirect",false)
            })
        })
    }

    private fun sniff() = jo {
        addProperty("enabled",true); addProperty("metadataOnly",false)
        add("destOverride", ja { add("http"); add("tls"); add("quic"); add("fakedns") })
        addProperty("routeOnly",false)
    }

    private fun outbounds(p: ConfigProfile, psiphonPort: Int) = ja {
        add(proxyOut(p))           // proxy   — chains through psiphon-out
        add(psiphonOut(psiphonPort)) // psiphon-out — Psiphon SOCKS5
        add(dnsOut())               // dns-out
        // NO "direct" outbound — all traffic forced through Psiphon
        add(blockOut())             // block — safety catch-all
    }

    private fun proxyOut(p: ConfigProfile) = jo {
        addProperty("tag","proxy")
        setProtocol(this, p)
        add("streamSettings", stream(p))
        // CRITICAL: force ALL proxy traffic through Psiphon SOCKS5
        add("proxySettings", jo {
            addProperty("tag","psiphon-out")
            addProperty("transportLayer",true)
        })
        add("mux", jo {
            val ok = p.network !in listOf("grpc","quic","kcp")
            addProperty("enabled",ok); addProperty("concurrency", if(ok) 8 else -1)
        })
    }

    private fun psiphonOut(port: Int) = jo {
        addProperty("tag","psiphon-out"); addProperty("protocol","socks")
        add("settings", jo {
            add("servers", ja { add(jo { addProperty("address","127.0.0.1"); addProperty("port",port) }) })
        })
    }

    private fun dnsOut()   = jo { addProperty("tag","dns-out"); addProperty("protocol","dns"); add("settings",JsonObject()) }
    private fun blockOut() = jo {
        addProperty("tag","block"); addProperty("protocol","blackhole")
        add("settings", jo { add("response", jo { addProperty("type","http") }) })
    }

    private fun setProtocol(obj: JsonObject, p: ConfigProfile) {
        when (p.protocol) {
            Protocol.VMESS -> {
                obj.addProperty("protocol","vmess")
                obj.add("settings", jo { add("vnext", ja { add(jo {
                    addProperty("address",p.address); addProperty("port",p.port)
                    add("users", ja { add(jo {
                        addProperty("id",p.uuid); addProperty("alterId",p.alterId)
                        addProperty("security",p.encryption.ifBlank{"auto"})
                    })})
                })})})
            }
            Protocol.VLESS -> {
                obj.addProperty("protocol","vless")
                obj.add("settings", jo { add("vnext", ja { add(jo {
                    addProperty("address",p.address); addProperty("port",p.port)
                    add("users", ja { add(jo {
                        addProperty("id",p.uuid); addProperty("encryption","none")
                        if (p.flow.isNotBlank()) addProperty("flow",p.flow)
                    })})
                })})})
            }
            Protocol.TROJAN -> {
                obj.addProperty("protocol","trojan")
                obj.add("settings", jo { add("servers", ja { add(jo {
                    addProperty("address",p.address); addProperty("port",p.port)
                    addProperty("password",p.password)
                    if (p.flow.isNotBlank()) addProperty("flow",p.flow)
                })})})
            }
            Protocol.SHADOWSOCKS -> {
                obj.addProperty("protocol","shadowsocks")
                obj.add("settings", jo { add("servers", ja { add(jo {
                    addProperty("address",p.address); addProperty("port",p.port)
                    addProperty("method",p.encryption); addProperty("password",p.password)
                    addProperty("uot",true)
                })})})
            }
            Protocol.SOCKS5 -> {
                obj.addProperty("protocol","socks")
                obj.add("settings", jo { add("servers", ja { add(jo {
                    addProperty("address",p.address); addProperty("port",p.port)
                    if (p.uuid.isNotBlank()||p.password.isNotBlank())
                        add("users", ja { add(jo { addProperty("user",p.uuid); addProperty("pass",p.password) }) })
                })})})
            }
            Protocol.HTTP -> {
                obj.addProperty("protocol","http")
                obj.add("settings", jo { add("servers", ja { add(jo {
                    addProperty("address",p.address); addProperty("port",p.port)
                    if (p.uuid.isNotBlank()||p.password.isNotBlank())
                        add("users", ja { add(jo { addProperty("user",p.uuid); addProperty("pass",p.password) }) })
                })})})
            }
        }
    }

    private fun stream(p: ConfigProfile) = jo {
        addProperty("network",p.network)
        when (p.security.lowercase()) {
            "tls" -> {
                addProperty("security","tls")
                add("tlsSettings", jo {
                    addProperty("serverName",p.sni.ifBlank{p.address})
                    addProperty("allowInsecure",false)
                    addProperty("fingerprint",p.fingerprint.ifBlank{"chrome"})
                    add("alpn", ja { if(p.network in listOf("h2","grpc")) add("h2") else { add("h2"); add("http/1.1") } })
                })
            }
            "reality" -> {
                addProperty("security","reality")
                add("realitySettings", jo {
                    addProperty("serverName",p.sni); addProperty("fingerprint",p.fingerprint.ifBlank{"chrome"})
                    addProperty("shortId",p.shortId); addProperty("publicKey",p.publicKey)
                    addProperty("spiderX",p.spiderX.ifBlank{"/"}); addProperty("show",false)
                })
            }
            else -> addProperty("security","none")
        }
        when (p.network.lowercase()) {
            "ws"          -> add("wsSettings", jo { addProperty("path",p.path.ifBlank{"/"})
                                add("headers",jo { if(p.host.isNotBlank()) addProperty("Host",p.host) }) })
            "grpc"        -> add("grpcSettings", jo { addProperty("serviceName",p.grpcServiceName)
                                addProperty("multiMode",false); addProperty("idle_timeout",60) })
            "h2","http"   -> add("httpSettings", jo { addProperty("path",p.path.ifBlank{"/"})
                                add("host", ja { if(p.host.isNotBlank()) add(p.host) }) })
            "httpupgrade" -> add("httpupgradeSettings", jo { addProperty("path",p.path.ifBlank{"/"})
                                if(p.host.isNotBlank()) addProperty("host",p.host) })
            "quic"        -> add("quicSettings", jo { addProperty("security","none"); addProperty("key","")
                                add("header",jo{addProperty("type","none")}) })
            "kcp"         -> add("kcpSettings", jo {
                                addProperty("mtu",1350); addProperty("tti",50)
                                addProperty("uplinkCapacity",12); addProperty("downlinkCapacity",100)
                                addProperty("congestion",false); addProperty("readBufferSize",2)
                                addProperty("writeBufferSize",2)
                                add("header",jo{addProperty("type","none")})
                            })
        }
    }

    private fun routing() = jo {
        addProperty("domainStrategy","IPIfNonMatch"); addProperty("domainMatcher","hybrid")
        add("rules", ja {
            // DNS inbound → dns-out (Xray handles DNS, routes via Psiphon)
            add(jo { addProperty("type","field"); addProperty("inboundTag","dns-in"); addProperty("outboundTag","dns-out") })
            // FakeDNS virtual IPs → proxy
            add(jo { addProperty("type","field"); add("ip",ja{add("198.18.0.0/15")}); addProperty("outboundTag","proxy") })
            // Ads → block
            add(jo { addProperty("type","field"); add("domain",ja{add("geosite:category-ads-all")}); addProperty("outboundTag","block") })
            // Private/loopback → proxy (Psiphon handles; avoids routing conflicts)
            add(jo { addProperty("type","field")
                add("ip",ja{add("127.0.0.0/8");add("::1/128");add("10.0.0.0/8");add("172.16.0.0/12");add("192.168.0.0/16")})
                addProperty("outboundTag","proxy") })
            // ALL remaining → proxy (through Psiphon — no direct fallback ever)
            add(jo { addProperty("type","field")
                add("network",ja{add("tcp");add("udp")}); addProperty("outboundTag","proxy") })
        })
    }

    private fun policy() = jo {
        add("levels", jo { add("0", jo {
            addProperty("handshake",4); addProperty("connIdle",300)
            addProperty("uplinkOnly",1); addProperty("downlinkOnly",1)
            addProperty("bufferSize",10240)
            addProperty("statsUserUplink",true); addProperty("statsUserDownlink",true)
        })})
        add("system", jo {
            addProperty("statsInboundUplink",true); addProperty("statsInboundDownlink",true)
            addProperty("statsOutboundUplink",true); addProperty("statsOutboundDownlink",true)
        })
    }

    private fun jo(init: JsonObject.() -> Unit = {}) = JsonObject().also(init)
    private fun ja(init: JsonArray.() -> Unit = {})  = JsonArray().also(init)
}
