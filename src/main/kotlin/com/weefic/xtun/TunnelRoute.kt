package com.weefic.xtun

import java.net.InetSocketAddress

class TunnelRoute(private val config: TunnelConfig) {
    private val portToInboundName = this.config.inbound.associate { config ->
        config.port to config.id
    }
    private val inboundNameToConfig = this.config.inbound.associateBy { it.id }
    private val outboundNameToConfig = this.config.outbound.associateBy { it.id }
    private val outboundMapping = this.config.route.associate { route ->
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
    }

    fun getInboundConfig(port: Int): TunnelInboundConfig? {
        val inboundName = this.portToInboundName[port] ?: return null
        return this.inboundNameToConfig[inboundName]
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
            return this.outboundNameToConfig[outboundName]
        }
        return null
    }
}