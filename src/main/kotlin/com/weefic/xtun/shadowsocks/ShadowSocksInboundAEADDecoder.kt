package com.weefic.xtun.shadowsocks

import com.weefic.xtun.shadowsocks.cipher.AEADCipher
import com.weefic.xtun.shadowsocks.cipher.AEADCipherProvider
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import org.slf4j.LoggerFactory

class ShadowSocksInboundAEADDecoder(
    private val password: String,
    private val cipherProvider: AEADCipherProvider
) : ByteToMessageDecoder() {
    companion object {
        private val LOG = LoggerFactory.getLogger(ShadowSocksInboundAEADDecoder::class.java)
    }

    private var cipher: AEADCipher? = null
    private var payloadLength: Int = 0


    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        var cipher = this.cipher
        if (cipher == null) {
            if (input.readableBytes() >= this.cipherProvider.saltSize) {
                val salt = ByteArray(this.cipherProvider.saltSize)
                input.readBytes(salt)
                cipher = this.cipherProvider.createCipherForShadowsocks(false, this.password, salt)
                this.cipher = cipher
            } else {
                return
            }
        }
        while (true) {
            if (this.payloadLength == 0) {
                // Read payload length
                val requireDataSize = 2 + this.cipherProvider.tagSize
                if (input.readableBytes() >= requireDataSize) {
                    val plain = cipher.process(input.readRetainedSlice(requireDataSize))
                    try {
                        this.payloadLength = plain.readShort().toInt() and 0xFFFF
                    } finally {
                        plain.release()
                    }
                } else {
                    return
                }
            } else {
                // Read payload
                val requireSize = this.payloadLength + this.cipherProvider.tagSize
                if (input.readableBytes() >= requireSize) {
                    this.payloadLength = 0
                    val plain = cipher.process(input.readRetainedSlice(requireSize))
                    out.add(plain)
                } else {
                    return
                }
            }
        }
    }
}