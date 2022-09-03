package com.weefic.xtun.shadowsocks.cipher

interface AEADCipherProvider {
    val saltSize: Int
    val tagSize: Int
    fun createCipher(encrypt: Boolean, password: String, header: ByteArray): AEADCipher
}