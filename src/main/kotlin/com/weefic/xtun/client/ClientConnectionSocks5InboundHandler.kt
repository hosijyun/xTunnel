package com.weefic.xtun.client

import com.weefic.xtun.ServerConnectionEstablishedEvent
import com.weefic.xtun.ServerConnectionNegotiationFailedEvent
import com.weefic.xtun.ServerConnectionRequest
import com.weefic.xtun.UserCredential
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.socksx.SocksVersion
import io.netty.handler.codec.socksx.v5.Socks5AddressType
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import io.netty.handler.codec.socksx.v5.Socks5CommandType
import io.netty.util.CharsetUtil
import io.netty.util.NetUtil

class ClientConnectionSocks5InboundHandler(connectionId: Long, val userCredential: UserCredential?) : ByteToMessageDecoder() {
    private enum class Status {
        Initialize,
        WaitAuthentication,
        WaitServerInfo,
        WaitConnection,
        Closed,
    }

    private var status = Status.Initialize

    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        if (this.status == Status.Initialize) {
            if (msg.readableBytes() > 2) {
                val readerIndex = msg.readerIndex()

                val version = SocksVersion.valueOf(msg.readByte())
                val countOfMethods = msg.readByte().toInt() and 0xFF
                if (msg.readableBytes() >= countOfMethods) {
                    val methods = ByteArray(countOfMethods)
                    msg.readBytes(methods)

                    val acceptMethod = if (this.userCredential == null) Socks5AuthMethod.NO_AUTH else Socks5AuthMethod.PASSWORD
                    if (version == SocksVersion.SOCKS5) {
                        if (methods.contains(acceptMethod.byteValue())) {
                            ctx.writeAndFlush(ctx.alloc().buffer().writeBytes(byteArrayOf(SocksVersion.SOCKS5.byteValue(), acceptMethod.byteValue())))
                            this.status = if (this.userCredential == null) Status.WaitServerInfo else Status.WaitAuthentication
                        } else {
                            ctx.writeAndFlush(ctx.alloc().buffer().writeBytes(byteArrayOf(SocksVersion.SOCKS5.byteValue(), Socks5AuthMethod.UNACCEPTED.byteValue()))).addListener(ChannelFutureListener.CLOSE)
                            msg.readerIndex(msg.writerIndex())
                            this.status = Status.Closed
                        }
                    } else {
                        msg.readerIndex(msg.writerIndex())
                        this.status = Status.Closed
                        ctx.close()
                    }
                } else {
                    msg.readerIndex(readerIndex)
                }
            }
        } else if (this.status == Status.WaitAuthentication) {
            if (msg.readableBytes() > 2) {
                val credential = this.userCredential!!
                val readerIndex = msg.readerIndex()

                val version = SocksVersion.valueOf(msg.readByte())
                val uLength = msg.readByte().toInt() and 0xFF
                if (msg.readableBytes() > uLength) {
                    val user = ByteArray(uLength)
                    msg.readBytes(user)
                    val pLength = msg.readByte().toInt() and 0xFF
                    if (msg.readableBytes() >= pLength) {
                        val password = ByteArray(pLength)
                        msg.readBytes(password)
                        if (credential.user.encodeToByteArray().contentEquals(user) && credential.password.encodeToByteArray().contentEquals(password)) {
                            ctx.writeAndFlush(
                                ctx.alloc().buffer().writeBytes(
                                    byteArrayOf(
                                        SocksVersion.SOCKS5.byteValue(),
                                        Socks5CommandStatus.SUCCESS.byteValue()
                                    )
                                )
                            )
                            this.status = Status.WaitServerInfo
                        } else {
                            msg.readerIndex(msg.writerIndex())
                            ctx.writeAndFlush(
                                ctx.alloc().buffer().writeBytes(
                                    byteArrayOf(
                                        SocksVersion.SOCKS5.byteValue(),
                                        Socks5CommandStatus.FAILURE.byteValue()
                                    )
                                )
                            ).addListener(ChannelFutureListener.CLOSE)
                            this.status = Status.Closed
                        }
                    } else {
                        msg.readerIndex(readerIndex)
                    }
                } else {
                    msg.readerIndex(readerIndex)
                }
            }
        } else if (this.status == Status.WaitServerInfo) {
            if (msg.readableBytes() > 4) {
                val readerIndex = msg.readerIndex()

                val version = SocksVersion.valueOf(msg.readByte())
                val command = Socks5CommandType.valueOf(msg.readByte())
                msg.skipBytes(1); // RSV
                val addressType = Socks5AddressType.valueOf(msg.readByte())
                val host = if (addressType == Socks5AddressType.IPv4) {
                    if (msg.readableBytes() >= 6) {
                        NetUtil.intToIpAddress(msg.readInt())
                    } else {
                        msg.readerIndex(readerIndex)
                        return
                    }
                } else if (addressType == Socks5AddressType.DOMAIN) {
                    val length = msg.readByte().toInt() and 0xFF
                    if (msg.readableBytes() >= length + 2) {
                        val addr = msg.toString(msg.readerIndex(), length, CharsetUtil.US_ASCII);
                        msg.skipBytes(length)
                        addr
                    } else {
                        msg.readerIndex(readerIndex)
                        return
                    }
                } else if (addressType == Socks5AddressType.IPv6) {
                    if (msg.readableBytes() >= 18) {
                        if (msg.hasArray()) {
                            val readerIdx = msg.readerIndex();
                            msg.readerIndex(readerIdx + 16)
                            NetUtil.bytesToIpAddress(msg.array(), msg.arrayOffset() + readerIdx, 16)
                        } else {
                            val tmp = ByteArray(16)
                            msg.readBytes(tmp)
                            NetUtil.bytesToIpAddress(tmp);
                        }
                    } else {
                        msg.readerIndex(readerIndex)
                        return
                    }
                } else {
                    msg.readerIndex(msg.writerIndex())
                    ctx.close()
                    this.status = Status.Closed
                    return
                }
                val port = msg.readUnsignedShort()
                if (version == SocksVersion.SOCKS5 && command == Socks5CommandType.CONNECT) {
                    ctx.fireChannelRead(ServerConnectionRequest(host, port))
                    this.status = Status.WaitConnection
                } else {
                    msg.readerIndex(msg.writerIndex())
                    ctx.close()
                    this.status = Status.Closed
                }
            }
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is ServerConnectionEstablishedEvent && this.status == Status.WaitConnection) {
            ctx.writeAndFlush(
                ctx.alloc().buffer().writeBytes(
                    byteArrayOf(
                        SocksVersion.SOCKS5.byteValue(),
                        Socks5CommandStatus.SUCCESS.byteValue(),
                        0x0,
                        Socks5AddressType.IPv4.byteValue(),
                        0x0, 0x0, 0x0, 0x0,
                        0x0, 0x0,
                    )
                )
            )
            ctx.pipeline().remove(this)
        }
        if (evt is ServerConnectionNegotiationFailedEvent && this.status == Status.WaitConnection) {
            ctx.writeAndFlush(
                ctx.alloc().buffer().writeBytes(
                    byteArrayOf(
                        SocksVersion.SOCKS5.byteValue(),
                        Socks5CommandStatus.FAILURE.byteValue(),
                        0x0,
                        Socks5AddressType.IPv4.byteValue(),
                        0x0, 0x0, 0x0, 0x0,
                        0x0, 0x0,
                    )
                )
            )
            ctx.pipeline().remove(this)
        }
        super.userEventTriggered(ctx, evt)
    }
}