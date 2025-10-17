package com.weefic.xtun

import com.weefic.xtun.utils.ChannelLoggingUtils
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.socket.SocketChannel
import org.slf4j.LoggerFactory
import java.util.*


class ServerConnection(val tunnel: Tunnel, channel: SocketChannel) : ChannelPeerConnection(channel) {
    companion object {
        val LOG = LoggerFactory.getLogger("Server")
    }

    private val LOG_TAG get() = this.tunnel.LOG_TAG
    private var lastWriteBlocking: Long = -1

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        LOG.debug(this.LOG_TAG, "Server connection active.")
    }


    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        when (msg) {
            is ByteBuf -> {
                LOG.debug(LOG_TAG, "Server read {} bytes", msg.readableBytes())
                this.tunnel.writeToClient(msg)
            }

            is ServerConnectionResult -> {
                if (msg == ServerConnectionResult.Success) {
                    LOG.debug(LOG_TAG, "Server connection established")
                    this.tunnel.serverConnectionEstablished(this)
                } else {
                    LOG.debug(LOG_TAG, "Server connection failed")
                    this.tunnel.serverConnectionNegotiationFailed(msg)
                }
            }

            else -> {
                LOG.debug(LOG_TAG, "Server read {} message", msg.javaClass)
                this.tunnel.writeToClient(msg)
            }
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        LOG.debug(LOG_TAG, "Server read complete")
        this.tunnel.clientConnection.flush()
        if (this.tunnel.clientWritable) {
            ctx.read()
        }
        super.channelReadComplete(ctx)
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        val writable = ctx.channel().isWritable
        if (writable) {
            if (this.lastWriteBlocking != -1L) {
                val duration = System.currentTimeMillis() - this.lastWriteBlocking
                if (duration > 200L) {
                    LOG.warn(LOG_TAG, "Server write unblocked after {} ms.", duration)
                } else {
                    LOG.debug(LOG_TAG, "Server write unblocked after {} ms.", duration)
                }
            }
        } else {
            this.lastWriteBlocking = System.currentTimeMillis()
            LOG.debug(LOG_TAG, "Server write blocked.")
        }
        this.tunnel.serverWritable = writable
        super.channelWritabilityChanged(ctx)
    }


    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        LOG.debug(LOG_TAG, "Server connection inactive.")
        this.tunnel.serverClosed()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ChannelLoggingUtils.logChannelException(LOG, LOG_TAG, "Server exception occurred.", cause)
        ctx.close()
    }

    override fun peerWritableChanged() {
        if (this.tunnel.clientWritable && this.channel.isActive) {
            this.channel.read()
        }
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg is ByteBuf) {
            if (LOG.isTraceEnabled) {
                val buffer = ByteArray(msg.readableBytes())
                msg.getBytes(msg.readerIndex(), buffer)
                LOG.trace(LOG_TAG, "Server write {} bytes : [{}]", msg.readableBytes(), Base64.getEncoder().encodeToString(buffer))
            } else {
                LOG.debug(LOG_TAG, "Server write {} bytes", msg.readableBytes())
            }
        } else {
            LOG.warn(LOG_TAG, "Server write Unknown message : {}", msg.javaClass)
        }
        super.write(ctx, msg, promise)
    }
}