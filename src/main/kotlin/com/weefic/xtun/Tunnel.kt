package com.weefic.xtun

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelOption
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory
import org.slf4j.helpers.BasicMarkerFactory
import java.util.concurrent.atomic.AtomicLong

class Tunnel(val config: TunnelConfig, clientChannel: SocketChannel) {
    companion object {
        val MARKERS = BasicMarkerFactory()
        private val IDGenerator = AtomicLong(0)
        private val LOG = LoggerFactory.getLogger("Tunnel")
    }

    val connectionId = IDGenerator.incrementAndGet()
    private val LOG_PREFIX = Tunnel.MARKERS.getDetachedMarker("-$connectionId")

    val clientConnection = ClientConnection(this, clientChannel)
    private var clientClosed = false

    private var connectServerRequested = false
    private var serverConnection: ServerConnection? = null
    private var serverClosed = false

    private var proxyToServerBuffer: MutableList<Any>? = null
    var clientWritable = true
        set(value) {
            field = value
            this.serverConnection?.clientWritableChanged()
        }
    var serverWritable = false
        set(value) {
            field = value
            this.clientConnection.serverWritableChanged()
        }

    fun connectServer(host: String, port: Int) {
        this.connectServerRequested = true
        val outboundConfig = this.config.outbound
        val serverAddress = outboundConfig.getServerAddress(host, port)
        val serverConnectionBootstrap = Bootstrap()
        serverConnectionBootstrap
            .group(this.clientConnection.channel.eventLoop())
            .channel(NioSocketChannel::class.java)
            .option(ChannelOption.AUTO_READ, false)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .handler(ServerChannelInitializer(this, outboundConfig, host, port))
        val serverConnectionFuture = serverConnectionBootstrap.connect(serverAddress)
        serverConnectionFuture.addListener { future ->
            if (!future.isSuccess) {
                LOG.info(LOG_PREFIX, "Failed to connection server : {}:{}", host, port)
                this.serverClosed()
            }
        }
    }

    fun serverConnectionNegotiationFailed() {
        if (!this.clientClosed) {
            this.clientConnection.channel.pipeline().fireUserEventTriggered(ServerConnectionNegotiationFailedEvent)
        }
    }

    fun serverConnectionEstablished(serverConnection: ServerConnection) {
        if (this.clientClosed) {
            serverConnection.channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        } else {
            this.clientConnection.channel.pipeline().fireUserEventTriggered(ServerConnectionEstablishedEvent)
            this.serverConnection = serverConnection
            this.proxyToServerBuffer?.let {
                for (msg in it) {
                    serverConnection.channel.write(msg).addListener {
                        if (!it.isSuccess) {
                            it.cause().printStackTrace()
                        }
                    }
                }
                this.flushServer()
            }
            this.proxyToServerBuffer = null
            this.serverWritable = true
        }
    }


    fun writeToServer(message: Any) {
        val serverConnection = this.serverConnection
        if (serverConnection == null) {
            var buffer = this.proxyToServerBuffer
            if (buffer == null) {
                buffer = mutableListOf()
                this.proxyToServerBuffer = buffer
            }
            buffer.add(message)
        } else {
            serverConnection.channel.write(message).addListener {
                if (!it.isSuccess) {
                    it.cause().printStackTrace()
                }
            }
        }
    }

    fun flushServer() {
        this.serverConnection?.channel?.flush()
    }

    fun writeToClient(message: Any) {
        this.clientConnection.channel.write(message).addListener {
            if (!it.isSuccess) {
                it.cause().printStackTrace()
            }
        }
    }

    fun flushClient() {
        this.clientConnection.channel.flush()
    }

    fun clientClosed() {
        val formConnectedToClose = this.clientClosed == false
        this.clientClosed = true
        if (this.serverClosed) {
            if (formConnectedToClose) {
                this.closed()
            }
        } else {
            if (this.connectServerRequested) {
                val serverConnection = this.serverConnection
                if (serverConnection != null) {
                    serverConnection.channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
                }
            } else {
                this.serverClosed = true
                this.closed()
            }
        }
    }


    fun serverClosed() {
        val formConnectedToClose = this.serverClosed == false
        this.serverClosed = true
        if (this.clientClosed) {
            if (formConnectedToClose) {
                this.closed()
            }
        } else {
            this.clientConnection.channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        }
    }

    private fun closed() {
        LOG.info(LOG_PREFIX, "Tunnel closed")
        this.proxyToServerBuffer?.let {
            it.forEach(ReferenceCountUtil::release)
            this.proxyToServerBuffer = null
        }
    }
}