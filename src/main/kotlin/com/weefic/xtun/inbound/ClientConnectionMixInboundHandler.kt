package com.weefic.xtun.inbound

import com.weefic.xtun.ClientConnection
import com.weefic.xtun.UserCredential
import com.weefic.xtun.handlers.InboundHttpRequestDecoder
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.socksx.SocksVersion

class ClientConnectionMixInboundHandler(val connectionId: Long, val users: List<UserCredential>?) : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is ByteBuf) {
            if (msg.readableBytes() > 0) {
                val firstByte = msg.getByte(msg.readerIndex())
                val pipeline = ctx.pipeline()
                if (firstByte == SocksVersion.SOCKS5.byteValue()) {
                    pipeline.addBefore(ClientConnection.NAME, "Socks", ClientConnectionSocks5InboundHandler(this.connectionId, this.users))
                } else {
                    pipeline.addBefore(ClientConnection.NAME, ClientConnectionHttpProxyInboundHandler.HTTP_DECODER_NAME, HttpRequestDecoder())
                    pipeline.addBefore(ClientConnection.NAME, "Http", ClientConnectionHttpProxyInboundHandler(this.connectionId, this.users))
                    pipeline.addBefore(ClientConnection.NAME, ClientConnectionHttpProxyInboundHandler.HTTP_ENCODER_NAME, InboundHttpRequestDecoder())
                }
                ctx.pipeline().remove(this)
            }
        }
        super.channelRead(ctx, msg)
    }
}