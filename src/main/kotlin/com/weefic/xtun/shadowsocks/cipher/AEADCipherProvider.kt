package com.weefic.xtun.shadowsocks.cipher

interface AEADCipherProvider {
    val saltSize: Int
    val tagSize: Int
    fun createCipherForShadowsocks(encrypt: Boolean, password: String, salt: ByteArray): AEADCipher
}