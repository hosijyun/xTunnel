package com.weefic.xtun.shadowsocks

import com.weefic.xtun.shadowsocks.cipher.StreamCipher
import com.weefic.xtun.shadowsocks.cipher.StreamCipherProvider
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import kotlin.random.Random

class ShadowSocksOutboundEncoder(
    private val password: String,
    private val cipherProvider: StreamCipherProvider,
) : ChannelOutboundHandlerAdapter() {
    private val iv: ByteArray
    private var ivWrote = false
    private var cipher: StreamCipher

    init {
        this.iv = ByteArray(this.cipherProvider.headerLength)
        Random.nextBytes(iv)
        this.cipher = this.cipherProvider.createCipher(true, this.password, this.iv)
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        val output = msg as ByteBuf
        this.cipher.process(output)
        if (!this.ivWrote) {
            this.ivWrote = true
            val ivBuf = ctx.alloc().buffer().writeBytes(this.iv)
            val buf = ctx.alloc().compositeBuffer(2).addComponents(true, ivBuf, output)
            ctx.write(buf, promise)
        } else {
            ctx.write(msg, promise)
        }
    }
}