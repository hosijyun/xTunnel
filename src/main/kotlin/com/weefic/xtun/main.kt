package com.weefic.xtun

import ch.qos.logback.classic.util.ContextInitializer
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.ResourceLeakDetector
import org.slf4j.LoggerFactory

fun xtun(config: TunnelConfig) {
    val LOG = LoggerFactory.getLogger("Startup")
    val bossGroup = NioEventLoopGroup(1)
    val workerGroup = NioEventLoopGroup()
    val route = TunnelRoute(config)
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
        val binds = config.inbound.values.map { inbound ->
            LOG.info("Binding {}", inbound.port)
            bootstrap.bind("0.0.0.0", inbound.port).addListener {
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
            TunnelRouteConfig("in1", "out1", null, "u1"),
            TunnelRouteConfig("in1", "out2", null, "u2")
        ),
        inbound = mapOf(
            "in1" to TunnelInboundConfig.Http(
                port = 8899,
                credentials = listOf(
                    UserCredential("u1", "1"),
                    UserCredential("u2", "1")
                )
            ),
        ),
        outbound = mapOf(
            "out1" to TunnelOutboundConfig.Http("127.0.0.1", 1087),
            "out2" to TunnelOutboundConfig.Http("127.0.0.1", 1088),
        ),
    )
    xtun(config)
}