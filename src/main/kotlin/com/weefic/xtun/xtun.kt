package com.weefic.xtun

import com.weefic.xtun.web.WebConfig
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.resolver.dns.DnsNameResolverBuilder
import io.netty.resolver.dns.SequentialDnsServerAddressStreamProvider
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress


val XTUN_LOGGER by lazy { LoggerFactory.getLogger("Main  ") }

fun xtun(config: TunnelConfig, pac: Map<String, PAC>? = null, webConfig: WebConfig? = null, tlsConfigs: List<TlsConfig>? = null) {
    val LOG = XTUN_LOGGER
    val ioHandlerFactory = NioIoHandler.newFactory()
    val bossGroup = MultiThreadIoEventLoopGroup(1, ioHandlerFactory)
    val workerGroup = MultiThreadIoEventLoopGroup(ioHandlerFactory)
    val dnsResolver = DnsNameResolverBuilder(workerGroup.next()).apply {
        this.datagramChannelType(NioDatagramChannel::class.java)
        if (config.dns.isNotEmpty()) {
            this.nameServerProvider(
                SequentialDnsServerAddressStreamProvider(
                    config.dns.map { InetSocketAddress.createUnresolved(it, 53) }
                ))
        }
    }.build()
    val route = TunnelRoute(config, pac, dnsResolver)
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
            .childHandler(ClientChannelInitializer(route, webConfig, tlsConfigs, bossGroup))


        val webServiceFuture = if (webConfig != null) {
            val webHost = webConfig.host ?: "0.0.0.0"
            val webPort = webConfig.port
            bootstrap.bind(webHost, webPort).addListener {
                if (it.isSuccess) {
                    LOG.info("Web port {} bound. Starting web services.", webPort)
                } else {
                    LOG.error("Failed to bind web port {}. Stopping services.", webPort, it.cause())
                }
            }.sync()
        } else {
            null
        }
        val binds = config.proxies.map { (name, proxy) ->
            val bindingHost = proxy.host ?: "0.0.0.0"
            val bindingPort = proxy.port
            LOG.info("Starting proxy '{}' on {}:{}.", name, bindingHost, bindingPort)
            bootstrap.bind(bindingHost, bindingPort).addListener {
                if (it.isSuccess) {
                    LOG.info("Proxy '{}' is started on {}:{}.", name, bindingHost, bindingPort)
                } else {
                    LOG.error("Failed to start the proxy '{}' on {}:{}.", name, bindingHost, bindingPort, it.cause())
                }
            }
            bootstrap.bind(bindingHost, bindingPort)
        }
        for (bind in binds) {
            try {
                bind.sync()
            } catch (_: Exception) {
            }
        }
        webServiceFuture?.channel()?.closeFuture()?.sync()
        for (bind in binds) {
            bind.channel().closeFuture().sync()
        }
        LOG.info("Service stopped.")
    } catch (e: Exception) {
        LOG.error("Service error.", e)
    } finally {
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
    }
}