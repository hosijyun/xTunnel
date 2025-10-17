package com.weefic.xtun.vmess

import com.weefic.xtun.utils.Endian
import org.bouncycastle.crypto.digests.SHAKEDigest
import kotlin.experimental.xor

interface VMessChunkSizeCoder {
    val sizeBytes: Int
    fun encode(size: Short, buffer: ByteArray): ByteArray
    fun decode(buffer: ByteArray): Short
}


class PlainChunkSizeCoder() : VMessChunkSizeCoder {
    override val sizeBytes: Int = 2
    override fun encode(size: Short, buffer: ByteArray): ByteArray {
        Endian.BE.putShort(buffer, 0, size)
        return buffer.copyOfRange(0, 2)
    }

    override fun decode(buffer: ByteArray): Short {
        return Endian.BE.getShort(buffer, 0)
    }
}

class ShakeSizeCoder(nonce: ByteArray) : VMessChunkSizeCoder {
    override val sizeBytes: Int = 2


    private val shake = SHAKEDigest(128)
    private val buffer = ByteArray(2)

    init {
        this.shake.update(nonce, 0, nonce.size)
    }

    private fun next(): Short {
        this.shake.doOutput(this.buffer, 0, 2)
        val mask = Endian.BE.getShort(this.buffer, 0)
        return mask
    }

    override fun encode(size: Short, buffer: ByteArray): ByteArray {
        val mask = this.next()
        Endian.BE.putShort(buffer, 0, mask xor size)
        return buffer.copyOfRange(0, 2)
    }

    override fun decode(buffer: ByteArray): Short {
        val mask = this.next()
        val value = Endian.BE.getShort(buffer, 0)
        return mask xor value
    }
}