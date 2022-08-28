package com.weefic.xtun

import io.netty.channel.socket.SocketChannel
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory
import org.slf4j.helpers.BasicMarkerFactory
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong

class Tunnel(val outboundConfig: TunnelOutboundConfig, clientChannel: SocketChannel) {
    companion object {
        val MARKERS = BasicMarkerFactory()
        private val IDGenerator = AtomicLong(0)
        private val LOG = LoggerFactory.getLogger("Tunnel")
    }

    val connectionId = IDGenerator.incrementAndGet()
    private val LOG_PREFIX = MARKERS.getDetachedMarker("-$connectionId")

    val clientConnection = ClientConnection(this, clientChannel)
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
    private val inboundLocalAddress = clientChannel.localAddress()

    fun connectServer(address: InetSocketAddress) {
        this.connectServerRequested = true
        ServerConnectionFactory.connect(this, this.clientConnection.eventLoop, this.outboundConfig, this.inboundLocalAddress, address, object : ServerConnectionCompletionListener {
            override fun complete(isSuccess: Boolean) {
                if (!isSuccess) {
                    LOG.info(LOG_PREFIX, "Failed to connection server : {}:{}", address.hostString, address.port)
                    this@Tunnel.serverClosed()
                }
            }
        })
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