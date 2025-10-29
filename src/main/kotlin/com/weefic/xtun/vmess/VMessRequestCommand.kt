package com.weefic.xtun.vmess

enum class VMessRequestCommand(val value: Byte) {
    TCP(0x01.toByte()),
    UDP(0x02.toByte()),
    MUX(0x03.toByte());

    companion object {
        fun of(value: Byte): VMessRequestCommand? {
            return VMessRequestCommand.entries.firstOrNull { it.value == value }
        }
    }

    fun getTransferType(): VMessTransferType {
        return when (this) {
            TCP, MUX -> VMessTransferType.Stream
            UDP -> VMessTransferType.Packet
        }
    }
}