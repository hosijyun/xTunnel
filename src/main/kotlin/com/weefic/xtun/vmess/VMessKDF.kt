package com.weefic.xtun.vmess

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter

object VMessKDF {
    fun kdf(key: ByteArray, vararg path: ByteArray): ByteArray {
        var hmac = HMac(SHA256Digest())
        hmac.init(KeyParameter(VMessConsts.KDFSaltConstVMessAEADKDF))
        for (p in path) {
            hmac = HMac(VMessHMacDigest(hmac))
            hmac.init(KeyParameter(p))
        }
        hmac.update(key, 0, key.size)
        val out = ByteArray(hmac.macSize)
        hmac.doFinal(out, 0)
        return out
    }

    fun kdf16(key: ByteArray, vararg path: ByteArray): ByteArray {
        return kdf(key, *path).copyOfRange(0, 16)
    }
}