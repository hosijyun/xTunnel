package com.weefic.xtun.web

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse

class WebConfig(
    val host: String?,
    val port: Int,
    val handler: (ctx: ChannelHandlerContext, request: FullHttpRequest, bossGroup: NioEventLoopGroup) -> FullHttpResponse
)