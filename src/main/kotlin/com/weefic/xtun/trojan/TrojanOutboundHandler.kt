package com.weefic.xtun.trojan

import com.weefic.xtun.ServerConnectionResult
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import java.security.MessageDigest

class TrojanOutboundHandler(
    val host: String,
    val port: Int,
    val password: String,
) : ChannelInboundHandlerAdapter() {
    companion object {
        private val CRLF = "\r\n".toByteArray()
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        val md = MessageDigest.getInstance("SHA-224")
        val hash = md.digest(password.toByteArray()).joinToString("") { "%02x".format(it) }
        val hashBytes = hash.toByteArray()
        val domainBytes = this.host.encodeToByteArray()

        val message = ctx.alloc().buffer(56 + 2 + 3 + domainBytes.size + 2 + 2)
        message.writeBytes(hashBytes)
        message.writeBytes(CRLF)
        message.writeByte(1) // Connect
        message.writeByte(3) // Domain Type
        message.writeByte(domainBytes.size) // Domain Length
        message.writeBytes(domainBytes) // Domain
        message.writeShort(this.port) // Port
        message.writeBytes(CRLF)

        ctx.writeAndFlush(message)
        ctx.fireChannelRead(ServerConnectionResult.Success)
    }
}