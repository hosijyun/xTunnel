package com.weefic.xtun.vmess

import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import kotlin.io.encoding.Base64

interface VMessDataDecoder {
    fun process(input: ByteArray): ByteArray
}

class VMessDataPassthoughtDecoder : VMessDataDecoder {
    override fun process(input: ByteArray): ByteArray {
        return input
    }
}

class VMessAEADDataDecoder(
    val auth: VMessAEADAuthenticator,
    val sizeParser: ChunkSizeDecoder,
    val transferType: VMessTransferType,
) : VMessDataDecoder {

    init {
    }


    override fun process(input: ByteArray): ByteArray {
        val iv = this.auth.nonceGenerator.next()
        println("AEADAuthenticator/SealIV=" + Base64.encode(iv))
        val associatedText = this.auth.additionalDataGenerator.next()
        auth.aead.init(false, AEADParameters(KeyParameter(this.auth.aeadKey), 128, iv, associatedText))
        println("AEADAuthenticator/SealResult=" + Base64.encode(input))
        val result = ByteArray(input.size - 16)
        var processed = auth.aead.processBytes(input, 0, input.size, result, 0)
        processed += auth.aead.doFinal(result, processed)
        check(processed == result.size)
        return result
    }
}