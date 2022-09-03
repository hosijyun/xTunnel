package com.weefic.xtun.shadowsocks

import com.weefic.xtun.shadowsocks.cipher.AEADCipher
import com.weefic.xtun.shadowsocks.cipher.AEADCipherProvider
import com.weefic.xtun.utils.Endian
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import java.lang.Integer.min
import kotlin.random.Random

class ShadowSocksOutboundAEADEncoder(
    private val password: String,
    private val cipherProvider: AEADCipherProvider,
) : ChannelOutboundHandlerAdapter() {
    companion object {
        private val MAX_MESSAGE_LENGTH = 0x3FFF
    }

    private val salt: ByteArray
    private var saltWrote = false
    private var cipher: AEADCipher

    init {
        this.salt = ByteArray(this.cipherProvider.saltSize)
        Random.nextBytes(this.salt)
        this.cipher = cipherProvider.createCipher(true, this.password, this.salt)
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        val data = msg as ByteBuf
        val dataSize = data.readableBytes()
        if (dataSize == 0) {
            ctx.write(msg, promise)
        } else {
            val tagSize = this.cipherProvider.tagSize
            val groupCount = (dataSize - 1) / MAX_MESSAGE_LENGTH + 1
            val messageLength = dataSize + groupCount * (2 + tagSize + tagSize)
            val message = ctx.alloc().buffer(messageLength)
            val buffer = ctx.alloc().heapBuffer(MAX_MESSAGE_LENGTH)
            buffer.ensureWritable(MAX_MESSAGE_LENGTH)
            val bufferBytes = buffer.array()
            val bufferOffset = buffer.arrayOffset()
            try {
                var processedDataSize = 0
                for (i in 0 until groupCount) {
                    val availableDataSize = dataSize - processedDataSize
                    val groupDataSize = min(availableDataSize, MAX_MESSAGE_LENGTH)
                    // 处理长度
                    Endian.BE.putShort(bufferBytes, bufferOffset, groupDataSize.toShort())
                    val encryptedGroupDataSize = this.cipher.process(bufferBytes, bufferOffset, 2)
                    message.writeBytes(encryptedGroupDataSize)
                    // 处理数据
                    data.readBytes(bufferBytes, bufferOffset, groupDataSize)
                    val encryptedData = this.cipher.process(bufferBytes, bufferOffset, groupDataSize)
                    message.writeBytes(encryptedData)
                    processedDataSize += groupDataSize
                }
                check(message.readableBytes() == messageLength)
            } catch (e: Exception) {
                ctx.close()
                message.release()
                e.printStackTrace()
                return
            } finally {
                buffer.release()
            }

            if (!this.saltWrote) {
                this.saltWrote = true
                val saltBuf = ctx.alloc().buffer().writeBytes(this.salt)
                val buf = ctx.alloc().compositeBuffer(2).addComponents(true, saltBuf, message)
                ctx.write(buf, promise)
            } else {
                ctx.write(message, promise)
            }
        }

    }
}