package com.weefic.xtun.inbound

import com.weefic.xtun.ServerConnectionRequest
import com.weefic.xtun.ServerConnectionResult
import com.weefic.xtun.Tunnel
import com.weefic.xtun.UserCredential
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFuture
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
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

class ClientConnectionSocks5InboundHandler(
    connectionId: Long,
    val userCredentials: List<UserCredential>?
) : ByteToMessageDecoder() {
    companion object {
        private val LOG = LoggerFactory.getLogger("client-connection-socks5")
    }

    private enum class Status {
        Initialize,
        WaitAuthentication,
        WaitServerInfo,
        WaitConnection,
        Closed,
    }

    private val TAG = Tunnel.MARKERS.getDetachedMarker("-$connectionId")
    private var status = Status.Initialize
    private var user: String? = null


    private fun ChannelHandlerContext.writeData(data: ByteArray): ChannelFuture {
        return this.writeAndFlush(this.alloc().buffer().writeBytes(data))
    }

    private fun handleHandshake(ctx: ChannelHandlerContext, version: SocksVersion, methods: ByteArray) {
        if (version == SocksVersion.SOCKS5) {
            val userCredentials = this.userCredentials
            val method = if (userCredentials != null && userCredentials.isNotEmpty()) {
                if (methods.contains(Socks5AuthMethod.PASSWORD.byteValue())) {
                    Socks5AuthMethod.PASSWORD
                } else {
                    val allowAny = userCredentials.any { it.user == "*" && it.password == "*" }
                    if (allowAny && methods.contains(Socks5AuthMethod.NO_AUTH.byteValue())) {
                        Socks5AuthMethod.NO_AUTH
                    } else {
                        null
                    }
                }
            } else {
                if (methods.contains(Socks5AuthMethod.PASSWORD.byteValue())) {
                    Socks5AuthMethod.PASSWORD
                } else if (methods.contains(Socks5AuthMethod.NO_AUTH.byteValue())) {
                    Socks5AuthMethod.NO_AUTH
                } else {
                    null
                }
            }
            if (method != null) {
                LOG.debug("Socks5 handshake accepted.")
                ctx.writeData(byteArrayOf(SocksVersion.SOCKS5.byteValue(), method.byteValue()))
                    .addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
                if (method == Socks5AuthMethod.NO_AUTH) {
                    this.status = Status.WaitServerInfo
                } else {
                    this.status = Status.WaitAuthentication
                }
            } else {
                ctx.writeData(byteArrayOf(SocksVersion.SOCKS5.byteValue(), Socks5AuthMethod.UNACCEPTED.byteValue()))
                    .addListener(ChannelFutureListener.CLOSE)
                this.status = Status.Closed
            }
        } else {
            this.status = Status.Closed
            ctx.close()
        }
    }

    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        if (this.status == Status.Initialize) {
            if (msg.readableBytes() > 2) {
                val readerIndex = msg.readerIndex()
                val version = SocksVersion.valueOf(msg.readByte())
                val countOfMethods = msg.readByte().toInt() and 0xFF
                if (msg.readableBytes() >= countOfMethods) {
                    val methods = ByteArray(countOfMethods)
                    msg.readBytes(methods)
                    this.handleHandshake(ctx, version, methods)
                } else {
                    msg.readerIndex(readerIndex)
                }
            }
        } else if (this.status == Status.WaitAuthentication) {
            if (msg.readableBytes() > 2) {
                val readerIndex = msg.readerIndex()

                val version = msg.readByte()
                val uLength = msg.readByte().toInt() and 0xFF
                if (msg.readableBytes() > uLength) {
                    val user = ByteArray(uLength)
                    msg.readBytes(user)
                    val pLength = msg.readByte().toInt() and 0xFF
                    if (msg.readableBytes() >= pLength) {
                        val password = ByteArray(pLength)
                        msg.readBytes(password)
                        val credential = UserCredential(user.decodeToString(), password.decodeToString())
                        val accepted = this.accept(credential)
                        if (accepted) {
                            ctx.writeAndFlush(
                                ctx.alloc().buffer().writeBytes(
                                    byteArrayOf(
                                        0x01,
                                        Socks5CommandStatus.SUCCESS.byteValue()
                                    )
                                )
                            )
                            this.status = Status.WaitServerInfo
                            this.user = credential.user
                        } else {
                            msg.readerIndex(msg.writerIndex())
                            ctx.writeAndFlush(
                                ctx.alloc().buffer().writeBytes(
                                    byteArrayOf(
                                        0x01,
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
                    ctx.fireChannelRead(
                        ServerConnectionRequest(
                            InetSocketAddress.createUnresolved(host, port),
                            this.user
                        )
                    )
                    this.status = Status.WaitConnection
                } else {
                    msg.readerIndex(msg.writerIndex())
                    ctx.close()
                    this.status = Status.Closed
                }
            }
        }
    }

    private fun accept(userCredential: UserCredential): Boolean {
        val credentials = this.userCredentials
        if (credentials == null) {
            return true
        } else {
            for (credential in credentials) {
                if (credential.user == "*" || credential.user == userCredential.user) {
                    if (credential.password == "*" || credential.password == userCredential.password) {
                        return true
                    }
                }
            }
            return false
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is ServerConnectionResult && this.status == Status.WaitConnection) {
            if (evt == ServerConnectionResult.Success) {
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
            } else {
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
        }
        super.userEventTriggered(ctx, evt)
    }
}