package com.weefic.xtun.utils

import java.util.*

fun UUID.toByteArray(): ByteArray {
    val bytes = ByteArray(16)
    Endian.BE.putLong(bytes, 0, this.mostSignificantBits)
    Endian.BE.putLong(bytes, 8, this.leastSignificantBits)
    return bytes
}