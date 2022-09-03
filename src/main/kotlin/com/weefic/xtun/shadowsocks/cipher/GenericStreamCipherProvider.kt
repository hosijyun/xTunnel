package com.weefic.xtun.shadowsocks.cipher

import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV

abstract class GenericStreamCipherProvider : StreamCipherProvider {
    abstract val ivLength: Int
    abstract val keyLength: Int

    override val headerLength: Int
        get() = this.ivLength

    protected abstract fun createCipher(): org.bouncycastle.crypto.StreamCipher

    override fun createCipher(encrypt: Boolean, password: String, iv: ByteArray): StreamCipher {
        val key = ShadowSocksKeyDerive.genericDeriveKey(password, this.keyLength)
        val params = ParametersWithIV(KeyParameter(key), iv)
        val cipher = this.createCipher()
        cipher.init(encrypt, params)
        return GenericStreamCipher(cipher)
    }
}