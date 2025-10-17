package com.weefic.xtun.shadowsocks.cipher

class AES192CFBJceCipherProvider : AESCFBJceCipherProvider() {
    override val keyLength: Int get() = 24
}