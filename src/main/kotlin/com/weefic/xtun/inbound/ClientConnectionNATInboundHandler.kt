package com.weefic.xtun.inbound

import com.weefic.xtun.ServerConnectionRequest
import com.weefic.xtun.Tunnel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

class ClientConnectionNATInboundHandler(
    connectionId: Long,
    val serverHost: String,
    val serverPort: Int,
) : ChannelInboundHandlerAdapter() {
    companion object {
        private val LOG = LoggerFactory.getLogger("client-connection-socks5")
    }

    private val TAG = Tunnel.MARKERS.getDetachedMarker("-$connectionId")
    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        ctx.fireChannelRead(ServerConnectionRequest(InetSocketAddress.createUnresolved(this.serverHost, this.serverPort), null))
    }

    private fun ChannelHandlerContext.writeData(data: ByteArray): ChannelFuture {
        return this.writeAndFlush(this.alloc().buffer().writeBytes(data))
    }
}