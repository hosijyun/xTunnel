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
            .childHandler(ClientChannelInitializer(config))
        val binds = config.tunnels.map { tunnelConfig ->
            LOG.info("Binding {}", tunnelConfig.inbound.port)
            bootstrap.bind("0.0.0.0", tunnelConfig.inbound.port).addListener {
                if (it.isSuccess) {
                    LOG.info("Port {} binded", tunnelConfig.inbound.port)
                } else {
                    LOG.warn("Failed to bind {}", tunnelConfig.inbound.port, it.cause())
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

    val config = Config(
        listOf(
            TunnelConfig(TunnelInboundConfig.Http(8899), TunnelOutboundConfig.Http("127.0.0.1", 1087))
        )
    )
    xtun(config)
}