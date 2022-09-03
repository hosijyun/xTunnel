package com.weefic.xtun.shadowsocks.cipher

import org.bouncycastle.crypto.StreamCipher
import org.bouncycastle.crypto.engines.ChaChaEngine

class Chacha20CipherProvider : GenericStreamCipherProvider() {
    override val ivLength: Int get() = 8
    override val keyLength: Int get() = 32

    override fun createCipher(): StreamCipher {
        return ChaChaEngine()
    }
}