package com.weefic.xtun.shadowsocks.cipher


class AES128CFBJceCipherProvider : AESCFBJceCipherProvider() {
    override val keyLength: Int get() = 16
}