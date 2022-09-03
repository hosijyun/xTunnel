package com.weefic.xtun.shadowsocks

import com.weefic.xtun.ServerConnectionRequest
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

class HostPortMapping(
    val host: String,
    val port: Int,
)

open class ShadowSocksHostDecoder() : ChannelInboundHandlerAdapter() {
    companion object {
        val LOG = LoggerFactory.getLogger("Host-Decoder")
        private const val TYPE_IPV4 = 1
        private const val TYPE_DOMAIN = 3
        private const val TYPE_IPV6 = 6
    }

    private var hostDecoded = false
    private var buffer: ByteBuf? = null

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (this.hostDecoded) {
            super.channelRead(ctx, msg)
        } else {
            val message = msg as ByteBuf
            var buffer = this.buffer
            if (buffer == null) {
                if (message.isReadOnly) {
                    buffer = ctx.alloc().buffer()!!
                    this.buffer = buffer
                    buffer.writeBytes(message)
                    message.release()
                } else {
                    buffer = message
                    this.buffer = buffer
                }
            } else {
                buffer.writeBytes(message)
                message.release()
            }



            if (buffer.readableBytes() > 0) {
                buffer.markReaderIndex()
                val type = buffer.readByte().toInt()
                when (type) {
                    TYPE_IPV4 -> { // IPV4
                        if (buffer.readableBytes() >= 6) {
                            val ipValue = buffer.readInt()
                            val part1 = ipValue and 0xFF
                            val part2 = ipValue ushr 8 and 0xFF
                            val part3 = ipValue ushr 16 and 0xFF
                            val part4 = ipValue ushr 24 and 0xFF
                            val host = "$part4.$part3.$part2.$part1"
                            val port = buffer.readShort().toInt() and 0xFFFF
                            this.buffer = null
                            this.hostDecoded = true
                            this.connect(ctx, host, port, buffer)
                            return
                        }
                    }
                    TYPE_DOMAIN -> { // domain
                        if (buffer.readableBytes() >= 1) {
                            val domainLength = buffer.readByte().toInt() and 0xFF
                            if (buffer.readableBytes() >= domainLength + 2) {
                                val domain = ByteArray(domainLength)
                                buffer.readBytes(domain)

                                val host = String(domain).lowercase()
                                val port = buffer.readShort().toInt() and 0xFFFF
                                this.buffer = null
                                this.hostDecoded = true
                                this.connect(ctx, host, port, buffer)
                                return
                            }
                        }
                    }
                    TYPE_IPV6 -> { // IPv6
                        LOG.warn("IPv6 Unsupported. I'm lazy...")
                        this.buffer = null
                        this.hostDecoded = true
                        buffer.release()
                        ctx.channel().close()
                        return
                    }
                    else -> {
                        this.buffer = null
                        this.hostDecoded = true
                        this.handleUnknownType(ctx, buffer)
                        return
                    }
                }
                buffer.resetReaderIndex()
            }
            ctx.read()
        }
    }

    private fun connect(ctx: ChannelHandlerContext, host: String, port: Int, buffer: ByteBuf) {
        val mappedHostAndPort = try {
            this.map(host, port)
        } catch (e: Exception) {
            LOG.info("Map failed.", e)
            null
        }
        if (mappedHostAndPort == null) {
            LOG.info("Remote address {}:{} not allowed.", host, port)
            buffer.release()
            ctx.channel().close()
        } else {
            LOG.info("Accept $host:$port")
            try {
                ctx.fireChannelRead(ServerConnectionRequest(InetSocketAddress.createUnresolved(mappedHostAndPort.host, mappedHostAndPort.port), null))
            } finally {
                super.channelRead(ctx, buffer)
            }
        }
    }

    open fun map(host: String, port: Int): HostPortMapping? {
        return HostPortMapping(host, port)
    }

    open fun handleUnknownType(ctx: ChannelHandlerContext, buffer: ByteBuf) {
        buffer.release()
        ctx.close()
    }


    override fun channelInactive(ctx: ChannelHandlerContext) {
        this.buffer?.release()
        this.buffer = null
        super.channelInactive(ctx)
    }
}