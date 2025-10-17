package com.weefic.xtun.async

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

open class AsyncInboundHandler() : ChannelInboundHandlerAdapter() {
    private val dispatcherScope = GlobalScope
    private var dispatcher: NettyDispatcher? = null
    private val source = kotlinx.coroutines.channels.Channel<ByteArray>()
    private val reader = AsyncReader(AsyncReaderChannelSource(this.source))

    private fun getDispatcher(ctx: ChannelHandlerContext): NettyDispatcher {
        var dispatcher = this.dispatcher
        if (dispatcher == null) {
            dispatcher = NettyDispatcher(ctx.channel().eventLoop())
            this.dispatcher = dispatcher
        }
        return dispatcher
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        this.dispatcherScope.launch(this.getDispatcher(ctx)) {
            handle(ctx, reader)
        }.invokeOnCompletion { error ->
            if (error != null) {
                if (error is kotlinx.coroutines.channels.ClosedReceiveChannelException) {
                    // Channel inactive
                } else {
                    ctx.fireExceptionCaught(error)
                }
            }
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        this.dispatcherScope.launch(this.getDispatcher(ctx)) {
            source.close()
        }
        super.channelInactive(ctx)
    }


    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is ByteBuf) {
            this.dispatcherScope.launch(this.getDispatcher(ctx)) {
                try {
                    val buffer = ByteArray(msg.readableBytes())
                    msg.getBytes(msg.readerIndex(), buffer)
                    source.send(buffer)
                } finally {
                    msg.release()
                }
            }
        } else {
            super.channelRead(ctx, msg)
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        super.channelReadComplete(ctx)
    }


    open suspend fun handle(ctx: ChannelHandlerContext, reader: AsyncReader) {
    }
}