package com.weefic.xtun.shadowsocks.cipher

interface StreamCipherProvider {
    val headerLength: Int
    fun createCipher(encrypt: Boolean, password: String, header: ByteArray): StreamCipher
}