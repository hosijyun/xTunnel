package com.weefic.xtun.outbound

import com.weefic.xtun.AbstractConnection
import com.weefic.xtun.Tunnel
import io.netty.channel.EventLoop
import io.netty.util.ReferenceCountUtil

class EchoConnection(val tunnel: Tunnel, val eventLoop: EventLoop) : AbstractConnection {
    private var close = false
    override fun peerWritableChanged() {
        this.tunnel.serverWritable = this.tunnel.clientWritable
    }

    init {
        this.eventLoop.execute {
            this.tunnel.serverConnectionEstablished(this)
        }
    }

    override fun triggerEvent(event: Any) {
    }

    override fun write(message: Any) {
        this.tunnel.clientConnection.write(message)
    }

    override fun writeAndFlush(message: Any) {
        this.tunnel.clientConnection.writeAndFlush(message)
    }

    override fun flush() {
        this.tunnel.clientConnection.flush()
    }

    override fun close() {
        if (!this.close) {
            this.close = true
            this.tunnel.serverClosed()
        }
    }
}