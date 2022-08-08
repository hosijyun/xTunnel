package com.weefic.xtun

import com.weefic.xtun.handlers.InboundHttpRequestDecoder
import com.weefic.xtun.inbound.ClientConnectionHttpProxyInboundHandler
import com.weefic.xtun.inbound.ClientConnectionSocks5InboundHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpRequestDecoder
import org.slf4j.LoggerFactory

class ClientChannelInitializer(val config: Config) : ChannelInitializer<SocketChannel>() {
    companion object {
        val LOG = LoggerFactory.getLogger("Client-Initializer")
    }

    override fun initChannel(clientChannel: SocketChannel) {
        val port = clientChannel.localAddress().port
        val tunnelConfig = this.config.tunnels.firstOrNull { it.inbound.port == port }
        if (tunnelConfig == null) {
            clientChannel.close()
            return
        }
        val pipeline = clientChannel.pipeline()
        val tunnel = Tunnel(tunnelConfig, clientChannel)
        when (tunnelConfig.inbound) {
            is TunnelInboundConfig.Http -> {
                pipeline.addLast(ClientConnectionHttpProxyInboundHandler.HTTP_DECODER_NAME, HttpRequestDecoder())
                pipeline.addLast(ClientConnectionHttpProxyInboundHandler(tunnel.connectionId, tunnelConfig.inbound.credential))
                pipeline.addLast(ClientConnectionHttpProxyInboundHandler.HTTP_ENCODER_NAME, InboundHttpRequestDecoder())
                pipeline.addLast(tunnel.clientConnection)
            }
            is TunnelInboundConfig.Socks5 -> {
                pipeline.addLast(ClientConnectionSocks5InboundHandler(tunnel.connectionId, tunnelConfig.inbound.credential))
                pipeline.addLast(tunnel.clientConnection)
            }
        }
    }
}