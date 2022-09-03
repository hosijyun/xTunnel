package com.weefic.xtun

import com.weefic.xtun.handlers.ConnectRequestHttpResponseDecoder
import com.weefic.xtun.handlers.PureHttpRequestEncoder
import com.weefic.xtun.outbound.ServerConnectionDirectInboundHandler
import com.weefic.xtun.outbound.ServerConnectionHttpProxyInboundHandler
import com.weefic.xtun.outbound.ServerConnectionSocks5InboundHandler
import com.weefic.xtun.shadowsocks.ShadowSocksHostEncoder
import com.weefic.xtun.shadowsocks.config
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import java.net.InetSocketAddress

class ServerChannelInitializer(
    val tunnel: Tunnel,
    val outboundConfig: TunnelOutboundConfig,
    val targetAddress: InetSocketAddress,
) : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel) {
        val pipeline = ch.pipeline()
        val serverConnection = ServerConnection(this.tunnel, ch)
        when (this.outboundConfig) {
            is TunnelOutboundConfig.Direct -> {
                pipeline.addLast(ServerConnectionDirectInboundHandler())
                pipeline.addLast(serverConnection)
            }
            is TunnelOutboundConfig.Http -> {
                pipeline.addLast(ServerConnectionHttpProxyInboundHandler.HTTP_DECODER_NAME, ConnectRequestHttpResponseDecoder())
                pipeline.addLast(ServerConnectionHttpProxyInboundHandler(this.tunnel.connectionId, this.targetAddress.hostString, this.targetAddress.port, this.outboundConfig.credential))
                pipeline.addLast(serverConnection)
                pipeline.addLast(PureHttpRequestEncoder()) // Write CONNECT Request
            }
            is TunnelOutboundConfig.Socks5 -> {
                pipeline.addLast(ServerConnectionSocks5InboundHandler(this.tunnel.connectionId, this.targetAddress.hostString, this.targetAddress.port, this.outboundConfig.credential))
                pipeline.addLast(serverConnection)
            }
            is TunnelOutboundConfig.Shadowsocks -> {
                this.outboundConfig.encryptionMethod.config(pipeline, this.outboundConfig.password)
                pipeline.addLast(ShadowSocksHostEncoder(this.targetAddress.hostString, this.targetAddress.port))
                pipeline.addLast(serverConnection)
            }
            TunnelOutboundConfig.Blackhole -> throw UnsupportedOperationException("Blackhole unsupported")
            TunnelOutboundConfig.Echo -> throw UnsupportedOperationException("Echo unsupported")

        }
    }
}