package com.weefic.xtun.shadowsocks.cipher

import io.netty.buffer.ByteBuf

interface StreamCipher {
    fun process(data: ByteBuf)
}