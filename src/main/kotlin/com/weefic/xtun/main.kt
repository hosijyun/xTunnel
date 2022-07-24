package com.weefic.xtun

import ch.qos.logback.classic.util.ContextInitializer
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.ResourceLeakDetector
import org.slf4j.LoggerFactory

fun xtun(config: Config) {
    val LOG = LoggerFactory.getLogger("Startup")
    val bossGroup = NioEventLoopGroup(1)
    val workerGroup = NioEventLoopGroup()
    System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, "./logback.xml")
    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID)


    try {
        val bootstrap = ServerBootstrap()
        bootstrap
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 128)
            .option(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.AUTO_READ, false)
            .childHandler(ClientChannelInitializer(config))
        val binds = config.tunnels.map {
            bootstrap.bind("0.0.0.0", it.inbound.port)
        }
        for (bind in binds) {
            bind.sync()
        }
        for (bind in binds) {
            bind.channel().closeFuture().sync()
        }
        LOG.info("Service stopped")
    } finally {
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
    }
}

fun main() {
    val config = Config(
        listOf(
            TunnelConfig(TunnelInboundConfig.Socks5(8899, UserCredential("hello", "1")), TunnelOutboundConfig.Socks5("127.0.0.1", 1086))
        )
    )
    xtun(config)
}