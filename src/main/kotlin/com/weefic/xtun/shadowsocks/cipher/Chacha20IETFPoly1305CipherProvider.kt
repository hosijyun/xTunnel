package com.weefic.xtun.shadowsocks.cipher

import org.bouncycastle.crypto.modes.ChaCha20Poly1305

class Chacha20IETFPoly1305CipherProvider : GenericAEADCipherProvider() {
    override val keySize: Int
        get() = 32
    override val saltSize: Int
        get() = 32
    override val nonceSize: Int
        get() = 12
    override val tagSize: Int
        get() = 16

    override fun createCipher(): org.bouncycastle.crypto.modes.AEADCipher {
        return ChaCha20Poly1305()
    }
}