package com.weefic.xtun


data class UserCredential(val user: String, val password: String)


sealed class TunnelInboundConfig(val port: Int) {
    class Http(port: Int, val credentials: List<UserCredential>? = null) : TunnelInboundConfig(port)
    class Socks5(port: Int, val credentials: List<UserCredential>? = null) : TunnelInboundConfig(port)
}

sealed class TunnelOutboundConfig() {
    object Direct : TunnelOutboundConfig()
    object Blackhole : TunnelOutboundConfig()
    object Echo : TunnelOutboundConfig()
    class Http(val host: String, val port: Int, val credential: UserCredential? = null) : TunnelOutboundConfig()
    class Socks5(val host: String, val port: Int, val credential: UserCredential? = null) : TunnelOutboundConfig()
}

class TunnelRouteConfig(
    val inbound: String,
    val outbound: String,
    val clientAddress: String? = null,
    val user: String? = null,
)

class TunnelConfig(
    val route: List<TunnelRouteConfig>,
    val inbound: Map<String, TunnelInboundConfig>,
    val outbound: Map<String, TunnelOutboundConfig>,
)