package com.weefic.xtun.vmess

import com.weefic.xtun.utils.Endian
import org.bouncycastle.crypto.modes.AEADBlockCipher


interface VMessBytesGenerator {
    fun next(): ByteArray
}

class VMessAEADNonceGenerator(
    val nonce: ByteArray,
    val size: Int,
) : VMessBytesGenerator {
    private var count = 0
    override fun next(): ByteArray {
        val nonce = nonce.copyOf()
        Endian.BE.putShort(nonce, 0, this.count.toShort())
        this.count++
        return nonce.sliceArray(0 until this.size)
    }
}

class VMessEmptyBytesGenerator : VMessBytesGenerator {
    override fun next(): ByteArray {
        return ByteArray(0)
    }
}

class VMessAEADAuthenticator(
    val aeadKey: ByteArray,
    val aead: AEADBlockCipher,
    val nonceGenerator: VMessBytesGenerator,
    val additionalDataGenerator: VMessBytesGenerator,
)