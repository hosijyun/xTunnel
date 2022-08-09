package com.weefic.xtun

import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoop
import io.netty.channel.socket.nio.NioSocketChannel
import java.net.InetSocketAddress

object ServerConnectionFactory {
    fun connect(tunnel: Tunnel, eventLoop: EventLoop, outboundConfig: TunnelOutboundConfig, host: String, port: Int, completeHandler: (Boolean) -> Unit) {
        val serverAddress = when (outboundConfig) {
            TunnelOutboundConfig.Direct -> InetSocketAddress(host, port)
            is TunnelOutboundConfig.Http -> InetSocketAddress(outboundConfig.host, outboundConfig.port)
            is TunnelOutboundConfig.Socks5 -> InetSocketAddress(outboundConfig.host, outboundConfig.port)
        }
        val serverConnectionBootstrap = Bootstrap()
        serverConnectionBootstrap
            .group(eventLoop)
            .channel(NioSocketChannel::class.java)
            .option(ChannelOption.AUTO_READ, false)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .handler(ServerChannelInitializer(tunnel, outboundConfig, host, port))
        serverConnectionBootstrap.connect(serverAddress).addListener {
            completeHandler(it.isSuccess)
        }
    }
}