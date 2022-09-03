package com.weefic.xtun.shadowsocks.cipher

import org.bouncycastle.crypto.StreamCipher
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.CFBBlockCipher

class AES192CFBCipherProvider : GenericStreamCipherProvider() {
    override val ivLength: Int get() = 16
    override val keyLength: Int get() = 24

    override fun createCipher(): StreamCipher {
        return CFBBlockCipher(AESEngine(), this.ivLength * 8)
    }
}