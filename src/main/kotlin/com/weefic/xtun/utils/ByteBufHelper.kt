package com.weefic.xtun.utils

import io.netty.buffer.ByteBuf

fun ByteBuf.getText(): String {
    val buffer = ByteArray(this.readableBytes())
    this.getBytes(this.readerIndex(), buffer)
    return String(buffer)
}