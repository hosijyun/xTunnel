package com.weefic.xtun.shadowsocks

import com.weefic.xtun.ServerConnectionRequest
import com.weefic.xtun.utils.Socks5Coder
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ReplayingDecoder
import org.slf4j.LoggerFactory

class ShadowSocksHostDecoder() : ReplayingDecoder<Void>() {
    companion object {
        val LOG = LoggerFactory.getLogger("Host-Decoder")
    }

    override fun decode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        val address = Socks5Coder.decodeAddress(buf)
        out.add(ServerConnectionRequest(address, null))
        out.add(buf.readBytes(super.actualReadableBytes()));
        ctx.pipeline().remove(this)
    }
}