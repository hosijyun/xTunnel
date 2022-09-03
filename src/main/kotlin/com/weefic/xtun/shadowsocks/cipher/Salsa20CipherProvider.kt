package com.weefic.xtun.shadowsocks.cipher

import org.bouncycastle.crypto.StreamCipher
import org.bouncycastle.crypto.engines.Salsa20Engine

class Salsa20CipherProvider : GenericStreamCipherProvider() {
    override val ivLength: Int get() = 8
    override val keyLength: Int get() = 32

    override fun createCipher(): StreamCipher {
        return Salsa20Engine()
    }
}