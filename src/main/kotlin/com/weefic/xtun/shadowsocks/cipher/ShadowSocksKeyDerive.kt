package com.weefic.xtun.shadowsocks.cipher

import org.bouncycastle.crypto.digests.MD5Digest
import java.nio.charset.StandardCharsets

class ShadowSocksKeyDerive {
    companion object {
        /**
         * 导出Key
         */
        fun genericDeriveKey(password: String, keySize: Int): ByteArray {
            val digest = MD5Digest()
            val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
            val digestSize = digest.digestSize
            val alignedKeySize = (keySize + digestSize - 1) / digestSize * digestSize
            val key = ByteArray(alignedKeySize)
            var processed = 0
            while (processed < keySize) {
                digest.update(key, 0, processed)
                digest.update(passwordBytes, 0, passwordBytes.size)
                processed += digest.doFinal(key, processed)
                digest.reset()
            }
            return key.copyOf(keySize)
        }
    }
}