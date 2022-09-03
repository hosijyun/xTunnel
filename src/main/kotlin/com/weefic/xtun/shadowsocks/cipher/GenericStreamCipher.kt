package com.weefic.xtun.shadowsocks.cipher

import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator

class GenericStreamCipher(val cipher: org.bouncycastle.crypto.StreamCipher) : StreamCipher {
    override fun process(buffer: ByteBuf) {
        if (false) {
            val size = buffer.readableBytes()
            val data = ByteArray(size)
            buffer.readBytes(data)
            this.cipher.processBytes(data, 0, size, data, 0)
            buffer.clear()
            buffer.writeBytes(data)
            return
        }

        if (buffer.hasArray()) {
            val data = buffer.array()
            val dataOffset = buffer.arrayOffset()
            this.cipher.processBytes(data, dataOffset, buffer.readableBytes(), data, dataOffset)
        } else {
            val size = buffer.readableBytes()
            val heap = PooledByteBufAllocator.DEFAULT.heapBuffer(size)
            try {
                val data = heap.array()
                val dataOffset = heap.arrayOffset()
                buffer.getBytes(buffer.readerIndex(), data, dataOffset, size)
                this.cipher.processBytes(data, dataOffset, size, data, dataOffset)
                buffer.setBytes(buffer.readerIndex(), data, dataOffset, size)
            } finally {
                heap.release()
            }
        }
    }
}