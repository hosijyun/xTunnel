package com.weefic.xtun

import com.weefic.xtun.handlers.InboundHttpRequestDecoder
import com.weefic.xtun.inbound.*
import com.weefic.xtun.shadowsocks.ShadowSocksHostDecoder
import com.weefic.xtun.shadowsocks.config
import com.weefic.xtun.web.WebConfig
import com.weefic.xtun.web.WebServerHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.stream.ChunkedWriteHandler
import org.slf4j.LoggerFactory
import java.io.File


class ClientChannelInitializer(val route: TunnelRoute, val webConfig: WebConfig?, val tlsConfig: TlsConfig?, val bossGroup: NioEventLoopGroup) : ChannelInitializer<SocketChannel>() {
    companion object {
        val LOG = LoggerFactory.getLogger("Client-Initializer")
    }

    override fun initChannel(clientChannel: SocketChannel) {
        val clientAddress = clientChannel.remoteAddress().address.hostAddress
        LOG.info("New client connection from : {}", clientAddress)
        val tunnelPort = clientChannel.localAddress().port
        val webConfig = this.webConfig
        if (webConfig != null && webConfig.port == tunnelPort) {
            val pipeline = clientChannel.pipeline()
            pipeline.addLast(HttpRequestDecoder())
            pipeline.addLast(HttpObjectAggregator(65536))
            pipeline.addLast(HttpResponseEncoder())
            pipeline.addLast(ChunkedWriteHandler())
            pipeline.addLast(WebServerHandler(webConfig, this.bossGroup))
        } else {
            val inbound = this.route.getInboundConfig(tunnelPort)
            if (inbound == null) {
                LOG.info("Connection from {} is rejected. Reason : No inbound config found", clientAddress)
                clientChannel.close()
                return
            } else {
                LOG.info("Accept client connection from {}. Using protocol {}", clientAddress, inbound.javaClass.simpleName)
            }
            val pipeline = clientChannel.pipeline()
            val tunnel = Tunnel(clientChannel, this.route)

            pipeline.addLast(ClientConnectionLoggerHandler(tunnel.connectionId))
            val tls = inbound.tls
            if (tls != null) {
                val keyPair = tlsConfig?.keyPairs?.firstOrNull { it.id == tls }
                if (keyPair == null) {
                    LOG.info("No key found for '{}'", tls)
                    clientChannel.close()
                    return
                }
                val sslContext: SslContext = SslContextBuilder.forServer(File(keyPair.certificate), File(keyPair.keyPath)).build()
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