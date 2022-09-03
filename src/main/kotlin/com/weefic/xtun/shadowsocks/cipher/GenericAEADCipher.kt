package com.weefic.xtun.shadowsocks.cipher

import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter

class GenericAEADCipher(val cipher: org.bouncycastle.crypto.modes.AEADCipher, val encrypt: Boolean, val subKey: KeyParameter, val tagSize: Int, val nonceSize: Int) : AEADCipher {
    private val nonce = ByteArray(this.nonceSize)

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