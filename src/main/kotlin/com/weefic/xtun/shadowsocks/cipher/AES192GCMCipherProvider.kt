package com.weefic.xtun.shadowsocks.cipher

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.AEADBlockCipher
import org.bouncycastle.crypto.modes.GCMBlockCipher

class AES192GCMCipherProvider : GenericAEADCipherProvider() {
    override val keySize: Int
        get() = 24
    override val saltSize: Int
        get() = 24
    override val nonceSize: Int
        get() = 12
    override val tagSize: Int
        get() = 16

    override fun createCipher(): AEADBlockCipher {
        return GCMBlockCipher(AESEngine())
    }
}