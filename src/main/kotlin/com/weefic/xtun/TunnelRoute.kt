package com.weefic.xtun

import com.maxmind.geoip2.DatabaseReader
import com.weefic.xtun.utils.matchWildcard
import io.netty.resolver.dns.DnsNameResolver
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class RouterMatchingResult(
    val outbound: TunnelOutboundConfig,
    val targetAddress: InetSocketAddress
)

private class TunnelOutboundRoute {
    private val always: Boolean
    private val users: Set<String>?
    private val clientAddresses: Set<String>?
    private val serverAddresses: Set<String>?
    private val redirect: List<Pair<String, String>>?
    private val pac: PAC?
    private val geoip: DatabaseReader?
    private val countries: Set<String>?
    private val outboundConfig: TunnelOutboundConfig
    private val dnsResolver: DnsNameResolver?

    companion object {
        private val DNS_CACHE = WeakHashMap<String, InetAddress>()
        private val DNS_CACHE_LOCK = ReentrantReadWriteLock()
    }

    constructor(outboundConfig: TunnelOutboundConfig = TunnelOutboundConfig.Reject()) {
        this.always = true
        this.users = null
        this.clientAddresses = null
        this.serverAddresses = null
        this.outboundConfig = outboundConfig
        this.pac = null
        this.geoip = null
        this.countries = null
        this.redirect = null
        this.dnsResolver = null
    }

    constructor(
        routeConfig: TunnelRouteConfig,
        outboundConfigs: Map<String, TunnelOutboundConfig>,
        pacs: Map<String, PAC>?,
        geoip: DatabaseReader?,
        dnsResolver: DnsNameResolver?,
    ) {
        this.outboundConfig = outboundConfigs[routeConfig.outbound] ?: TunnelOutboundConfig.Reject()
        this.geoip = geoip
        val match = routeConfig.match
        this.users = match.users.toHashSet()
        this.clientAddresses = match.clientAddresses.map { it.lowercase() }.toHashSet()
        this.serverAddresses = match.serverAddresses.map { it.lowercase() }.toHashSet()
        this.pac = match.pac?.let { pacs?.get(it) }
        this.countries = match.countries.map { it.uppercase() }.toHashSet()
        this.always = this.users.isEmpty() && this.clientAddresses.isEmpty()
                && this.serverAddresses.isEmpty() && this.pac == null && this.countries.isEmpty()
        this.redirect = routeConfig.redirect.map {
            it.from.lowercase() to it.to.lowercase()
        }
        this.dnsResolver = dnsResolver
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

    private fun matchAny(host: String, port: Int, addresses: Set<String>): Boolean {
        for (address in addresses) {
            val colon = address.lastIndexOf(':')
            if (colon == -1) {
                // Any port
                if (host.matchWildcard(address)) {
                    return true
                }
            } else {
                if ("$host:$port".matchWildcard(address)) {
                    return true
                }
            }
        }
        return false
    }


    fun match(
        user: String?,
        clientAddress: InetSocketAddress,
        targetAddress: InetSocketAddress
    ): RouterMatchingResult? {
        val outboundConfig = this.outboundConfig
        if (this.always) { // Fast match
            return RouterMatchingResult(outboundConfig, this.matchRedirection(targetAddress))
        }
        if (!this.users.isNullOrEmpty() && !this.users.contains(user)) {
            return null
        }
        if (!this.clientAddresses.isNullOrEmpty()) {
            val host = clientAddress.hostString.lowercase()
            val port = clientAddress.port
            if (!matchAny(host, port, this.clientAddresses)) {
                return null
            }
        }
        if (!this.serverAddresses.isNullOrEmpty()) {
            val host = targetAddress.hostString.lowercase()
            val port = targetAddress.port
            if (!matchAny(host, port, this.serverAddresses)) {
                return null
            }
        }
        if (this.pac != null) {
            if (!this.pac.accept(targetAddress)) {
                return null
            }
        }
        if (!this.countries.isNullOrEmpty()) {
            val geoip = this.geoip
            if (geoip == null) {
                return null
            } else {
                var address: InetAddress? = targetAddress.address
                val hostString: String? = targetAddress.hostString
                if (address == null) {
                    if (hostString == null) {
                        return null
                    }
                    address = DNS_CACHE_LOCK.read {
                        DNS_CACHE[hostString]
                    }
                    if (address == null) {
                        if (hostString.endsWith(".local.")) {
                            return null
                        }
                        val resolver = this.dnsResolver
                        val address = if (resolver != null) {
                            try {
                                resolver.resolve(hostString).sync().get()
                            } catch (e: Exception) {
                                // UnknownHostException?
                                return null
                            }
                        } else {
                            try {
                                InetAddress.getByName(hostString)
                            } catch (_: Exception) {
                                // UnknownHostException?
                                return null
                            }
                        }
                        DNS_CACHE_LOCK.write {
                            DNS_CACHE[hostString] = address
                        }
                    }
                }
                try {
                    val response = geoip.city(address)
                    if (!this.countries.contains(response.country.isoCode.uppercase())) {
                        return null
                    }
                } catch (e: Exception) {
                    // ??
                    return null
                }
            }
        }
        return RouterMatchingResult(outboundConfig, this.matchRedirection(targetAddress))
    }
}

class TunnelRoute(
    private val config: TunnelConfig,
    val pac: Map<String, PAC>?,
    val dnsResolver: DnsNameResolver?,
) {
    private val portToInboundConfig = this.config.proxies.map { it.value.port to (it.key to it.value) }.toMap()
    private val routing: Map<Int, List<TunnelOutboundRoute>>
    val geoip = this.config.assets?.geoip?.let { DatabaseReader.Builder(File(it)).build() }

    init {
        val outboundIdToOutboundConfig = (this.config.outbound ?: mutableMapOf()).toMutableMap()
        if (!outboundIdToOutboundConfig.contains("direct")) {
            outboundIdToOutboundConfig["direct"] = TunnelOutboundConfig.Direct()
        }
        if (!outboundIdToOutboundConfig.contains("reject")) {
            outboundIdToOutboundConfig["reject"] = TunnelOutboundConfig.Reject()
        }
        if (!outboundIdToOutboundConfig.contains("default")) {
            outboundIdToOutboundConfig["default"] = TunnelOutboundConfig.Direct()
        }
        this.routing = this.config.proxies.values.associate {
            val rule = if (it.useDefaultRules) it.rule + this.config.defaultRules else it.rule
            if (rule.isEmpty()) {
                val defaultRoute = TunnelRouteConfig(outbound = "default")
                it.port to listOf(TunnelOutboundRoute(defaultRoute, outboundIdToOutboundConfig, pac, geoip, dnsResolver))
            } else {
                it.port to rule.map { route ->
                    TunnelOutboundRoute(route, outboundIdToOutboundConfig, pac, geoip, dnsResolver)
                }
            }
        }
    }


    fun getInboundConfig(port: Int): Pair<String, TunnelInboundConfig>? {
        return this.portToInboundConfig[port]
    }

    fun getOutboundConfig(
        localAddress: InetSocketAddress,
        clientAddress: InetSocketAddress,
        targetAddress: InetSocketAddress,
        user: String?
    ): RouterMatchingResult? {
        return this.routing[localAddress.port]?.firstNotNullOfOrNull { it.match(user, clientAddress, targetAddress) }
    }
}