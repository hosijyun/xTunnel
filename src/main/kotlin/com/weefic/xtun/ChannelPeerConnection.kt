package com.weefic.xtun

import com.weefic.xtun.utils.ChannelLoggingUtils
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.socket.SocketChannel
import java.nio.channels.ClosedChannelException

abstract class ChannelPeerConnection(protected val channel: SocketChannel) : ChannelDuplexHandler(), AbstractConnection {
    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        ctx.read()
    }

    override fun triggerEvent(event: Any) {
        this.channel.pipeline().fireUserEventTriggered(event)
    }

    override fun write(message: Any) {
        this.channel.write(message)
    }

    override fun writeAndFlush(message: Any) {
        this.channel.writeAndFlush(message)
    }

    override fun flush() {
        this.channel.flush()
    }

    override fun close() {
        this.channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
    }
}