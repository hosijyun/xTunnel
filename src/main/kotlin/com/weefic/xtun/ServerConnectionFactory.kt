package com.weefic.xtun

import com.weefic.xtun.outbound.BlackholeConnection
import com.weefic.xtun.outbound.EchoConnection
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoop
import io.netty.channel.socket.nio.NioSocketChannel
import java.net.InetSocketAddress

interface ServerConnectionCompletionListener {
    fun complete(success: Boolean)
}

object ServerConnectionFactory {
    fun connect(tunnel: Tunnel, eventLoop: EventLoop, outboundConfig: TunnelOutboundConfig, address: InetSocketAddress, completeHandler: ServerConnectionCompletionListener) {
        when (outboundConfig) {
            TunnelOutboundConfig.Direct -> {
                this.connect0(tunnel, eventLoop, outboundConfig, address, address, completeHandler)
            }
            is TunnelOutboundConfig.Http -> {
                val serverAddress = InetSocketAddress.createUnresolved(outboundConfig.host, outboundConfig.port)
                this.connect0(tunnel, eventLoop, outboundConfig, serverAddress, address, completeHandler)
            }
            is TunnelOutboundConfig.Socks5 -> {
                val serverAddress = InetSocketAddress.createUnresolved(outboundConfig.host, outboundConfig.port)
                this.connect0(tunnel, eventLoop, outboundConfig, serverAddress, address, completeHandler)
            }
            TunnelOutboundConfig.Blackhole -> {
                BlackholeConnection(tunnel, eventLoop)
                completeHandler.complete(true)
            }
            TunnelOutboundConfig.Echo -> {
                EchoConnection(tunnel, eventLoop)
                completeHandler.complete(true)
            }
        }
    }

    private fun connect0(tunnel: Tunnel, eventLoop: EventLoop, outboundConfig: TunnelOutboundConfig, serverAddress: InetSocketAddress, targetAddress: InetSocketAddress, completeHandler: ServerConnectionCompletionListener) {
        val serverConnectionBootstrap = Bootstrap()
        serverConnectionBootstrap
            .group(eventLoop)
            .channel(NioSocketChannel::class.java)
            .option(ChannelOption.AUTO_READ, false)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .handler(ServerChannelInitializer(tunnel, outboundConfig, targetAddress))
        serverConnectionBootstrap.connect(serverAddress).addListener {
            completeHandler.complete(it.isSuccess)
        }
    }
}