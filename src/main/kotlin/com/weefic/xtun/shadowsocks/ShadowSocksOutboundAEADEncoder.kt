package com.weefic.xtun.shadowsocks

import com.weefic.xtun.shadowsocks.cipher.AEADCipher
import com.weefic.xtun.shadowsocks.cipher.AEADCipherProvider
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import org.slf4j.LoggerFactory
import java.lang.Integer.min
import kotlin.random.Random

class ShadowSocksOutboundAEADEncoder(
    private val password: String,
    private val cipherProvider: AEADCipherProvider,
) : ChannelOutboundHandlerAdapter() {
    companion object {
        private val LOG = LoggerFactory.getLogger(ShadowSocksOutboundAEADEncoder::class.java)

        // Payload length is a 2-byte big-endian unsigned integer capped at 0x3FFF.
        // The higher two bits are reserved and must be set to zero.
        // Payload is therefore limited to 16*1024 - 1 bytes.
        // @see https://github.com/shadowsocks/shadowsocks-org/wiki/AEAD-Ciphers
        private val MAX_PAYLOAD_SIZE = 0x3FFF
    }

    private val salt: ByteArray
    private var saltWrote = false
    private var cipher: AEADCipher

    init {
        this.salt = ByteArray(this.cipherProvider.saltSize)
        Random.nextBytes(this.salt)
        this.cipher = cipherProvider.createCipherForShadowsocks(true, this.password, this.salt)
    }

    override fun write(ctx: ChannelHandlerContext, data: Any, promise: ChannelPromise) {
        if (data !is ByteBuf) {
            ctx.write(data, promise)
            return
        }
        val dataSize = data.readableBytes()
        if (dataSize == 0) {
            ctx.write(data, promise)
            return
        }
        val message = ctx.alloc().compositeBuffer()
        try {
            if (!this.saltWrote) {
                this.saltWrote = true
                message.addComponent(true, Unpooled.wrappedBuffer(this.salt))
            }
            while (true) {
                val readSize = min(MAX_PAYLOAD_SIZE, data.readableBytes())
                if (readSize == 0) {
                    break
                }
                val sizeBuf = ctx.alloc().heapBuffer(2 + this.cipherProvider.tagSize)
                sizeBuf.writeShort(readSize)
                message.addComponent(true, this.cipher.process(sizeBuf))

                val dataBuf = data.readRetainedSlice(readSize)
                message.addComponent(true, this.cipher.process(dataBuf))
            }
            ctx.write(message.retain(), promise)
        } finally {
            message.release()
            data.release()
        }
    }
}