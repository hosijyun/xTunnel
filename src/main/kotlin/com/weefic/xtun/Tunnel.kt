package com.weefic.xtun

import io.netty.buffer.ByteBuf
import io.netty.channel.socket.SocketChannel
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory
import org.slf4j.helpers.BasicMarkerFactory
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class Tunnel(
    private val inboundName: String,
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
    var LOG_TAG = MARKERS.getDetachedMarker("$inboundName#$connectionId")

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
    var clientBytes: Long = 0
    var serverBytes: Long = 0

    private fun connectServer0(routerMatchingResult: RouterMatchingResult?, clientAddress: InetSocketAddress, localAddress: InetSocketAddress, user: String?) {
        if (routerMatchingResult != null) {
            val outboundConfig = routerMatchingResult.outbound
            val targetAddress = routerMatchingResult.targetAddress
            val startAt = System.currentTimeMillis()
            ServerConnectionFactory.connect(this, this.clientConnection.eventLoop, outboundConfig, localAddress, targetAddress, object : ServerConnectionCompletionListener {
                override fun complete(isSuccess: Boolean) {
                    val duration = System.currentTimeMillis() - startAt
                    if (!isSuccess) {
                        ClientConnection.LOG.info(LOG_TAG, "Failed to connect server : {}:{}. It take {} millis.", targetAddress.hostString, targetAddress.port, duration)
                        this@Tunnel.serverClosed()
                    } else {
                        ClientConnection.LOG.info(LOG_TAG, "Connect {}:{} successfully. It take {} millis.", targetAddress.hostString, targetAddress.port, duration)
                    }
                }
            })
        } else {
            ClientConnection.LOG.info(LOG_TAG, "No outbound config for client address : {}:{}, user : {}", clientAddress.hostString, clientAddress.port, user)
            this@Tunnel.serverClosed()
        }
    }

    fun connectServer(targetAddress: InetSocketAddress, user: String?) {
        this.LOG_TAG = MARKERS.getDetachedMarker("$inboundName#$connectionId][${targetAddress.hostString}:${targetAddress.port}")
        this.connectServerRequested = true
        val localAddress = this.clientChannel.localAddress()!!
        val clientAddress = this.clientChannel.remoteAddress()!!
        val useEventLoop = this.route.pac != null || this.route.geoip != null
        if (useEventLoop) {
            val eventLoop = this@Tunnel.clientChannel.eventLoop()
            val startAt = System.currentTimeMillis()
            OutboundRoutingThreads.run {
                val outboundConfig = try {
                    // This may take some time.(For PAC || GEOIP)
                    this@Tunnel.route.getOutboundConfig(localAddress, clientAddress, targetAddress, user)
                } catch (e: Exception) {
                    ClientConnection.LOG.warn(LOG_TAG, "Failed to get outbound config.", e)
                    null
                }
                eventLoop.execute {
                    val duration = System.currentTimeMillis() - startAt
                    if (duration > 1500) {
                        ClientConnection.LOG.warn(LOG_TAG, "It take {} millis to routing {}:{}", duration, targetAddress.hostString, targetAddress.port)
                    }
                    this@Tunnel.connectServer0(outboundConfig, clientAddress, localAddress, user)
                }
            }
        } else {
            val outboundConfig = this@Tunnel.route.getOutboundConfig(localAddress, clientAddress, targetAddress, user)
            this.connectServer0(outboundConfig, clientAddress, localAddress, user)
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
        if (LOG.isInfoEnabled) {
            if (message is ByteBuf) {
                this.clientBytes += message.readableBytes()
            }
        }
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

    fun writeToClient(message: Any) {
        if (LOG.isInfoEnabled) {
            if (message is ByteBuf) {
                this.serverBytes += message.readableBytes()
            }
        }
        this.clientConnection.write(message)
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
        LOG.info(LOG_TAG, "Tunnel closed. Upload {} bytes / Download {} bytes.", this.clientBytes, this.serverBytes)
        this.proxyToServerBuffer?.let {
            it.forEach(ReferenceCountUtil::release)
            this.proxyToServerBuffer = null
        }
    }
}