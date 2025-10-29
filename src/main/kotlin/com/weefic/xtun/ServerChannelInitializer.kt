package com.weefic.xtun

import com.weefic.xtun.handlers.ConnectRequestHttpResponseDecoder
import com.weefic.xtun.handlers.PureHttpRequestEncoder
import com.weefic.xtun.outbound.ServerConnectionDirectInboundHandler
import com.weefic.xtun.outbound.ServerConnectionHttpProxyInboundHandler
import com.weefic.xtun.outbound.ServerConnectionSocks5InboundHandler
import com.weefic.xtun.shadowsocks.ShadowSocksHostEncoder
import com.weefic.xtun.shadowsocks.config
import com.weefic.xtun.trojan.TrojanOutboundHandler
import com.weefic.xtun.utils.X509TrustAllManager
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPipeline
import io.netty.channel.socket.SocketChannel
import io.netty.handler.ssl.SslContextBuilder
import java.net.InetSocketAddress

class ServerChannelInitializer(
    val tunnel: Tunnel,
    val outboundConfig: TunnelOutboundConfig,
    val targetAddress: InetSocketAddress,
) : ChannelInitializer<SocketChannel>() {
    private fun ChannelPipeline.addTls(useTls: Boolean?) {
        if (useTls == true) {
            val sslContext = SslContextBuilder.forClient().trustManager(X509TrustAllManager()).build()
            this.addLast("ssl", sslContext.newHandler(this.channel().alloc()))
        }
    }


    override fun initChannel(ch: SocketChannel) {
        val pipeline = ch.pipeline()
        val serverConnection = ServerConnection(this.tunnel, ch)
        when (this.outboundConfig) {
            is TunnelOutboundConfig.Direct -> {
                pipeline.addLast(ServerConnectionDirectInboundHandler())
                pipeline.addLast(serverConnection)
            }

            is TunnelOutboundConfig.Http -> {
                pipeline.addTls(this.outboundConfig.tls)
                pipeline.addLast(ServerConnectionHttpProxyInboundHandler.HTTP_DECODER_NAME, ConnectRequestHttpResponseDecoder())
                pipeline.addLast(ServerConnectionHttpProxyInboundHandler(this.tunnel.connectionId, this.targetAddress.hostString, this.targetAddress.port, this.outboundConfig.user))
                pipeline.addLast(serverConnection)
                pipeline.addLast(PureHttpRequestEncoder()) // Write CONNECT Request
            }

            is TunnelOutboundConfig.Socks5 -> {
                pipeline.addTls(this.outboundConfig.tls)
                pipeline.addLast(ServerConnectionSocks5InboundHandler(this.tunnel.connectionId, this.targetAddress.hostString, this.targetAddress.port, this.outboundConfig.user))
                pipeline.addLast(serverConnection)
            }

            is TunnelOutboundConfig.Shadowsocks -> {
                pipeline.addTls(this.outboundConfig.tls)
                this.outboundConfig.method.config(pipeline, this.outboundConfig.password)
                pipeline.addLast(ShadowSocksHostEncoder(this.targetAddress.hostString, this.targetAddress.port))
                pipeline.addLast(serverConnection)
            }

            is TunnelOutboundConfig.Trojan -> {
                pipeline.addTls(this.outboundConfig.tls ?: true)
                pipeline.addLast(TrojanOutboundHandler(this.targetAddress.hostString, this.targetAddress.port, this.outboundConfig.password))
                pipeline.addLast(serverConnection)
            }

            is TunnelOutboundConfig.Blackhole -> throw UnsupportedOperationException("Blackhole unsupported")
            is TunnelOutboundConfig.Echo -> throw UnsupportedOperationException("Echo unsupported")
            is TunnelOutboundConfig.Reject -> throw UnsupportedOperationException("Reject unsupported")

        }
    }
}