package com.weefic.xtun.inbound

import com.weefic.xtun.ClientConnection
import com.weefic.xtun.Tunnel
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import org.slf4j.LoggerFactory
import java.util.Base64

class ClientConnectionLoggerHandler(
    private val tunnel: Tunnel,
) : ChannelDuplexHandler() {
    companion object {
        private val LOG = LoggerFactory.getLogger("Tunnel")
    }

    private val LOG_TAG get() = this.tunnel.LOG_TAG
    private var startTimestamp: Long = Long.MAX_VALUE

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        this.startTimestamp = System.currentTimeMillis()
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (LOG.isDebugEnabled && msg is ByteBuf) {
            LOG.debug(LOG_TAG, "Client read {} bytes : {}", msg.readableBytes(), "...")
        }
        super.channelRead(ctx, msg)
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise?) {
        if (LOG.isDebugEnabled && msg is ByteBuf) {
            LOG.debug(LOG_TAG, "Client write {} bytes : {}", msg.readableBytes(), "...")
        }
        if (this.startTimestamp != Long.MAX_VALUE) {
            val duration = System.currentTimeMillis() - this.startTimestamp
            if (duration > 5000L) {
                LOG.warn(LOG_TAG, "The client receives the content from the server after {} ms.", duration)
            }
            this.startTimestamp = Long.MAX_VALUE
        }
        super.write(ctx, msg, promise)
    }
}