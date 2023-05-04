package com.weefic.xtun

import java.net.InetSocketAddress

class RouterMatchingResult(
    val outbound: TunnelOutboundConfig,
    val targetAddress: InetSocketAddress
)

private class TunnelOutboundRoute(
    routeConfig: TunnelRouteConfig,
    outboundConfigs: Map<String, TunnelOutboundConfig>,
    pacs: Map<String, PAC>?
) {
    private val always: Boolean
    private val user: String?
    private val clientAddresses: Set<String>?
    private val serverAddresses: Set<String>?
    private val redirect: List<Pair<String, String>>?
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
        this.redirect = routeConfig.redirect?.map {
            it.from.lowercase() to it.to.lowercase()
        }
    }

    private fun matchRedirection(targetAddress: InetSocketAddress): InetSocketAddress {
        var target = targetAddress
        if (this.redirect != null) {
            val targetHost = targetAddress.hostString.lowercase()
            val targetPort = targetAddress.port.toString()
            val redirect = this.redirect.firstOrNull {
                val from = it.first
                val colonIndex = from.lastIndexOf(':')
                val fromServer: String
                val fromPort: String
                if (colonIndex == -1) {
                    fromServer = from
                    fromPort = "*"
                } else {
                    fromServer = from.substring(0, colonIndex)
                    fromPort = from.substring(colonIndex + 1)
                }
                (fromServer == "*" || fromServer == targetHost) && (fromPort == "*" || fromPort == targetPort)
            }
            if (redirect != null) {
                val to = redirect.second
                val colonIndex = to.lastIndexOf(':')
                val toServer: String
                val toPort: String
                if (colonIndex == -1) {
                    toServer = to
                    toPort = "*"
                } else {
                    toServer = to.substring(0, colonIndex)
                    toPort = to.substring(colonIndex + 1)
                }
                val redirectToServer: String = if (toServer == "*") targetHost else toServer
                val redirectToPort: Int = if (toPort == "*") targetAddress.port else toPort.toIntOrNull() ?: targetAddress.port
                target = InetSocketAddress.createUnresolved(redirectToServer, redirectToPort)
            }
        }
        return target
    }

    fun match(user: String?, clientAddress: InetSocketAddress, targetAddress: InetSocketAddress): RouterMatchingResult? {
        val outboundConfig = this.outboundConfig ?: return null
        if (this.always) { // Fast match
            return RouterMatchingResult(outboundConfig, this.matchRedirection(targetAddress))
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
        return RouterMatchingResult(outboundConfig, this.matchRedirection(targetAddress))
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

    fun getOutboundConfig(localAddress: InetSocketAddress, clientAddress: InetSocketAddress, targetAddress: InetSocketAddress, user: String?): RouterMatchingResult? {
        return this.routing[localAddress.port]?.firstNotNullOfOrNull { it.match(user, clientAddress, targetAddress) }
    }
}