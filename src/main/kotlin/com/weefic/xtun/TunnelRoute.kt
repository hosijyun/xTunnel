package com.weefic.xtun

import java.net.InetSocketAddress

private class TunnelOutboundRoute(
    routeConfig: TunnelRouteConfig,
    outboundConfigs: Map<String, TunnelOutboundConfig>,
    pacs: Map<String, PAC>?
) {
    private val always: Boolean
    private val user: String?
    private val clientAddresses: Set<String>?
    private val serverAddresses: Set<String>?
    private val outboundConfig = outboundConfigs[routeConfig.outbound]
    private val pac: PAC?

    init {
        val match = routeConfig.match
        if (match != null) {
            this.always = false
            this.user = match.user
            this.clientAddresses = match.clientAddresses?.map { it.lowercase() }?.toHashSet()
            this.serverAddresses = match.serverAddresses?.map { it.lowercase() }?.toHashSet()
            this.pac = match.pac?.let { pacs?.get(it) }
        } else {
            this.always = true
            this.user = null
            this.clientAddresses = null
            this.serverAddresses = null
            this.pac = null
        }
    }

    fun match(user: String?, clientAddress: InetSocketAddress, targetAddress: InetSocketAddress): TunnelOutboundConfig? {
        if (this.always) { // Fast match
            return this.outboundConfig
        }
        if (this.user != null && this.user != user) {
            return null
        }
        if (this.clientAddresses != null) {
            val host = clientAddress.hostString.lowercase()
            val port = clientAddress.port
            if (!this.clientAddresses.contains("$host:$port") && !this.clientAddresses.contains("$host:*")) {
                return null
            }
        }
        if (this.serverAddresses != null) {
            val host = targetAddress.hostString.lowercase()
            val port = targetAddress.port
            if (!this.serverAddresses.contains("$host:$port") && !this.serverAddresses.contains("$host:*")) {
                return null
            }
        }
        if (this.pac != null) {
            if (!this.pac.accept(targetAddress)) {
                return null
            }
        }
        return this.outboundConfig
    }
}

class TunnelRoute(private val config: TunnelConfig, val pac: Map<String, PAC>?) {
    private val portToInboundConfig = this.config.inbound.associateBy { it.port }
    private val routing: Map<Int, List<TunnelOutboundRoute>>

    init {
        val outboundIdToOutboundConfig = this.config.outbound.associateBy { it.id }
        val inboundIdToInboundPort = this.config.inbound.associate { it.id to it.port }
        val routing = mutableMapOf<Int, MutableList<TunnelOutboundRoute>>()
        this.config.route.forEach { route ->
            val port = inboundIdToInboundPort[route.inbound]
            if (port != null) {
                val outboundRoute = TunnelOutboundRoute(route, outboundIdToOutboundConfig, pac)
                routing.getOrPut(port) { mutableListOf() }.add(outboundRoute)
            }
        }
        this.routing = routing
    }


    fun getInboundConfig(port: Int): TunnelInboundConfig? {
        return this.portToInboundConfig[port]
    }

    fun getOutboundConfig(localAddress: InetSocketAddress, clientAddress: InetSocketAddress, targetAddress: InetSocketAddress, user: String?): TunnelOutboundConfig? {
        return this.routing[localAddress.port]?.firstNotNullOfOrNull { it.match(user, clientAddress, targetAddress) }
    }
}