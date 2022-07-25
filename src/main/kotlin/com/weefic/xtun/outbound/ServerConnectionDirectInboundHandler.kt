package com.weefic.xtun.outbound

import com.weefic.xtun.ServerConnectionResult
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

class ServerConnectionDirectInboundHandler : ChannelInboundHandlerAdapter() {
    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        ctx.fireChannelRead(ServerConnectionResult.Success)
    }
}