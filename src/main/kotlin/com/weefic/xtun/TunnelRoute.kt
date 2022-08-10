package com.weefic.xtun

class TunnelRoute(private val config: TunnelConfig) {
    private val inboundPortToName = this.config.inbound.map { entry ->
        entry.value.port to entry.key
    }.toMap()
    private val inToOut = this.config.route.map {
        val clientAddress = it.clientAddress
        if (clientAddress == null) {
            it.inbound to it.outbound
        } else {
            "${it.inbound}-$clientAddress" to it.outbound
        }
    }.toMap()


    fun route(port: Int, clientAddress: String): Pair<TunnelInboundConfig, TunnelOutboundConfig>? {
        val inboundName = this.inboundPortToName[port] ?: return null
        val outboundName = this.inToOut["$inboundName-$clientAddress"] ?: this.inToOut[inboundName] ?: return null

        val inbound = this.config.inbound[inboundName] ?: return null
        val outbound = this.config.outbound[outboundName] ?: return null
        return inbound to outbound
    }
}