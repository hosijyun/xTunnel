package com.weefic.xtun

import com.weefic.xtun.handlers.InboundHttpRequestDecoder
import com.weefic.xtun.inbound.*
import com.weefic.xtun.shadowsocks.ShadowSocksHostDecoder
import com.weefic.xtun.shadowsocks.config
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpRequestDecoder
import org.slf4j.LoggerFactory

class ClientChannelInitializer(val route: TunnelRoute) : ChannelInitializer<SocketChannel>() {
    companion object {
        val LOG = LoggerFactory.getLogger("Client-Initializer")
    }

    override fun initChannel(clientChannel: SocketChannel) {
        val clientAddress = clientChannel.remoteAddress().address.hostAddress
        val tunnelPort = clientChannel.localAddress().port
        val inbound = this.route.getInboundConfig(tunnelPort)
        if (inbound == null) {
            clientChannel.close()
            return
        }
        val pipeline = clientChannel.pipeline()
        val tunnel = Tunnel(clientChannel, this.route)

        pipeline.addLast(ClientConnectionLoggerHandler(tunnel.connectionId))
        when (inbound) {
            is TunnelInboundConfig.Http -> {
                pipeline.addLast(ClientConnectionHttpProxyInboundHandler.HTTP_DECODER_NAME, HttpRequestDecoder())
                pipeline.addLast(ClientConnectionHttpProxyInboundHandler(tunnel.connectionId, inbound.users))
                pipeline.addLast(ClientConnectionHttpProxyInboundHandler.HTTP_ENCODER_NAME, InboundHttpRequestDecoder())
                pipeline.addLast(tunnel.clientConnection)
            }

            is TunnelInboundConfig.Socks5 -> {
                pipeline.addLast(ClientConnectionSocks5InboundHandler(tunnel.connectionId, inbound.users))
                pipeline.addLast(tunnel.clientConnection)
            }

            is TunnelInboundConfig.Shadowsocks -> {
                inbound.method.config(pipeline, inbound.password)
                pipeline.addLast(ShadowSocksHostDecoder())
                pipeline.addLast(tunnel.clientConnection)
            }

            is TunnelInboundConfig.NAT -> {
                pipeline.addLast(
                    ClientConnectionNATInboundHandler(
                        tunnel.connectionId,
                        inbound.serverHost,
                        inbound.serverPort
                    )
                )
                pipeline.addLast(tunnel.clientConnection)
            }

            is TunnelInboundConfig.MTProto -> {
                pipeline.addLast(
                    ClientConnectionMTProtoInboundHandler(
                        tunnel.connectionId,
                        inbound.secret,
                    )
                )
                pipeline.addLast(tunnel.clientConnection)
            }
        }
    }
}