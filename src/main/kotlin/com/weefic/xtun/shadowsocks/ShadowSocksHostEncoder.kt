package com.weefic.xtun.shadowsocks

import com.weefic.xtun.ServerConnectionResult
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

class ShadowSocksHostEncoder(val host: String, val port: Int) : ChannelInboundHandlerAdapter() {
    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        val hostBytes = host.encodeToByteArray()
        val message = ctx.alloc().buffer(1 + 1 + hostBytes.size + 2)
        message.writeByte(3)
        message.writeByte(hostBytes.size)
        message.writeBytes(hostBytes)
        message.writeShort(this.port)
        ctx.writeAndFlush(message)
        ctx.fireChannelRead(ServerConnectionResult.Success)
    }
}