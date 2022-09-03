package com.weefic.xtun.shadowsocks.cipher

interface AEADCipher {
    fun process(data: ByteArray): ByteArray {
        return this.process(data, 0, data.size)
    }

    fun process(data: ByteArray, offset: Int, length: Int): ByteArray
}