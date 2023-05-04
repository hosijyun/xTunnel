package com.weefic.xtun.outbound

import com.weefic.xtun.ServerConnectionResult
import com.weefic.xtun.UserCredential
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.socksx.SocksVersion
import io.netty.handler.codec.socksx.v5.*
import org.slf4j.LoggerFactory

class ServerConnectionSocks5InboundHandler(
    val connectionId: Long,
    val host: String, val port: Int,
    val credential: UserCredential?
) : ByteToMessageDecoder() {
    companion object {
        private val LOG = LoggerFactory.getLogger("Server-Connection-Socks5")
    }

    private enum class State {
        Initialize,
        WaitAuthenticationResult,
        WaitServerConnectResult,
        Streaming,
        Inconsistent,
    }

    private var state = State.Initialize
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
        message.writeBytes(
            byteArrayOf(
                SocksVersion.SOCKS5.byteValue(),
                Socks5CommandType.CONNECT.byteValue(),
                0x00,
                Socks5AddressType.DOMAIN.byteValue()
            )
        )
        message.writeByte(hostBytes.size)
        message.writeBytes(hostBytes)
        message.writeByte(this.port.ushr(8))
        message.writeByte(this.port)
        ctx.writeAndFlush(message).addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
        ctx.read()
    }


    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        when (this.state) {
            State.Initialize -> {
                if (msg.readableBytes() >= 2) {
                    val version = SocksVersion.valueOf(msg.readByte())
                    val method = Socks5AuthMethod.valueOf(msg.readByte())
                    this.processInitialResponse(ctx, version, method)
                }
            }

            State.WaitAuthenticationResult -> {
                if (msg.readableBytes() >= 2) {
                    val version = msg.readByte()
                    val result = Socks5PasswordAuthStatus.valueOf(msg.readByte())
                    this.processAuthenticationResponse(ctx, version, result)
                }
            }

            State.WaitServerConnectResult -> {
                if (msg.readableBytes() > 4) {
                    val readerIndex = msg.readerIndex()

                    val version = SocksVersion.valueOf(msg.readByte())
                    val status = Socks5CommandStatus.valueOf(msg.readByte())
                    msg.skipBytes(1); // Reserved
                    when (val addressType: Socks5AddressType = Socks5AddressType.valueOf(msg.readByte())) {
                        Socks5AddressType.IPv4 -> {
                            if (msg.readableBytes() >= 6) {
                                msg.skipBytes(4)
                            } else {
                                msg.readerIndex(readerIndex)
                                return
                            }
                        }

                        Socks5AddressType.DOMAIN -> {
                            val length = msg.readUnsignedByte().toInt()
                            if (msg.readableBytes() >= length + 2) {
                                msg.skipBytes(length)
                            } else {
                                msg.readerIndex(readerIndex)
                                return
                            }
                        }

                        Socks5AddressType.IPv6 -> {
                            if (msg.readableBytes() >= 18) {
                                msg.skipBytes(16)
                            } else {
                                msg.readerIndex(readerIndex)
                                return
                            }
                        }

                        else -> {
                            LOG.warn("Unknown response address type : {}", addressType)
                            this.state = State.Inconsistent
                            msg.readerIndex(msg.writerIndex())
                            ctx.close()
                        }
                    }
                    msg.skipBytes(2)
                    this.processServerConnectResponse(ctx, version, status)
                }
            }

            State.Streaming -> {
                out.add(msg.readRetainedSlice(msg.readableBytes()))
            }

            State.Inconsistent -> {
                val available = msg.readableBytes()
                LOG.info("Inconsistent state. Drop {} bytes data", available)
                msg.skipBytes(available)
            }
        }
    }

    private fun processInitialResponse(ctx: ChannelHandlerContext, version: SocksVersion, method: Socks5AuthMethod) {
        val expectedMethod = if (this.credential == null) Socks5AuthMethod.NO_AUTH else Socks5AuthMethod.PASSWORD
        val expectedNextStatus =
            if (this.credential == null) State.WaitServerConnectResult else State.WaitAuthenticationResult
        if (version == SocksVersion.SOCKS5) {
            if (method == expectedMethod) {
                LOG.info("Process initial response using {} authentication method", expectedMethod)
                this.state = expectedNextStatus
            } else {
                LOG.warn("Server require {} authentication method. We support {} only.", method, expectedMethod)
                this.state = State.Inconsistent
                ctx.close()
            }
        } else {
            LOG.warn("Unknown server socks version : {}", version)
            this.state = State.Inconsistent
            ctx.close()
        }
    }

    private fun processAuthenticationResponse(
        ctx: ChannelHandlerContext,
        version: Byte,
        result: Socks5PasswordAuthStatus
    ) {
        if (version == 1.toByte() && result.isSuccess) {
            LOG.info("Password authentication success.")
            this.state = State.WaitServerConnectResult
        } else {
            LOG.warn("Password authentication failed.")
            this.state = State.Inconsistent
            ctx.close()
        }
    }

    private fun processServerConnectResponse(
        ctx: ChannelHandlerContext,
        version: SocksVersion,
        status: Socks5CommandStatus
    ) {
        if (version == SocksVersion.SOCKS5) {
            if (status == Socks5CommandStatus.SUCCESS) {
                LOG.info("Server connection established.")
                this.state = State.Streaming
                ctx.fireChannelRead(ServerConnectionResult.Success)
                ctx.pipeline().remove(this)
            } else {
                LOG.info("Server connect failed : {}", status)
                this.state = State.Inconsistent
                ctx.fireChannelRead(ServerConnectionResult.DataFlowInvalid)
                ctx.close()
            }
        } else {
            LOG.warn("Unknown server socks version : {}", version)
            this.state = State.Inconsistent
            ctx.close()
        }
    }
}