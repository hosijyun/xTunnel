package com.weefic.xtun

import com.weefic.xtun.web.WebConfig
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import org.slf4j.LoggerFactory

fun xtun(config: TunnelConfig, pac: Map<String, PAC>? = null, webConfig: WebConfig? = null) {
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
            .childHandler(ClientChannelInitializer(route, webConfig, bossGroup))
        val addresses = config.proxies.values.map { it.host to it.port }.toMutableList()
        if (webConfig != null) {
            addresses.add(webConfig.host to webConfig.port)
        }
        val binds = addresses.map { (host, port) ->
            val bindingHost = host ?: "0.0.0.0"
            LOG.info("Binding {}:{}", bindingHost, port)
            bootstrap.bind(bindingHost, port).addListener {
                if (it.isSuccess) {
                    LOG.info("Port {} binded", port)
                } else {
                    LOG.warn("Failed to bind {}", port, it.cause())
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