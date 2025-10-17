package com.weefic.xtun.utils

import java.util.*

fun ByteArray.toUUID(): UUID {
    check(this.size == 16) { "ByteArray size must be 16" }
    return UUID(
        Endian.BE.getLong(this, 0),
        Endian.BE.getLong(this, 8)
    )
}