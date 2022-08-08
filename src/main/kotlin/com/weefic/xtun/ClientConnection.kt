package com.weefic.xtun

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.socket.SocketChannel
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory


class ClientConnection(private val tunnel: Tunnel, channel: SocketChannel) : ChannelPeerConnection(channel) {
    companion object {
        private val LOG = LoggerFactory.getLogger("Client-Connection")
    }

    private val LOG_PREFIX = Tunnel.MARKERS.getDetachedMarker("-${tunnel.connectionId}")
    val eventLoop get() = this.channel.eventLoop()

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        LOG.info(LOG_PREFIX, "Client active")
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        when (msg) {
            is ByteBuf -> {
                LOG.debug(LOG_PREFIX, "Client read {} bytes", msg.readableBytes())
                this.tunnel.writeToServer(msg)
            }
            is ServerConnectionRequest -> {
                LOG.info(LOG_PREFIX, "Connecting server")
                this.tunnel.connectServer(msg.host, msg.port)
                ReferenceCountUtil.release(msg)
            }
            else -> {
                LOG.debug(LOG_PREFIX, "Client read {} message", msg.javaClass)
                this.tunnel.writeToServer(msg)
            }
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        LOG.info(LOG_PREFIX, "Client read complete")
        this.tunnel.serverConnection?.flush()
        if (this.tunnel.serverWritable) {
            ctx.read()
        }
        super.channelReadComplete(ctx)
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        val writable = ctx.channel().isWritable
        LOG.info(LOG_PREFIX, "Client writability changed to {}", writable)
        this.tunnel.clientWritable = writable
        super.channelWritabilityChanged(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        LOG.info(LOG_PREFIX, "Client inactive")
        this.tunnel.clientClosed()
        super.channelInactive(ctx)
    }

    override fun peerWritableChanged() {
        if (this.tunnel.serverWritable && this.channel.isActive) {
            this.channel.read()
        }
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg is ByteBuf) {
            LOG.info("Client write {} bytes", msg.readableBytes())
        } else {
            LOG.warn("Client write Unknown message : {}", msg.javaClass)
        }
        super.write(ctx, msg, promise)
    }
}