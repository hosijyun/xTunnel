package com.weefic.xtun.server

import com.weefic.xtun.ServerConnectionEstablishedEvent
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

class ServerConnectionDirectInboundHandler : ChannelInboundHandlerAdapter() {
    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        ctx.fireChannelRead(ServerConnectionEstablishedEvent)
    }
}