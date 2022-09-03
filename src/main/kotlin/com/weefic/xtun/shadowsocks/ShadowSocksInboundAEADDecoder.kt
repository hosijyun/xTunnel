package com.weefic.xtun.shadowsocks

import com.weefic.xtun.shadowsocks.cipher.AEADCipher
import com.weefic.xtun.shadowsocks.cipher.AEADCipherProvider
import com.weefic.xtun.utils.Endian
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder

class ShadowSocksInboundAEADDecoder(
    private val password: String,
    private val cipherProvider: AEADCipherProvider
) : ByteToMessageDecoder() {
    private var cipher: AEADCipher? = null
    private var payloadLength: Int = 0


    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        var cipher = this.cipher
        if (cipher == null) {
            if (input.readableBytes() >= this.cipherProvider.saltSize) {
                val header = ByteArray(this.cipherProvider.saltSize)
                input.readBytes(header)
                cipher = this.cipherProvider.createCipher(false, this.password, header)
                this.cipher = cipher
            } else {
                return
            }
        }
        while (true) {
            if (this.payloadLength == 0) {
                // Read payload length
                if (input.readableBytes() >= 2 + this.cipherProvider.tagSize) {
                    val encryptedPayloadLength = ByteArray(2 + this.cipherProvider.tagSize)
                    input.readBytes(encryptedPayloadLength)
                    try {
                        val decryptedPayloadLength = cipher.process(encryptedPayloadLength)
                        this.payloadLength = Endian.BE.getShort(decryptedPayloadLength, 0).toInt()
                        check(this.payloadLength > 0 && this.payloadLength <= 0x3FFF) { "Bad payload length : $payloadLength" }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        ctx.close()
                        return
                    }
                } else {
                    return
                }
            } else {
                // Read payload
                if (input.readableBytes() >= this.payloadLength + this.cipherProvider.tagSize) {
                    val encryptedPayload = ByteArray(this.payloadLength + this.cipherProvider.tagSize)
                    input.readBytes(encryptedPayload)
                    this.payloadLength = 0
                    try {
                        val decryptedPayload = cipher.process(encryptedPayload)
                        out.add(Unpooled.wrappedBuffer(decryptedPayload))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        ctx.close()
                        return
                    }
                } else {
                    return
                }
            }
        }
    }
}