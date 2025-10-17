package com.weefic.xtun.vmess

class VMessCommand(
    val version: Byte,
    val requestBodyIV: ByteArray, // 16
    val requestBodyKey: ByteArray, // 16
    val responseHeader: Byte,
    val option: Byte,
    val security: Byte,
    val command: Byte,
)