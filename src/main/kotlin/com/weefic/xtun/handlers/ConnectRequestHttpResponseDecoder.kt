package com.weefic.xtun.handlers

import io.netty.handler.codec.http.HttpMessage
import io.netty.handler.codec.http.HttpResponseDecoder

class ConnectRequestHttpResponseDecoder : HttpResponseDecoder() {
    override fun isContentAlwaysEmpty(msg: HttpMessage): Boolean {
        return true
    }
}