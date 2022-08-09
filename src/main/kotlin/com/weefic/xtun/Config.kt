package com.weefic.xtun

import io.netty.channel.Channel
import java.net.InetSocketAddress


data class UserCredential(val user: String, val password: String)

interface DynamicRoute {
    fun route(channel: Channel): InetSocketAddress?
}

sealed class TunnelInboundConfig(val port: Int) {
    class Http(port: Int, val credential: UserCredential? = null) : TunnelInboundConfig(port)
    class Socks5(port: Int, val credential: UserCredential? = null) : TunnelInboundConfig(port)
    class Dynamic(port: Int, val router: DynamicRoute) : TunnelInboundConfig(port)
}

sealed class TunnelOutboundConfig() {
    object Direct : TunnelOutboundConfig()
    data class Http(val host: String, val port: Int, val credential: UserCredential? = null) : TunnelOutboundConfig()
    data class Socks5(val host: String, val port: Int, val credential: UserCredential? = null) : TunnelOutboundConfig()
}

class TunnelConfig(
    val inbound: TunnelInboundConfig,
    val outbound: TunnelOutboundConfig,
)


class Config(
    val tunnels: List<TunnelConfig>
)