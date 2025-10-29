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
    private val cipher: StreamCipher
    private var ivWrote = false

    init {
        this.iv = ByteArray(this.cipherProvider.headerLength)
        Random.nextBytes(iv)
        this.cipher = this.cipherProvider.createCipher(true, this.password, this.iv)
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg is ByteBuf) {
            try {
                this.cipher.process(msg)
            } catch (e: Exception) {
                msg.release()
                throw e
            }
            if (!this.ivWrote) {
                this.ivWrote = true
                val ivBuf = ctx.alloc().buffer().writeBytes(this.iv)
                val buf = ctx.alloc().compositeBuffer(2).addComponents(true, ivBuf, msg)
                ctx.write(buf, promise)
            } else {
                ctx.write(msg, promise)
            }
        } else {
            ctx.write(msg, promise)
        }
    }
}