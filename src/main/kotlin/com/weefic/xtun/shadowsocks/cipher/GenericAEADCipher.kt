package com.weefic.xtun.shadowsocks.cipher

import io.netty.buffer.ByteBuf
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter

class GenericAEADCipher(
    val cipher: org.bouncycastle.crypto.modes.AEADCipher,
    val encrypt: Boolean,
    val subKey: KeyParameter,
    val tagSize: Int,
    val nonceSize: Int
) : AEADCipher {
    private val nonce = ByteArray(this.nonceSize)

    override fun process(buffer: ByteBuf): ByteBuf {
        try {
            val bufferSize = buffer.readableBytes()
            val requireSize = bufferSize + if (this.encrypt) this.tagSize else -this.tagSize
            if (buffer.hasArray() && buffer.capacity() >= requireSize) {
                val data = buffer.array()
                val dataOffset = buffer.arrayOffset() + buffer.readerIndex()

                this.cipher.init(this.encrypt, AEADParameters(this.subKey, this.tagSize * 8, this.nonce))
                var processed = this.cipher.processBytes(data, dataOffset, bufferSize, data, dataOffset)
                processed += this.cipher.doFinal(data, dataOffset + processed)
                check(processed == requireSize)
                this.incrementNonce()

                buffer.writerIndex(buffer.readerIndex() + requireSize)
                return buffer.retain() // release in finally block
            } else {
                val heapSize = if (this.encrypt) bufferSize + this.tagSize else bufferSize
                val heapBuffer = buffer.alloc().heapBuffer(heapSize)
                try {
                    heapBuffer.writeBytes(buffer)
                    val data = heapBuffer.array()
                    val dataOffset = heapBuffer.arrayOffset() + heapBuffer.readerIndex()


                    this.cipher.init(this.encrypt, AEADParameters(this.subKey, this.tagSize * 8, this.nonce))
                    var processed = this.cipher.processBytes(data, dataOffset, bufferSize, data, dataOffset)
                    processed += this.cipher.doFinal(data, dataOffset + processed)
                    check(processed == requireSize)
                    this.incrementNonce()

                    heapBuffer.writerIndex(heapBuffer.readerIndex() + requireSize)
                } catch (t: Throwable) {
                    heapBuffer.release()
                    throw t
                }
                return heapBuffer
            }
        } finally {
            buffer.release()
        }
    }

    override fun process(data: ByteArray, offset: Int, length: Int): ByteArray {
        this.cipher.init(this.encrypt, AEADParameters(this.subKey, this.tagSize * 8, this.nonce))
        val result = if (this.encrypt) {
            ByteArray(length + this.tagSize)
        } else {
            ByteArray(length - this.tagSize)
        }
        var processed = 0
        processed += this.cipher.processBytes(data, offset, length, result, processed)
        processed += this.cipher.doFinal(result, processed)
        if (processed != result.size) {
            throw IllegalStateException()
        }
        this.incrementNonce()
        return result
    }

    private fun incrementNonce() {
        for (i in this.nonce.indices) {
            this.nonce[i]++
            if (this.nonce[i].toInt() != 0) {
                break
            }
        }
    }
}