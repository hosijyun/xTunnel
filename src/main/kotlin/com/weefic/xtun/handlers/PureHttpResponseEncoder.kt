package com.weefic.xtun.handlers

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.HttpResponseEncoder

class PureHttpResponseEncoder : HttpResponseEncoder() {
    override fun acceptOutboundMessage(msg: Any): Boolean {
        if (msg is ByteBuf) {
            return false
        }
        return super.acceptOutboundMessage(msg)
    }
}