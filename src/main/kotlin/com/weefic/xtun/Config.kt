package com.weefic.xtun

import java.net.InetSocketAddress


data class UserCredential(val user: String, val password: String)

sealed class TunnelInboundConfig(val port: Int) {
    class Http(port: Int, val credential: UserCredential? = null) : TunnelInboundConfig(port)
    class Socks5(port: Int, val credential: UserCredential? = null) : TunnelInboundConfig(port)
}

sealed class TunnelOutboundConfig() {
    abstract fun getServerAddress(host: String, port: Int): InetSocketAddress

    object Direct : TunnelOutboundConfig() {
        override fun getServerAddress(host: String, port: Int): InetSocketAddress {
            return InetSocketAddress(host, port)
        }
    }

    data class Http(val host: String, val port: Int, val credential: UserCredential? = null) : TunnelOutboundConfig() {
        override fun getServerAddress(host: String, port: Int): InetSocketAddress {
            return InetSocketAddress(this.host, this.port)
        }
    }

    data class Socks5(val host: String, val port: Int, val credential: UserCredential? = null) : TunnelOutboundConfig() {
        override fun getServerAddress(host: String, port: Int): InetSocketAddress {
            return InetSocketAddress(this.host, this.port)
        }
    }
}

class TunnelConfig(
    val inbound: TunnelInboundConfig,
    val outbound: TunnelOutboundConfig,
)


class Config(
    val tunnels: List<TunnelConfig>
)