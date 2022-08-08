package com.weefic.xtun

import com.weefic.xtun.handlers.ConnectRequestHttpResponseDecoder
import com.weefic.xtun.handlers.PureHttpRequestEncoder
import com.weefic.xtun.outbound.ServerConnectionDirectInboundHandler
import com.weefic.xtun.outbound.ServerConnectionHttpProxyInboundHandler
import com.weefic.xtun.outbound.ServerConnectionSocks5InboundHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel

class ServerChannelInitializer(
    val tunnel: Tunnel,
    val outboundConfig: TunnelOutboundConfig,
    val targetHost: String,
    val targetPort: Int,
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
                pipeline.addLast(ServerConnectionHttpProxyInboundHandler(this.tunnel.connectionId, this.targetHost, this.targetPort, this.outboundConfig.credential))
                pipeline.addLast(serverConnection)
                pipeline.addLast(PureHttpRequestEncoder()) // Write CONNECT Request
            }
            is TunnelOutboundConfig.Socks5 -> {
                pipeline.addLast(ServerConnectionSocks5InboundHandler(this.tunnel.connectionId, this.targetHost, this.targetPort, this.outboundConfig.credential))
                pipeline.addLast(serverConnection)
            }
        }
    }
}