package com.weefic.xtun.trojan

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.min


class TrojanHeaderInboundHandler(
    val connectionId: Long,
    val passwords: List<String>
) : ByteToMessageDecoder() {
    companion object {
        const val HANDLER_NAME = "TrojanHeader"
    }

    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        val dataSize = input.readableBytes()
        val readSize = min(58, dataSize)
        val data = ByteArray(readSize)
        input.getBytes(input.readerIndex(), data)
        // Find \r\n
        val carryIndex = data.indexOf(0xD)
        if (carryIndex == 56 && data[57] == 0xA.toByte()) {
            // Check passwords...
            val trojanHash = String(data, 0, 56, StandardCharsets.UTF_8)
            val md = MessageDigest.getInstance("SHA-224")
            for (password in this.passwords) {
                val hash = md.digest(password.toByteArray()).joinToString("") { "%02x".format(it) }
                if (trojanHash == hash) {
                    // Strip header
                    input.readerIndex(input.readerIndex() + 58)
                    ctx.pipeline().addAfter(HANDLER_NAME, null, TrojanInboundHandler(this.connectionId))
                    ctx.pipeline().remove(HANDLER_NAME)
                    return
                }
            }
        }
        val p = ctx.pipeline()
        p.addAfter(HANDLER_NAME, "decoder", HttpRequestDecoder())
        p.addAfter("decoder", "encoder", HttpResponseEncoder())
        p.addAfter("encoder", "aggregator", HttpObjectAggregator(1048576))
        p.addAfter("aggregator", null, TrojanHttpInboundHandler())
        p.remove(HANDLER_NAME)
    }
}