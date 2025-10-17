package com.weefic.xtun.shadowsocks.cipher

import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator

class GenericStreamCipher(val cipher: org.bouncycastle.crypto.StreamCipher) : StreamCipher {
    override fun process(buffer: ByteBuf) {
        if (buffer.hasArray()) {
            val data = buffer.array()
            val dataOffset = buffer.arrayOffset() + buffer.readerIndex()
            val dataSize = buffer.readableBytes()
            val processedBytes = this.cipher.processBytes(data, dataOffset, dataSize, data, dataOffset)
            check(dataSize == processedBytes)
        } else {
            val size = buffer.readableBytes()
            val heap = PooledByteBufAllocator.DEFAULT.heapBuffer(size)
            try {
                buffer.getBytes(buffer.readerIndex(), heap)
                val data = heap.array()
                val dataOffset = heap.arrayOffset() + heap.readerIndex()
                val processedBytes = this.cipher.processBytes(data, dataOffset, size, data, dataOffset)
                check(processedBytes == size)
                buffer.setBytes(buffer.readerIndex(), heap)
            } finally {
                heap.release()
            }

        }
    }

    override fun process(data: ByteArray) {
        this.cipher.processBytes(data, 0, data.size, data, 0)
    }

    override fun close() {
    }
}