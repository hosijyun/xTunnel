package com.weefic.xtun.shadowsocks.cipher

import io.netty.buffer.ByteBuf
import javax.crypto.Cipher
import kotlin.experimental.xor

class AESCFBJceCipher(val cipher: Cipher, iv: ByteArray, val encrypt: Boolean) : StreamCipher {
    private val iv = ByteArray(16)
    private var processed = 0

    init {
        iv.copyInto(this.iv)
    }

    override fun process(buffer: ByteBuf) {
        val size = buffer.readableBytes()
        if (buffer.hasArray()) {
            val iv = this.iv
            val data = buffer.array()
            var dataIndex = buffer.arrayOffset() + buffer.readerIndex()
            val dataEnd = dataIndex + size
            while (dataIndex < dataEnd) {
                val index = this.processed and 0xF
                if (index == 0) {
                    this.cipher.update(iv, 0, 16, iv, 0)
                }
                if (this.encrypt) {
                    val b = data[dataIndex] xor iv[index]
                    data[dataIndex] = b
                    iv[index] = b
                } else {
                    val d = data[dataIndex]
                    val b = d xor iv[index]
                    iv[index] = d
                    data[dataIndex] = b
                }
                dataIndex++
                this.processed++
            }
        } else {
            val dataOffset = buffer.readerIndex()
            var dataIndex = 0
            while (dataIndex < size) {
                val index = this.processed and 0xF
                if (index == 0) {
                    this.cipher.update(this.iv, 0, 16, this.iv, 0)
                }
                if (this.encrypt) {
                    val b = buffer.getByte(dataOffset + dataIndex) xor this.iv[index]
                    buffer.setByte(dataOffset + dataIndex, b.toInt())
                    this.iv[index] = b
                } else {
                    val d = buffer.getByte(dataOffset + dataIndex)
                    val b = d xor iv[index]
                    iv[index] = d
                    buffer.setByte(dataOffset + dataIndex, b.toInt())
                }
                dataIndex++


                this.processed++
            }
        }
    }

    override fun process(data: ByteArray) {
        val iv = this.iv
        var dataIndex = 0
        val dataEnd = dataIndex + data.size
        while (dataIndex < dataEnd) {
            val index = this.processed and 0xF
            if (index == 0) {
                this.cipher.update(iv, 0, 16, iv, 0)
            }
            if (this.encrypt) {
                val b = data[dataIndex] xor iv[index]
                data[dataIndex] = b
                iv[index] = b
            } else {
                val d = data[dataIndex]
                val b = d xor iv[index]
                iv[index] = d
                data[dataIndex] = b
            }
            dataIndex++
            this.processed++
        }
    }

    override fun close() {
    }
}