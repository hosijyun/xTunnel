package com.weefic.xtun.vmess

interface VMessInboundProcessor {
    fun process(data: ByteArray): ByteArray
}