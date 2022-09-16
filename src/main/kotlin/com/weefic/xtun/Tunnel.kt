package com.weefic.xtun

import io.netty.channel.socket.SocketChannel
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory
import org.slf4j.helpers.BasicMarkerFactory
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class Tunnel(
    private val clientChannel: SocketChannel,
    private val route: TunnelRoute
) {
    companion object {
        val MARKERS = BasicMarkerFactory()
        private val IDGenerator = AtomicLong(0)
        private val LOG = LoggerFactory.getLogger("Tunnel")
        private val OutboundRoutingThreads = Executors.newCachedThreadPool()
    }

    val connectionId = IDGenerator.incrementAndGet()
    private val LOG_PREFIX = MARKERS.getDetachedMarker("-$connectionId")

    val clientConnection = ClientConnection(this, this.clientChannel)
    private var clientClosed = false

    private var connectServerRequested = false
    var serverConnection: AbstractConnection? = null
    private var serverClosed = false

    private var proxyToServerBuffer: MutableList<Any>? = null
    var clientWritable = true
        set(value) {
            field = value
            this.serverConnection?.peerWritableChanged()
        }
    var serverWritable = false
        set(value) {
            field = value
            this.clientConnection.peerWritableChanged()
        }

    private fun connectServer0(outboundConfig: TunnelOutboundConfig?, localAddress: InetSocketAddress, targetAddress: InetSocketAddress, user: String?) {
        if (outboundConfig != null) {
            val startAt = System.currentTimeMillis()
            ServerConnectionFactory.connect(this, this.clientConnection.eventLoop, outboundConfig, localAddress, targetAddress, object : ServerConnectionCompletionListener {
                override fun complete(isSuccess: Boolean) {
                    val duration = System.currentTimeMillis() - startAt
                    if (!isSuccess) {
                        LOG.info(LOG_PREFIX, "Failed to connect server : {}:{}. It take {} millis.", targetAddress.hostString, targetAddress.port, duration)
                        this@Tunnel.serverClosed()
                    } else {
                        LOG.info(LOG_PREFIX, "Connect {}:{} successfully. It take {} millis.", targetAddress.hostString, targetAddress.port, duration)
                    }
                }
            })
        } else {
            LOG.info(LOG_PREFIX, "No outbound config for client address : {}:{}, user : {}", targetAddress.hostString, targetAddress.port, user)
            this@Tunnel.serverClosed()
        }
    }

    fun connectServer(targetAddress: InetSocketAddress, user: String?) {
        this.connectServerRequested = true
        val localAddress = this.clientChannel.localAddress()!!
        val clientAddress = this.clientChannel.remoteAddress()!!
        val useEventLoop = this.route.pac != null
        if (useEventLoop) {
            val eventLoop = this@Tunnel.clientChannel.eventLoop()
            val startAt = System.currentTimeMillis()
            OutboundRoutingThreads.run {
                val outboundConfig = try {
                    // This may take some time.(For PAC)
                    this@Tunnel.route.getOutboundConfig(localAddress, clientAddress, targetAddress, user)
                } catch (e: Exception) {
                    LOG.warn("Failed to get outbound config.", e)
                    null
                }
                eventLoop.execute {
                    val duration = System.currentTimeMillis() - startAt
                    if (duration > 1500) {
                        LOG.warn("It take {} millis to routing {}:{}", duration, targetAddress.hostString, targetAddress.port)
                    }
                    this@Tunnel.connectServer0(outboundConfig, localAddress, targetAddress, user)
                }
            }
        } else {
            val outboundConfig = this@Tunnel.route.getOutboundConfig(localAddress, clientAddress, targetAddress, user)
            this.connectServer0(outboundConfig, localAddress, targetAddress, user)
        }
    }

    fun serverConnectionNegotiationFailed(why: ServerConnectionResult) {
        if (!this.clientClosed) {
            this.clientConnection.triggerEvent(why)
        }
    }

    fun serverConnectionEstablished(serverConnection: AbstractConnection) {
        if (this.clientClosed) {
            serverConnection.close()
        } else {
            this.clientConnection.triggerEvent(ServerConnectionResult.Success)
            this.serverConnection = serverConnection
            this.proxyToServerBuffer?.let {
                for (msg in it) {
                    serverConnection.write(msg)
                }
                serverConnection.flush()
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
            serverConnection.write(message)
        }
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
                this.serverConnection?.close()
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
            this.clientConnection.close()
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