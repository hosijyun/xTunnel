package com.weefic.xtun

import com.weefic.xtun.handlers.InboundHttpRequestDecoder
import com.weefic.xtun.inbound.*
import com.weefic.xtun.shadowsocks.ShadowSocksHostDecoder
import com.weefic.xtun.shadowsocks.config
import com.weefic.xtun.trojan.TrojanHeaderInboundHandler
import com.weefic.xtun.web.WebConfig
import com.weefic.xtun.web.WebServerHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.stream.ChunkedWriteHandler


class ClientChannelInitializer(val route: TunnelRoute, val webConfig: WebConfig?, val tlsConfigs: List<TlsConfig>?, val bossGroup: EventLoopGroup) : ChannelInitializer<SocketChannel>() {
    companion object {
        val LOG = XTUN_LOGGER
    }

    override fun initChannel(clientChannel: SocketChannel) {
        val clientAddress = clientChannel.remoteAddress().address.hostAddress
        val tunnelPort = clientChannel.localAddress().port
        val webConfig = this.webConfig
        if (webConfig != null && webConfig.port == tunnelPort) {
            LOG.info("Start handling web request from client : {}", clientAddress)
            val pipeline = clientChannel.pipeline()
            pipeline.addLast(HttpRequestDecoder())
            pipeline.addLast(HttpObjectAggregator(65536))
            pipeline.addLast(HttpResponseEncoder())
            pipeline.addLast(ChunkedWriteHandler())
            pipeline.addLast(WebServerHandler(webConfig, this.bossGroup))
        } else {
            val inboundConfig = this.route.getInboundConfig(tunnelPort)
            LOG.info("New client connection from : {}", clientAddress)
            if (inboundConfig == null) {
                // WHY??
                LOG.error("Connection from {} is rejected. Reason : No inbound config found for port {}.", clientAddress, tunnelPort)
                clientChannel.close()
                return
            }
            val (inboundName, inbound) = inboundConfig
            val tunnel = Tunnel(inboundName, clientChannel, this.route)
            LOG.info(tunnel.LOG_TAG, "Accept client connection from {} with inbound proxy protocol {}", clientAddress, inbound.javaClass.simpleName)

            val pipeline = clientChannel.pipeline()
            pipeline.addLast(ClientConnectionLoggerHandler(tunnel))

            val tls = inbound.tls
            if (!tls.isNullOrEmpty()) {
                val tlsConfig = this.tlsConfigs?.firstOrNull { it.id == tls }
                if (tlsConfig == null) {
                    // Config error!!
                    LOG.error(tunnel.LOG_TAG, "No tls config found for '{}'.", tls)
                    clientChannel.close()
                    return
                }
                val sslContext: SslContext = SslContextBuilder
                    .forServer(tlsConfig.key, tlsConfig.certificate)
                    .clientAuth(ClientAuth.NONE)
                    .build()
                pipeline.addLast("ssl", sslContext.newHandler(clientChannel.alloc()));
            }
            when (inbound) {
                is TunnelInboundConfig.Mix -> {
                    pipeline.addLast(ClientConnectionMixInboundHandler(tunnel.connectionId, inbound.users))
                    pipeline.addLast(ClientConnection.NAME, tunnel.clientConnection)
                }

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

                is TunnelInboundConfig.Trojan -> {
                    pipeline.addLast(TrojanHeaderInboundHandler.HANDLER_NAME, TrojanHeaderInboundHandler(tunnel.connectionId, inbound.passwords))
                    pipeline.addLast(tunnel.clientConnection)
                }

                is TunnelInboundConfig.VMess -> {
                    pipeline.addLast(VMessRequestHeaderDecoder.NAME, VMessRequestHeaderDecoder(tunnel.connectionId, listOf()))
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
}