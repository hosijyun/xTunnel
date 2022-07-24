package com.weefic.xtun.server

import com.weefic.xtun.ServerConnectionEstablishedEvent
import com.weefic.xtun.ServerConnectionNegotiationFailedEvent
import com.weefic.xtun.UserCredential
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.socksx.SocksVersion
import io.netty.handler.codec.socksx.v5.Socks5AddressType
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import io.netty.handler.codec.socksx.v5.Socks5CommandType
import org.slf4j.LoggerFactory

class ServerConnectionSocks5InboundHandler(val connectionId: Long, val host: String, val port: Int, val credential: UserCredential?) : ByteToMessageDecoder() {
    companion object {
        private val LOG = LoggerFactory.getLogger("Server-Connection-Socks5")
    }

    private enum class Status {
        Initialize,
        WaitVerifyAuthentication,
        WaitConnect,
        Streaming
    }

    private var status = Status.Initialize
    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        val credential = this.credential
        val message = ctx.alloc().buffer()
        if (credential != null) {
            val userBytes = credential.user.encodeToByteArray()
            val passwordBytes = credential.password.encodeToByteArray()
            message.writeBytes(byteArrayOf(SocksVersion.SOCKS5.byteValue(), 1, Socks5AuthMethod.PASSWORD.byteValue()))
            message.writeByte(SocksVersion.SOCKS5.byteValue().toInt())
            message.writeByte(userBytes.size)
            message.writeBytes(userBytes)
            message.writeByte(passwordBytes.size)
            message.writeBytes(passwordBytes)
        } else {
            message.writeBytes(byteArrayOf(SocksVersion.SOCKS5.byteValue(), 1, Socks5AuthMethod.NO_AUTH.byteValue()))
        }
        val hostBytes = this.host.encodeToByteArray()
        message.writeBytes(byteArrayOf(SocksVersion.SOCKS5.byteValue(), Socks5CommandType.CONNECT.byteValue(), 0x00, Socks5AddressType.DOMAIN.byteValue()))
        message.writeByte(hostBytes.size)
        message.writeBytes(hostBytes)
        message.writeByte(this.port.ushr(8))
        message.writeByte(this.port)
        ctx.writeAndFlush(message)
        ctx.read()
    }

    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        if (this.status == Status.Initialize) {
            if (msg.readableBytes() >= 2) {
                val version = SocksVersion.valueOf(msg.readByte())
                val method = Socks5AuthMethod.valueOf(msg.readByte())
                val checkVersion = if (this.credential == null) Socks5AuthMethod.NO_AUTH else Socks5AuthMethod.PASSWORD
                if (version == SocksVersion.SOCKS5 && method == checkVersion) {
                    // OK
                    this.status = if (this.credential == null) Status.WaitConnect else Status.WaitVerifyAuthentication
                } else {
                    msg.readerIndex(msg.writerIndex())
                    this.status = Status.Streaming
                    ctx.pipeline().remove(this)
                    ctx.close()
                }
            }
        } else if (this.status == Status.WaitVerifyAuthentication) {
            if (msg.readableBytes() >= 2) {
                val version = SocksVersion.valueOf(msg.readByte())
                val result = msg.readByte()
                if (version == SocksVersion.SOCKS5 && result == 0.toByte()) {
                    this.status = Status.WaitConnect
                } else {
                    msg.readerIndex(msg.writerIndex())
                    this.status = Status.Streaming
                    ctx.pipeline().remove(this)
                    ctx.close()
                }
            }
        } else if (this.status == Status.WaitConnect) {
            if (msg.readableBytes() > 4) {
                val readerIndex = msg.readerIndex()

                val version = SocksVersion.valueOf(msg.readByte())
                val status = Socks5CommandStatus.valueOf(msg.readByte())
                msg.skipBytes(1); // Reserved
                val addressType = Socks5AddressType.valueOf(msg.readByte())

                if (addressType === Socks5AddressType.IPv4) {
                    if (msg.readableBytes() >= 6) {
                        msg.skipBytes(4)
                    } else {
                        msg.readerIndex(readerIndex)
                        return
                    }
                } else if (addressType === Socks5AddressType.DOMAIN) {
                    val length = msg.readUnsignedByte().toInt()
                    if (msg.readableBytes() >= length + 2) {
                        msg.skipBytes(length)
                    } else {
                        msg.readerIndex(readerIndex)
                        return
                    }
                } else if (addressType === Socks5AddressType.IPv6) {
                    if (msg.readableBytes() >= 18) {
                        msg.skipBytes(16)
                    } else {
                        msg.readerIndex(readerIndex)
                        return
                    }
                } else {
                    msg.readerIndex(msg.writerIndex())
                    this.status = Status.Streaming
                    ctx.pipeline().remove(this)
                    ctx.close()
                    return
                }
                msg.skipBytes(2)

                if (version == SocksVersion.SOCKS5) {
                    this.status = Status.Streaming
                    if (status == Socks5CommandStatus.SUCCESS) {
                        ctx.fireChannelRead(ServerConnectionEstablishedEvent)
                        ctx.pipeline().remove(this)
                    } else {
                        msg.readerIndex(msg.writerIndex())
                        ctx.fireChannelRead(ServerConnectionNegotiationFailedEvent)
                        ctx.pipeline().remove(this)
                        ctx.close()
                    }
                } else {
                    msg.readerIndex(msg.writerIndex())
                    this.status = Status.Streaming
                    ctx.pipeline().remove(this)
                    ctx.close()
                }
            }
        }
    }
}