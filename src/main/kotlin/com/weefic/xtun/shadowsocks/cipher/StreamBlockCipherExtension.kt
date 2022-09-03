package com.weefic.xtun.shadowsocks.cipher

import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import org.bouncycastle.crypto.StreamBlockCipher

fun StreamBlockCipher.process(input: ByteBuf) {
    if (false) {
        val size = input.readableBytes()
        val data = ByteArray(size)
        input.readBytes(data)
        this.processBytes(data, 0, size, data, 0)
        input.clear()
        input.writeBytes(data)
        return
    }

    if (input.hasArray()) {
        val data = input.array()
        val dataOffset = input.arrayOffset()
        this.processBytes(data, dataOffset, input.readableBytes(), data, dataOffset)
    } else {
        val size = input.readableBytes()
        val heap = PooledByteBufAllocator.DEFAULT.heapBuffer(size)
        try {
            heap.writeBytes(input)
            val data = heap.array()
            val dataOffset = heap.arrayOffset()
            this.processBytes(data, dataOffset, size, data, dataOffset)
            input.clear()
            input.writeBytes(data, dataOffset, size)
        } finally {
            heap.release()
        }
    }
}