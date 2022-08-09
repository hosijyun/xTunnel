package com.weefic.xtun

import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoop
import io.netty.channel.socket.nio.NioSocketChannel

object ServerConnectionFactory {
    fun connect(tunnel: Tunnel, eventLoop: EventLoop, outboundConfig: TunnelOutboundConfig, host: String, port: Int, completeHandler: (Boolean) -> Unit) {
        val serverAddress = outboundConfig.getServerAddress(host, port)
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