package com.weefic.xtun.inbound

import com.weefic.xtun.Tunnel
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import org.slf4j.LoggerFactory

class ClientConnectionLoggerHandler(connectionId: Long) : ChannelDuplexHandler() {
    companion object {
        private val LOG = LoggerFactory.getLogger("client-connection-logger")
    }

    private val TAG = Tunnel.MARKERS.getDetachedMarker("-$connectionId")

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (LOG.isDebugEnabled && msg is ByteBuf) {
            LOG.info(TAG, "Client read {} bytes : {}", msg.readableBytes(), "...")
        }
        super.channelRead(ctx, msg)
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise?) {
        if (LOG.isDebugEnabled && msg is ByteBuf) {
            LOG.info(TAG, "Client write {} bytes : {}", msg.readableBytes(), "...")
        }
        super.write(ctx, msg, promise)
    }
}