package com.weefic.xtun.inbound

import com.weefic.xtun.ServerConnectionRequest
import com.weefic.xtun.ServerConnectionResult
import com.weefic.xtun.Tunnel
import com.weefic.xtun.UserCredential
import com.weefic.xtun.utils.HttpProxyUtils
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.*
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.*

class ClientConnectionHttpProxyInboundHandler(connectionId: Long, val userCredentials: List<UserCredential>?) :
    ChannelInboundHandlerAdapter() {
    companion object {
        const val HTTP_DECODER_NAME = "HTTP_DECODER"
        const val HTTP_ENCODER_NAME = "HTTP_ENCODER"
        private val LOG = LoggerFactory.getLogger("Client-Connection-HTTPProxy")
    }

    private val LOG_TAG = Tunnel.MARKERS.getDetachedMarker("-$connectionId")


    private sealed class TransferMode {
        object Initialize : TransferMode()
        object Terminated : TransferMode()
        object HttpMessaging : TransferMode()
        data class ConnectNegotiating(val address: InetSocketAddress, val user: String?) : TransferMode()
        object ConnectStreaming : TransferMode()
    }

    private var transferMode: TransferMode = TransferMode.Initialize
    private var serverConnectedVarConnect = false


    private fun accept(userCredential: UserCredential?): Boolean {
        val credentials = this.userCredentials
        if (credentials.isNullOrEmpty()) {
            return true
        } else if (userCredential != null) {
            for (credential in credentials) {
                if (credential.user == "*" || credential.user == userCredential.user) {
                    if (credential.password == "*" || credential.password == userCredential.password) {
                        return true
                    }
                }
            }
            return false
        } else {
            return credentials.any { it.user == "*" && it.password == "*" }
        }
    }

    private fun parseAuthorizationCredential(authorization: String?): UserCredential? {
        if (authorization != null) {
            val authBase64 = authorization.substringAfter("Basic ")
            val authBytes = try {
                Base64.getDecoder().decode(authBase64)
            } catch (e: Exception) {
                null
            }
            if (authBytes != null) {
                val authToken = authBytes.decodeToString()
                val splitIndex = authToken.indexOf(':')
                if (splitIndex >= 0) {
                    val providedUser = authToken.substring(0, splitIndex)
                    val providedPassword = authToken.substring(splitIndex + 1)
                    return UserCredential(providedUser, providedPassword)
                }
            }
        }
        return null
    }

    override fun channelRead(ctx: ChannelHandlerContext, obj: Any) {
        when (val transferMode = this.transferMode) {
            is TransferMode.Initialize -> {
                if (obj is HttpRequest) {
                    val headers = obj.headers()
                    // 收到HTTP请求
                    val authorization = headers.get(HttpHeaderNames.PROXY_AUTHORIZATION)
                    val userCredential = this.parseAuthorizationCredential(authorization)
                    val accepted = this.accept(userCredential)
                    if (accepted) {
                        headers.set(HttpHeaderNames.CONNECTION, "Close")
                        headers.remove(HttpHeaderNames.PROXY_CONNECTION)
                        headers.remove(HttpHeaderNames.PROXY_AUTHORIZATION)
                        val (host, port) = this.identifyHostAndPort(obj)
                        val address = InetSocketAddress.createUnresolved(host, port)
                        LOG.info(LOG_TAG, "Request accepted with method '{}', destination server is {}:{} ", obj.method(), host, port)
                        if (obj.method() == HttpMethod.CONNECT) {
                            // 使用CONNECT模式
                            LOG.info(LOG_TAG, "Connection is negotiating.")
                            this.transferMode = TransferMode.ConnectNegotiating(address, userCredential?.user)
                            ReferenceCountUtil.release(obj)
                        } else {
                            // 使用非CONNECT模式
                            LOG.info(LOG_TAG, "Ready for connect destination server using HTTP-Message mode")
                            this.transferMode = TransferMode.HttpMessaging
                            ctx.fireChannelRead(ServerConnectionRequest(address, userCredential?.user))
                            ctx.fireChannelRead(obj)
                        }
                    } else {
                        this.transferMode = TransferMode.Terminated
                        LOG.info("Sending HTTP/1.1 407 Proxy Authentication Required")
                        val content = "<html><body>Authenticator required</body></html>"
                        val message =
                            "HTTP/1.1 407 Proxy Authentication Required\r\n" +
                                    "Content-Length: ${content.encodeToByteArray().size}\r\n" +
                                    "Content-Type: text/html; charset=utf-8\r\n" +
                                    "Proxy-Authenticate: Basic realm=\"Authentication required\"\r\n" +
                                    "\r\n" +
                                    content

                        ctx.writeAndFlush(ctx.alloc().buffer().writeBytes(message.toByteArray())).addListener(ChannelFutureListener.CLOSE)
                    }
                } else {
                    LOG.warn(LOG_TAG, "Transfer mode is `initialize` but we got message type : {}. The connection will be disconnected.", obj.javaClass)
                    this.transferMode = TransferMode.Terminated
                    ReferenceCountUtil.release(obj)
                    ctx.close()
                }
            }

            is TransferMode.ConnectNegotiating -> {
                when (obj) {
                    is LastHttpContent -> {
                        LOG.info(LOG_TAG, "Negotiation finished. Ready for connect destination server.")
                        this.transferMode = TransferMode.ConnectStreaming
                        ctx.fireChannelRead(ServerConnectionRequest(transferMode.address, transferMode.user))
                        ctx.pipeline().remove(HTTP_DECODER_NAME)
                        ReferenceCountUtil.release(obj)
                    }

                    is HttpContent -> {
                        // Chunk...Ignore
                        LOG.info(LOG_TAG, "Negotiating.")
                        ReferenceCountUtil.release(obj)
                    }

                    else -> {
                        LOG.warn(
                            LOG_TAG,
                            "Negotiation on progress but we got message type : {}. The connection will be disconnected.",
                            obj.javaClass
                        )
                        this.transferMode = TransferMode.Terminated
                        ReferenceCountUtil.release(obj)
                        ctx.close()
                    }
                }
            }

            is TransferMode.ConnectStreaming -> {
                if (obj is ByteBuf) {
                    LOG.debug(LOG_TAG, "Streaming data")
                    ctx.fireChannelRead(obj)
                } else {
                    LOG.warn(LOG_TAG, "Unknown data to stream : {}", obj.javaClass)
                    this.transferMode = TransferMode.Terminated
                    ReferenceCountUtil.release(obj)
                    ctx.close()
                }
            }

            is TransferMode.HttpMessaging -> {
                when (obj) {
                    is LastHttpContent -> {
                        this.transferMode = TransferMode.Terminated
                        LOG.debug(LOG_TAG, "Streaming last http message")
                        ctx.fireChannelRead(obj)
                    }

                    is HttpContent -> {
                        LOG.debug(LOG_TAG, "Streaming http message")
                        ctx.fireChannelRead(obj)
                    }

                    else -> {
                        LOG.info(LOG_TAG, "Unknown message when streaming http message : {}", obj.javaClass)
                        this.transferMode = TransferMode.Terminated
                        ReferenceCountUtil.release(obj)
                        ctx.close()
                    }
                }
            }

            TransferMode.Terminated -> {
                LOG.info(LOG_TAG, "Streaming terminated. But got message : {}", obj.javaClass)
                ReferenceCountUtil.release(obj)
                ctx.close()
            }
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is ServerConnectionResult && this.transferMode == TransferMode.ConnectStreaming) {
            if (evt == ServerConnectionResult.Success) {
                if (!this.serverConnectedVarConnect) {
                    this.serverConnectedVarConnect = true
                    LOG.info("Sending HTTP/1.1 200 Connection established")
                    ctx.writeAndFlush(
                        ctx.alloc().buffer().writeBytes("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
                    )
                } else {
                    LOG.warn("Duplicate ServerConnectionEstablishedEvent")
                }
            } else {
                if (!this.serverConnectedVarConnect) {
                    this.serverConnectedVarConnect = true
                    LOG.info("Sending HTTP/1.1 502 Bad Gateway")
                    ctx.writeAndFlush(ctx.alloc().buffer().writeBytes("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray()))
                } else {
                    LOG.warn("Duplicate ServerConnectionNegotiationFailedEvent")
                }
            }
        }
        super.userEventTriggered(ctx, evt)
    }

    private fun identifyHostAndPort(request: HttpRequest): Pair<String, Int> {
        val hostAndPort = HttpProxyUtils.identifyHostAndPort(request)
        val colonIndex = hostAndPort.indexOf(':')
        return if (colonIndex == -1) {
            hostAndPort to 80
        } else {
            val intPort = hostAndPort.substring(colonIndex + 1).toIntOrNull()
            if (intPort != null) {
                hostAndPort.substring(0, colonIndex) to intPort
            } else {
                hostAndPort to 80
            }
        }
    }
}