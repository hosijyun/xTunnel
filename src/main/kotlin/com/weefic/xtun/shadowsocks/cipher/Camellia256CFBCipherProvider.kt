package com.weefic.xtun.shadowsocks.cipher

import org.bouncycastle.crypto.StreamCipher
import org.bouncycastle.crypto.engines.CamelliaEngine
import org.bouncycastle.crypto.modes.CFBBlockCipher

class Camellia256CFBCipherProvider : GenericStreamCipherProvider() {
    override val ivLength: Int get() = 16
    override val keyLength: Int get() = 32

    override fun createCipher(): StreamCipher {
        return CFBBlockCipher(CamelliaEngine(), this.ivLength * 8)
    }
}