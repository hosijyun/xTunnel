package com.weefic.xtun

import ch.qos.logback.classic.util.ContextInitializer
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.ResourceLeakDetector
import org.slf4j.LoggerFactory



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
            TunnelOutboundConfig.Direct("out1")
        )
    )
    xtun(config)
}