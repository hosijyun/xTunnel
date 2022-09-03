package com.weefic.xtun.shadowsocks.cipher

import org.bouncycastle.crypto.StreamCipher
import org.bouncycastle.crypto.engines.ChaCha7539Engine

class Chacha20IETFCipherProvider : GenericStreamCipherProvider() {
    override val ivLength: Int get() = 12
    override val keyLength: Int get() = 32

    override fun createCipher(): StreamCipher {
        return ChaCha7539Engine()
    }
}