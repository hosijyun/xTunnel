package com.weefic.xtun.utils

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.DecoderException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress

object Socks5Coder {
    private val ATYPE_IPV4 = 0x1.toByte()
    private val ATYPE_DOMAIN = 0x3.toByte()
    private val ATYPE_IPV6 = 0x6.toByte()

    fun decodeAddress(buf: ByteBuf): InetSocketAddress {
        val type = buf.readByte()
        val address: InetSocketAddress = when (type) {
            ATYPE_IPV4 -> { // IPV4
                val hostData = ByteArray(4)
                buf.readBytes(hostData)
                val host = Inet4Address.getByAddress(hostData)
                val port = buf.readShort().toInt() and 0xFFFF
                InetSocketAddress(host, port)
            }

            ATYPE_DOMAIN -> { // domain
                val hostLength = buf.readByte().toInt() and 0XFF
                val hostData = ByteArray(hostLength)
                buf.readBytes(hostData)
                val port = buf.readShort().toInt() and 0xFFFF
                InetSocketAddress.createUnresolved(hostData.decodeToString(), port)
            }

            ATYPE_IPV6 -> { // IPv6
                val hostData = ByteArray(16)
                buf.readBytes(hostData)
                val host = Inet6Address.getByAddress(hostData)
                val port = buf.readShort().toInt() and 0xFFFF
                InetSocketAddress(host, port)
            }

            else -> {
                throw DecoderException("Unknown host type: $type")
            }
        }
        return address
    }
}