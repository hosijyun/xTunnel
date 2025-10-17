package com.weefic.xtun.shadowsocks.cipher

class AES256CFBJceCipherProvider : AESCFBJceCipherProvider() {
    override val keyLength: Int get() = 32
}