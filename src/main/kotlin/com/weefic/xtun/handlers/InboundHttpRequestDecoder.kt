package com.weefic.xtun.handlers

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageDecoder
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpRequestEncoder
import io.netty.util.ReferenceCountUtil

class InboundHttpRequestDecoder : MessageToMessageDecoder<HttpObject>() {
    private class InnerHttpRequestEncoder : HttpRequestEncoder() {
        public override fun encode(ctx: ChannelHandlerContext?, msg: Any?, out: List<Any?>?) {
            super.encode(ctx, msg, out)
        }
    }

    private val encoder = InnerHttpRequestEncoder()
    override fun acceptInboundMessage(msg: Any?): Boolean {
        return msg is HttpRequest || msg is HttpContent
    }

    override fun decode(ctx: ChannelHandlerContext, msg: HttpObject, out: MutableList<Any>) {
        ReferenceCountUtil.retain(msg) // Both MessageToMessageDecoder and HttpRequestEncoder will release the msg
        this.encoder.encode(ctx, msg, out)
    }
}