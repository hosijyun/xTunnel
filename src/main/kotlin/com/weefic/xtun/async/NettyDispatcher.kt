package com.weefic.xtun.async

import io.netty.channel.EventLoop
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext

class NettyDispatcher(
    private val eventLoop: EventLoop,
) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        this.eventLoop.execute(block)
    }
}