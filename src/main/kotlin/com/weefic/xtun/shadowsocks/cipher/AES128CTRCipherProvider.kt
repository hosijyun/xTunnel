package com.weefic.xtun.shadowsocks.cipher

import org.bouncycastle.crypto.StreamCipher
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.SICBlockCipher

class AES128CTRCipherProvider : GenericStreamCipherProvider() {
    override val ivLength: Int get() = 16
    override val keyLength: Int get() = 16

    override fun createCipher(): StreamCipher {
        return SICBlockCipher(AESEngine())
    }
}