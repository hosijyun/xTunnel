package com.weefic.xtun.vmess

import org.bouncycastle.crypto.MultiBlockCipher
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.params.KeyParameter

object VMessAuthID {
    fun newCipherFromKey(key: ByteArray, forEncryption: Boolean): MultiBlockCipher {
        val cipher = AESEngine.newInstance()
        val derivedKey = VMessKDF.kdf16(key, VMessConsts.KDFSaltConstAuthIDEncryptionKey)
        cipher.init(forEncryption, KeyParameter(derivedKey))
        return cipher
    }
}