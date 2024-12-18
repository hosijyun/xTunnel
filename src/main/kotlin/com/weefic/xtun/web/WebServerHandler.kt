package com.weefic.xtun.web

import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.codec.http.FullHttpRequest

class WebServerHandler(private val webConfig: WebConfig, private val bossGroup: NioEventLoopGroup) : SimpleChannelInboundHandler<FullHttpRequest>(true) {
    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        ctx.read()
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        super.channelReadComplete(ctx)
        ctx.read()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpRequest) {
        val response = this.webConfig.handler(ctx, msg, this.bossGroup)
        response.content()?.let {
            response.headers().add("Content-Length", it.readableBytes())
        }
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
    }
}