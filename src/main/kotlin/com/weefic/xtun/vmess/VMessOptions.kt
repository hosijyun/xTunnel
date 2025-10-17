package com.weefic.xtun.vmess

object VMessOptions {
    const val RequestOptionChunkStream = 0x01
    const val RequestOptionConnectionReuse = 0x02
    const val RequestOptionChunkMasking = 0x04
    const val RequestOptionGlobalPadding = 0x08
    const val RequestOptionAuthenticatedLength = 0x10
}