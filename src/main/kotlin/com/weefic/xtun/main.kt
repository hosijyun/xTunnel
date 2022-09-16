package com.weefic.xtun

import ch.qos.logback.classic.util.ContextInitializer
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.ResourceLeakDetector
import org.slf4j.LoggerFactory

fun xtun(config: TunnelConfig, pac: Map<String, PAC>? = null) {
    val LOG = LoggerFactory.getLogger("Startup")
    val bossGroup = NioEventLoopGroup(1)
    val workerGroup = NioEventLoopGroup()
    val route = TunnelRoute(config, pac)
    try {
        LOG.info("Starting services...")
        val bootstrap = ServerBootstrap()
        bootstrap
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 128)
            .option(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.AUTO_READ, false)
            .childHandler(ClientChannelInitializer(route))
        val binds = config.inbound.map { inbound ->
            val bindingHost = inbound.host ?: "0.0.0.0"
            LOG.info("Binding {}:{}", bindingHost, inbound.port)
            bootstrap.bind(bindingHost, inbound.port).addListener {
                if (it.isSuccess) {
                    LOG.info("Port {} binded", inbound.port)
                } else {
                    LOG.warn("Failed to bind {}", inbound.port, it.cause())
                }
            }
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
    System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, "./logback.xml")
    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID)
    val config = TunnelConfig(
        route = listOf(
            TunnelRouteConfig("in1", "out1"),
        ),
        inbound = listOf(
            TunnelInboundConfig.Http("in1", 1112),
        ),
        outbound = listOf(
            TunnelOutboundConfig.Socks5("out1", "8.219.130.225", 3308, user = UserCredential("zy", "DDmt5nUUc5zmlkwHPLrixA7dql3lGMYM"))
        )
    )
    xtun(config)
}