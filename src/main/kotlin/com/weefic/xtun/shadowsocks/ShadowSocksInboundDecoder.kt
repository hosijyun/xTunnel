package com.weefic.xtun.shadowsocks

import com.weefic.xtun.shadowsocks.cipher.StreamCipher
import com.weefic.xtun.shadowsocks.cipher.StreamCipherProvider
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import java.lang.Integer.min

class ShadowSocksInboundDecoder(
    private val password: String,
    private val cipherProvider: StreamCipherProvider,
) : ChannelInboundHandlerAdapter() {
    private val header: ByteArray
    private var headRead = 0
    private var cipher: StreamCipher? = null

    init {
        this.header = ByteArray(this.cipherProvider.headerLength)
    }

    private fun getCipher(input: ByteBuf): StreamCipher? {
        val theCipher = this.cipher
        if (theCipher == null) {
            if (this.headRead < this.header.size) {
                val maxRead = min(input.readableBytes(), this.header.size - this.headRead)
                input.readBytes(this.header, this.headRead, maxRead)
                this.headRead += maxRead
            }
            if (this.headRead == this.header.size) {
                this.cipher = this.cipherProvider.createCipher(false, this.password, this.header)
            }
        }
        return this.cipher
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val input = msg as ByteBuf
        val cipher = this.getCipher(input)
        if (cipher == null) {
            // Cipher is not ready yet
            input.release()
            if (!ctx.channel().config().isAutoRead) {
                ctx.channel().read()
            }
        } else {
            cipher.process(input)
            super.channelRead(ctx, input)
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        this.cipher?.close()
        this.cipher = null
        super.channelActive(ctx)
    }
}