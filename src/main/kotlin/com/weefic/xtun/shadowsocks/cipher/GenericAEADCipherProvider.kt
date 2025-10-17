package com.weefic.xtun.shadowsocks.cipher

import org.bouncycastle.crypto.digests.SHA1Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter

abstract class GenericAEADCipherProvider : AEADCipherProvider {
    abstract val keySize: Int
    abstract val nonceSize: Int

    protected abstract fun createCipher(): org.bouncycastle.crypto.modes.AEADCipher

    override fun createCipherForShadowsocks(encrypt: Boolean, password: String, salt: ByteArray): AEADCipher {
        val key = ShadowSocksKeyDerive.genericDeriveKey(password, this.keySize)
        val subKey = getShadowsocksSubKey(key, salt)
        val subKeyParameter = KeyParameter(subKey)
        return GenericAEADCipher(this.createCipher(), encrypt, subKeyParameter, this.tagSize, this.nonceSize)
    }

    private fun getShadowsocksSubKey(key: ByteArray, salt: ByteArray): ByteArray {
        val subKey = ByteArray(this.keySize)
        val generator = HKDFBytesGenerator(SHA1Digest())
        val parameters = HKDFParameters(key, salt, "ss-subkey".toByteArray())
        generator.init(parameters)
        generator.generateBytes(subKey, 0, this.keySize)
        return subKey
    }
}