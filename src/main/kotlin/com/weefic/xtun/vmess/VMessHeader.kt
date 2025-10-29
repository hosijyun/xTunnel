package com.weefic.xtun.vmess


enum class VMessVersion(val value: Byte) {
    V1(1.toByte());

    companion object {
        fun of(version: Byte): VMessVersion? {
            return VMessVersion.entries.firstOrNull { it.value == version }
        }
    }
}

enum class VMessSecurityType(val value: Byte) {
    Unknown(0.toByte()), // 0
    Legacy(1.toByte()), // 1
    Auto(2.toByte()), // 2
    AES128GCM(3.toByte()), //3
    Chacha20Poly1305(4.toByte()), //4
    None(5.toByte()), // 5
    Zero(6.toByte()), // 6
    ;

    companion object {
        fun of(value: Byte): VMessSecurityType? {
            return VMessSecurityType.entries.firstOrNull { it.value == value }
        }
    }

    fun isInsecureEncryption(): Boolean {
        return this == None || this == Legacy || this == Unknown
    }
}

enum class VMessTransferType {
    Stream,
    Packet,
}


data class VMessRequestAddress(val host: String, val port: Int)

class VMessHeader(
    val version: VMessVersion,
    val isAEADRequest: Boolean,
    val requestBodyIV: ByteArray,
    val requestBodyKey: ByteArray,
    val responseHeader: Byte,
    val options: Int,
    val security: VMessSecurityType,
    val command: VMessRequestCommand,
    val address: VMessRequestAddress,
)