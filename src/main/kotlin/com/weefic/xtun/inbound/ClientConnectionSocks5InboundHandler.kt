package com.weefic.xtun.inbound

import com.weefic.xtun.ServerConnectionRequest
import com.weefic.xtun.ServerConnectionResult
import com.weefic.xtun.Tunnel
import com.weefic.xtun.UserCredential
import com.weefic.xtun.utils.Socks5Coder
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.ReplayingDecoder
import io.netty.handler.codec.socksx.SocksVersion
import io.netty.handler.codec.socksx.v5.*
import org.slf4j.LoggerFactory

class ClientConnectionSocks5InboundHandler(
    connectionId: Long,
    val userCredentials: List<UserCredential>?
) : ReplayingDecoder<ClientConnectionSocks5InboundHandler.Status>(Status.Initialize) {
    companion object {
        private val LOG = LoggerFactory.getLogger("client-connection-socks5")
    }

    enum class Status {
        Initialize,
        PasswordAuthentication,
        Command,
        Connecting,
        Closed,
    }

    private val TAG = Tunnel.MARKERS.getDetachedMarker("-$connectionId")
    private var user: String? = null

    private fun ChannelHandlerContext.writeData(data: ByteArray): ChannelFuture {
        return this.writeAndFlush(this.alloc().buffer().writeBytes(data))
    }

    private fun chooseAuthMethod(ctx: ChannelHandlerContext, initialRequest: Socks5InitialRequest): Socks5AuthMethod? {
        val userCredentials = this.userCredentials
        val methods = initialRequest.authMethods()

        return if (userCredentials != null && userCredentials.isNotEmpty()) {
            if (methods.contains(Socks5AuthMethod.PASSWORD)) {
                Socks5AuthMethod.PASSWORD
            } else {
                val allowAny = userCredentials.any { it.user == "*" && it.password == "*" }
                if (allowAny && methods.contains(Socks5AuthMethod.NO_AUTH)) {
                    Socks5AuthMethod.NO_AUTH
                } else {
                    null
                }
            }
        } else {
            if (methods.contains(Socks5AuthMethod.NO_AUTH)) {
                Socks5AuthMethod.NO_AUTH
            } else if (methods.contains(Socks5AuthMethod.PASSWORD)) {
                Socks5AuthMethod.PASSWORD
            } else {
                null
            }
        }
    }


    private fun write(ctx: ChannelHandlerContext, response: Socks5InitialResponse): ChannelFuture {
        return ctx.writeData(byteArrayOf(response.version().byteValue(), response.authMethod().byteValue()))
    }

    private fun write(ctx: ChannelHandlerContext, response: Socks5PasswordAuthResponse): ChannelFuture {
        return ctx.writeData(byteArrayOf(1.toByte(), response.status().byteValue()))
    }

    private fun write(ctx: ChannelHandlerContext, response: Socks5CommandResponse): ChannelFuture {
        val out = ctx.alloc().buffer()
        try {
            out.writeByte(response.version().byteValue().toInt())
            out.writeByte(response.status().byteValue().toInt())
            out.writeByte(0x00)
            val bndAddrType: Socks5AddressType = response.bndAddrType()
            out.writeByte(bndAddrType.byteValue().toInt())
            Socks5AddressEncoder.DEFAULT.encodeAddress(bndAddrType, response.bndAddr(), out)
            ByteBufUtil.writeShortBE(out, response.bndPort())

            return ctx.writeAndFlush(out.retain())
        } finally {
            out.release()
        }
    }

    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        when (this.state()) {
            Status.Initialize -> {
                val version = SocksVersion.valueOf(msg.readByte())
                check(version == SocksVersion.SOCKS5) { throw DecoderException("unsupported version: $version (expected: ${SocksVersion.SOCKS5.byteValue()})") }

                val countOfMethods = msg.readByte().toInt() and 0xFF
                val methodsData = ByteArray(countOfMethods)
                msg.readBytes(methodsData)
                val methods = methodsData.map { Socks5AuthMethod(it.toInt() and 0xFF) }
                val initialRequest = DefaultSocks5InitialRequest(*methods.toTypedArray())
                val authMethod = this.chooseAuthMethod(ctx, initialRequest)
                if (authMethod == null) {
                    this.write(ctx, DefaultSocks5InitialResponse(Socks5AuthMethod.UNACCEPTED)).addListener(ChannelFutureListener.CLOSE)
                    this.checkpoint(Status.Closed)
                } else {
                    this.write(ctx, DefaultSocks5InitialResponse(authMethod)).addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
                    if (authMethod == Socks5AuthMethod.PASSWORD) {
                        checkpoint(Status.PasswordAuthentication)
                    } else {
                        checkpoint(Status.Command)
                    }
                }
            }

            Status.PasswordAuthentication -> {
                val version = msg.readByte()
                check(version == 1.toByte()) { "Version 1 expected." }
                val usernameLength = msg.readByte().toInt() and 0xFF
                val username = ByteArray(usernameLength)
                msg.readBytes(username)

                val passwordLength = msg.readByte().toInt() and 0xFF
                val password = ByteArray(passwordLength)
                msg.readBytes(password)
                val credential = UserCredential(username.decodeToString(), password.decodeToString())

                val accepted = this.accept(credential)
                if (accepted) {
                    this.write(ctx, DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS)).addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
                    this.checkpoint(Status.Command)
                    this.user = credential.user
                } else {
                    this.write(ctx, DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE)).addListener(ChannelFutureListener.CLOSE)
                    this.checkpoint(Status.Closed)
                }
            }

            Status.Command -> {
                val version = SocksVersion.valueOf(msg.readByte())
                check(version == SocksVersion.SOCKS5) { throw DecoderException("unsupported version: $version (expected: ${SocksVersion.SOCKS5.byteValue()})") }
                val type = Socks5CommandType.valueOf(msg.readByte())
                msg.skipBytes(1); // RSV
                val address = Socks5Coder.decodeAddress(msg)
                if (type == Socks5CommandType.CONNECT) {
                    out.add(ServerConnectionRequest(address, this.user))
                    this.checkpoint(Status.Connecting)
                } else {
                    this.write(
                        ctx, DefaultSocks5CommandResponse(
                            Socks5CommandStatus.FAILURE,
                            Socks5AddressType.IPv4
                        )
                    ).addListener(ChannelFutureListener.CLOSE)

                    this.checkpoint(Status.Closed)
                }
            }

            Status.Connecting -> {
                out.add(msg.readRetainedSlice(super.actualReadableBytes()))
            }

            Status.Closed -> {
                msg.skipBytes(super.actualReadableBytes())
                ctx.close()
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
        if (evt is ServerConnectionResult && this.state() == Status.Connecting) {
            if (evt == ServerConnectionResult.Success) {
                this.write(
                    ctx, DefaultSocks5CommandResponse(
                        Socks5CommandStatus.SUCCESS,
                        Socks5AddressType.IPv4
                    )
                )
                ctx.pipeline().remove(this)
            } else {
                this.write(
                    ctx, DefaultSocks5CommandResponse(
                        Socks5CommandStatus.FAILURE,
                        Socks5AddressType.IPv4
                    )
                ).addListener(ChannelFutureListener.CLOSE)
                checkpoint(Status.Closed)
                ctx.pipeline().remove(this)
            }
        }
        super.userEventTriggered(ctx, evt)
    }
}