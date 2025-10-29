package com.weefic.xtun.vmess

import com.weefic.xtun.utils.Endian
import org.bouncycastle.crypto.digests.SHAKEDigest
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import kotlin.experimental.xor

interface ChunkSizeDecoder {
    fun sizeBytes(): Int
    fun decode(buffer: ByteArray): Short
}

interface ChunkSizeDecoderWithOffset : ChunkSizeDecoder {
    fun hasConstantOffset(): Short
}

interface ChunkSizeEncoder {
    fun sizeBytes(): Int
    fun encode(size: Short, buffer: ByteArray): ByteArray
}

interface PaddingLengthGenerator {
    fun maxPaddingLen(): Short
    fun nextPaddingLen(): Short
}


class PlainChunkSizeParser() : ChunkSizeEncoder, ChunkSizeDecoder {
    override fun sizeBytes(): Int {
        return 2
    }


    override fun encode(size: Short, buffer: ByteArray): ByteArray {
        Endian.BE.putShort(buffer, 0, size)
        return buffer.copyOfRange(0, 2)
    }

    override fun decode(buffer: ByteArray): Short {
        return Endian.BE.getShort(buffer, 0)
    }
}

class AEADChunkSizeParser(val auth: VMessAEADAuthenticator) : ChunkSizeEncoder, ChunkSizeDecoder, ChunkSizeDecoderWithOffset {
    override fun hasConstantOffset(): Short {
        return 16
    }

    override fun sizeBytes(): Int {
        return 2 + 16 // 2 + MAC_SIZE
    }

    override fun encode(size: Short, buffer: ByteArray): ByteArray {
        TODO("Not yet implemented")
    }

    override fun decode(buffer: ByteArray): Short {
        val iv = this.auth.nonceGenerator.next()
        val associatedText = this.auth.additionalDataGenerator.next()
        this.auth.aead.init(false, AEADParameters(KeyParameter(this.auth.aeadKey), 128, iv, associatedText))
        val result = ByteArray(2)
        var processed = this.auth.aead.processBytes(buffer, 0, buffer.size, result, 0)
        processed += this.auth.aead.doFinal(result, processed)
        check(processed == 2)
        return Endian.BE.getShort(result, 0)
    }
}


class ShakeSizeParser(nonce: ByteArray) : ChunkSizeDecoder, ChunkSizeEncoder, PaddingLengthGenerator {
    override fun sizeBytes(): Int {
        return 2
    }

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

    override fun decode(buffer: ByteArray): Short {
        val mask = this.next()
        val size = Endian.BE.getShort(buffer, 0)
        return mask xor size
    }


    override fun encode(size: Short, buffer: ByteArray): ByteArray {
        val mask = this.next()
        Endian.BE.putShort(buffer, 0, mask xor size)
        return buffer.copyOfRange(0, 2)
    }

    override fun maxPaddingLen(): Short {
        return 64
    }

    override fun nextPaddingLen(): Short {
        val mask = this.next()
        return (mask.toInt() % 64).toShort()
    }
}


