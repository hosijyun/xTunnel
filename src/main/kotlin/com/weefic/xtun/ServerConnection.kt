package com.weefic.xtun

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.socket.SocketChannel
import org.slf4j.LoggerFactory


class ServerConnection(val tunnel: Tunnel, val channel: SocketChannel) : ChannelDuplexHandler() {
    companion object {
        val LOG = LoggerFactory.getLogger(ServerConnection::class.java)
    }

    private var LOG_PREFIX = Tunnel.MARKERS.getDetachedMarker("-${this.tunnel.connectionId}")

    override fun channelActive(ctx: ChannelHandlerContext) {
        LOG.info(this.LOG_PREFIX, "Server Active")
        ctx.read()
        super.channelActive(ctx)
    }


    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        when (msg) {
            is ByteBuf -> {
                LOG.debug(LOG_PREFIX, "Server read {} bytes", msg.readableBytes())
                this.tunnel.writeToClient(msg)
            }
            is ServerConnectionEstablishedEvent -> {
                LOG.info(LOG_PREFIX, "Server connection established")
                this.tunnel.serverConnectionEstablished(this)
            }
            is ServerConnectionNegotiationFailedEvent -> {
                LOG.info(LOG_PREFIX, "Server connection failed")
                this.tunnel.serverConnectionNegotiationFailed()
            }
            else -> {
                LOG.debug(LOG_PREFIX, "Server read {} message", msg.javaClass)
                this.tunnel.writeToClient(msg)
            }
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        LOG.info("Server read complete")
        this.tunnel.flushClient()
        if (this.tunnel.clientWritable) {
            ctx.read()
        }
        super.channelReadComplete(ctx)
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        val writable = ctx.channel().isWritable
        LOG.info(LOG_PREFIX, "Server writability changed to {}", writable)
        this.tunnel.serverWritable = writable
        super.channelWritabilityChanged(ctx)
    }


    override fun channelInactive(ctx: ChannelHandlerContext) {
        LOG.info("Server inactive")
        super.channelInactive(ctx)
        this.tunnel.serverClosed()
    }

    fun clientWritableChanged() {
        if (this.tunnel.clientWritable && this.channel.isActive) {
            this.channel.read()
        }
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg is ByteBuf) {
            LOG.info("Server write {} bytes", msg.readableBytes())
        } else {
            LOG.warn("Server write Unknown message : {}", msg.javaClass)
        }
        super.write(ctx, msg, promise)
    }

}