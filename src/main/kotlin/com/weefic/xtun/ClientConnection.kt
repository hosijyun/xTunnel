package com.weefic.xtun

import com.weefic.xtun.utils.ChannelLoggingUtils
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.socket.SocketChannel
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory
import java.util.*


class ClientConnection(private val tunnel: Tunnel, channel: SocketChannel) : ChannelPeerConnection(channel) {
    companion object {
        val LOG = LoggerFactory.getLogger("Client")
        const val NAME = "ClientConnection"
    }

    private val LOG_TAG get() = this.tunnel.LOG_TAG
    val eventLoop get() = this.channel.eventLoop()
    private var lastWriteBlocking: Long = -1

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        LOG.debug(LOG_TAG, "Client connection active.")
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        when (msg) {
            is ByteBuf -> {
                LOG.debug(LOG_TAG, "Client read {} bytes.", msg.readableBytes())
                this.tunnel.writeToServer(msg)
            }

            is ServerConnectionRequest -> {
                LOG.info(LOG_TAG, "Connecting server: {}:{}.", msg.address.hostString, msg.address.port)
                this.tunnel.connectServer(msg.address, msg.user)
                ReferenceCountUtil.release(msg)
            }

            else -> {
                LOG.debug(LOG_TAG, "Client read {} message.", msg.javaClass)
                this.tunnel.writeToServer(msg)
            }
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        LOG.debug(LOG_TAG, "Client read complete.")
        this.tunnel.serverConnection?.flush()
        if (this.tunnel.serverWritable) {
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
                    LOG.warn(LOG_TAG, "Client write unblocked after {} ms.", duration)
                } else {
                    LOG.debug(LOG_TAG, "Client write unblocked after {} ms.", duration)
                }
            }
        } else {
            this.lastWriteBlocking = System.currentTimeMillis()
            LOG.debug(LOG_TAG, "Client write blocked.")
        }
        this.tunnel.clientWritable = writable
        super.channelWritabilityChanged(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        LOG.debug(LOG_TAG, "Client connection inactive.")
        this.tunnel.clientClosed()
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ChannelLoggingUtils.logChannelException(LOG, LOG_TAG, "Client exception occurred.", cause)
        ctx.close()
    }

    override fun peerWritableChanged() {
        if (this.tunnel.serverWritable && this.channel.isActive) {
            this.channel.read()
        }
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg is ByteBuf) {
            if (LOG.isTraceEnabled) {
                val buffer = ByteArray(msg.readableBytes())
                msg.getBytes(msg.readerIndex(), buffer)
                LOG.trace(LOG_TAG, "Client write {} bytes : [{}]", msg.readableBytes(), Base64.getEncoder().encodeToString(buffer))
            } else {
                LOG.debug(LOG_TAG, "Client write {} bytes", msg.readableBytes())
            }
        } else {
            LOG.warn(LOG_TAG, "Client write Unknown message : {}", msg.javaClass)
        }
        super.write(ctx, msg, promise)
    }
}