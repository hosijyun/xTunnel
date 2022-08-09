package com.weefic.xtun.inbound

import com.weefic.xtun.DynamicRoute
import com.weefic.xtun.ServerConnectionRequest
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

class ClientConnectionDynamicInboundHandler(
    connectionId: Long,
    val route: DynamicRoute,
) : ChannelInboundHandlerAdapter() {

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        val serverAddress = try {
            this.route.route(ctx.channel())
        } catch (e: Exception) {
            null
        }
        if (serverAddress == null) {
            ctx.close()
        } else {
            ctx.fireChannelRead(ServerConnectionRequest(serverAddress))
        }
    }
}