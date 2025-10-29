package com.weefic.xtun.trojan

import com.weefic.xtun.ServerConnectionRequest
import com.weefic.xtun.utils.Socks5Coder
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.ReplayingDecoder
import org.slf4j.LoggerFactory

class TrojanInboundHandler(
    connectionId: Long
) : ReplayingDecoder<Void>() {
    companion object {
        private val LOG = LoggerFactory.getLogger("client-connection-trojan")
        private val CMD_CONNECT = 0x1.toByte()
        private val CMD_UDP_ASSOCIATE = 0x3.toByte()


    }

    override fun decode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        val cmd = buf.readByte()
        when (cmd) {
            CMD_CONNECT -> {
            }

            CMD_UDP_ASSOCIATE -> {
                throw DecoderException("Unsupported command: $cmd")
            }

            else -> {
                throw DecoderException("Invalid command: $cmd")
            }
        }
        val address = Socks5Coder.decodeAddress(buf)
        val cr = buf.readByte()
        val lf = buf.readByte()
        check(cr == 0xD.toByte() && lf == 0xA.toByte()) { throw DecoderException("Bad CRLF") }

        out.add(ServerConnectionRequest(address, null))
        ctx.pipeline().remove(this)
    }
}