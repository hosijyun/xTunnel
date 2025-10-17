package com.weefic.xtun.shadowsocks.cipher

import io.netty.buffer.ByteBuf
import java.io.Closeable

interface StreamCipher : Closeable {
    fun process(data: ByteBuf)
    fun process(data: ByteArray)
}