package com.weefic.xtun

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.socket.SocketChannel
import org.slf4j.LoggerFactory


class ServerConnection(val tunnel: Tunnel, channel: SocketChannel) : ChannelPeerConnection(channel) {
    companion object {
        val LOG = LoggerFactory.getLogger(ServerConnection::class.java)
    }

    private var LOG_PREFIX = Tunnel.MARKERS.getDetachedMarker("-${this.tunnel.connectionId}")

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        LOG.info(this.LOG_PREFIX, "Server Active")
    }


    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        when (msg) {
            is ByteBuf -> {
                LOG.debug(LOG_PREFIX, "Server read {} bytes", msg.readableBytes())
                this.tunnel.clientConnection.write(msg)
            }
            is ServerConnectionResult -> {
                if (msg == ServerConnectionResult.Success) {
                    LOG.info(LOG_PREFIX, "Server connection established")
                    this.tunnel.serverConnectionEstablished(this)
                } else {
                    LOG.info(LOG_PREFIX, "Server connection failed")
                    this.tunnel.serverConnectionNegotiationFailed(msg)
                }
            }
            else -> {
                LOG.debug(LOG_PREFIX, "Server read {} message", msg.javaClass)
                this.tunnel.clientConnection.write(msg)
            }
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        LOG.info("Server read complete")
        this.tunnel.clientConnection.flush()
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
        super.channelInactive(ctx)
        LOG.info("Server inactive")
        this.tunnel.serverClosed()
    }

    override fun peerWritableChanged() {
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