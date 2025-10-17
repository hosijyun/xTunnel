package com.weefic.xtun.vmess

import org.bouncycastle.crypto.Digest
import org.bouncycastle.crypto.macs.HMac

class VMessHMacDigest(private val hmac: HMac) : Digest {
    override fun getAlgorithmName(): String {
        return this.hmac.underlyingDigest.algorithmName // Hack...
    }

    override fun getDigestSize(): Int {
        return this.hmac.macSize
    }

    override fun update(input: Byte) {
        this.hmac.update(input)
    }

    override fun update(input: ByteArray, inOff: Int, len: Int) {
        this.hmac.update(input, inOff, len)
    }

    override fun doFinal(out: ByteArray, outOff: Int): Int {
        return this.hmac.doFinal(out, outOff)
    }

    override fun reset() {
        this.hmac.reset()
    }
}