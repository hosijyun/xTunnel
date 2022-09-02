package com.weefic.xtun

import java.net.InetSocketAddress

class TunnelRoute(private val config: TunnelConfig) {
    private val portToInboundName = this.config.inbound.map { entry ->
        entry.value.port to entry.key
    }.toMap()
    private val outboundMapping = this.config.route.map { route ->
        val clientAddress = route.clientAddress
        val user = route.user
        val key = if (clientAddress != null && user != null) {
            "${route.inbound}-$clientAddress-$user"
        } else if (clientAddress != null) {
            "${route.inbound}-$clientAddress"
        } else if (user != null) {
            "${route.inbound}-*-$user"
        } else {
            "${route.inbound}-*"
        }
        key to route.outbound
    }.toMap()

    fun getInboundConfig(port: Int): TunnelInboundConfig? {
        val inboundName = this.portToInboundName[port] ?: return null
        return this.config.inbound[inboundName]
    }

    fun getOutboundConfig(localAddress: InetSocketAddress, clientAddress: InetSocketAddress, user: String?): TunnelOutboundConfig? {
        val clientAddress = clientAddress.hostString
        val localPort = localAddress.port
        val inboundName = this.portToInboundName[localPort] ?: return null
        var outboundName: String? = null
        if (user != null) {
            outboundName = this.outboundMapping["$inboundName-$clientAddress-$user"] ?: this.outboundMapping["$inboundName-*-$user"]
        }
        if (outboundName == null) {
            outboundName = this.outboundMapping["$inboundName-$clientAddress"] ?: this.outboundMapping["$inboundName-*"]
        }
        if (outboundName != null) {
            return this.config.outbound[outboundName]
        }
        return null
    }
}