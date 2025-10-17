package com.weefic.xtun.shadowsocks.cipher

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

abstract class AESCFBJceCipherProvider : StreamCipherProvider {
    val ivLength: Int = 16
    abstract val keyLength: Int
    override val headerLength: Int get() = this.ivLength
    private val iv = ByteArray(this.ivLength)

    override fun createCipher(encrypt: Boolean, password: String, ivData: ByteArray): StreamCipher {
        ivData.copyInto(this.iv)
        val keyData = ShadowSocksKeyDerive.genericDeriveKey(password, this.keyLength)
        val key = SecretKeySpec(keyData, "AES")
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return AESCFBJceCipher(cipher, ivData, encrypt)
    }
}