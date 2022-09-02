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

class ClientConnectionHttpProxyInboundHandler(connectionId: Long, val userCredentials: List<UserCredential>?) : ChannelInboundHandlerAdapter() {
    companion object {
        const val HTTP_DECODER_NAME = "HTTP_DECODER"
        const val HTTP_ENCODER_NAME = "HTTP_ENCODER"
        private val LOG = LoggerFactory.getLogger("Client-Connection-HTTPProxy")
    }

    private val LOG_PREFIX = Tunnel.MARKERS.getDetachedMarker("-$connectionId")


    private sealed class TransferMode {
        object Undetermined : TransferMode()
        object Terminated : TransferMode()
        object HttpMessaging : TransferMode()
        data class ConnectNegotiating(val address: InetSocketAddress, val user: String?) : TransferMode()
        object ConnectStreaming : TransferMode()
    }

    private var transferMode: TransferMode = TransferMode.Undetermined
    private var serverConnectedVarConnect = false


    private fun accept(authorization: String?): Pair<String?, Boolean> {
        val credentials = this.userCredentials
        if (credentials == null) {
            return null to true
        } else {
            if (authorization != null) {
                val authBase64 = authorization.substringAfter("Basic ")
                val authBytes = try {
                    Base64.getDecoder().decode(authBase64)
                } catch (e: Exception) {
                    null
                }
                if (authBytes != null) {
                    for (credential in credentials) {
                        val checkBytes = "${credential.user}:${credential.password}".encodeToByteArray()
                        if (authBytes.contentEquals(checkBytes)) {
                            return credential.user to true
                        }
                    }
                }
            }
            return null to false
        }
    }

    override fun channelRead(ctx: ChannelHandlerContext, obj: Any) {
        when (val transferMode = this.transferMode) {
            is TransferMode.Undetermined -> {
                if (obj is HttpRequest) {
                    val headers = obj.headers()
                    // 收到HTTP请求
                    val (user, accepted) = this.accept(headers.get(HttpHeaderNames.PROXY_AUTHORIZATION))
                    if (accepted) {
                        headers.set(HttpHeaderNames.CONNECTION, "Close")
                        headers.remove(HttpHeaderNames.PROXY_CONNECTION)
                        headers.remove(HttpHeaderNames.PROXY_AUTHORIZATION)
                        val (host, port) = this.identifyHostAndPort(obj)
                        val address = InetSocketAddress.createUnresolved(host, port)
                        LOG.info(LOG_PREFIX, "Request accepted with method '{}', destination server is {}:{} ", obj.method(), host, port)
                        if (obj.method() == HttpMethod.CONNECT) {
                            // 使用CONNECT模式
                            LOG.info(LOG_PREFIX, "Connection is negotiating.")
                            this.transferMode = TransferMode.ConnectNegotiating(address, user)
                            ReferenceCountUtil.release(obj)
                        } else {
                            // 使用非CONNECT模式
                            LOG.info(LOG_PREFIX, "Ready for connect destination server using HTTP-Message mode")
                            this.transferMode = TransferMode.HttpMessaging
                            ctx.fireChannelRead(ServerConnectionRequest(address, user))
                            ctx.fireChannelRead(obj)
                        }
                    } else {
                        this.transferMode = TransferMode.Terminated
                        LOG.info("Sending HTTP/1.1 407 Proxy Authentication Required")
                        ctx.writeAndFlush(ctx.alloc().buffer().writeBytes("HTTP/1.1 407 Proxy Authentication Required\r\n\r\n".toByteArray())).addListener(ChannelFutureListener.CLOSE)
                    }
                } else {
                    LOG.warn(LOG_PREFIX, "Transfer mode is undetermined but we got message type : {}. The connection will be disconnected.", obj.javaClass)
                    this.transferMode = TransferMode.Terminated
                    ReferenceCountUtil.release(obj)
                    ctx.close()
                }
            }
            is TransferMode.ConnectNegotiating -> {
                when (obj) {
                    is LastHttpContent -> {
                        LOG.info(LOG_PREFIX, "Negotiation finished. Ready for connect destination server.")
                        this.transferMode = TransferMode.ConnectStreaming
                        ctx.fireChannelRead(ServerConnectionRequest(transferMode.address, transferMode.user))
                        ctx.pipeline().remove(HTTP_DECODER_NAME)
                        ReferenceCountUtil.release(obj)
                    }
                    is HttpContent -> {
                        // Chunk...Ignore
                        LOG.info(LOG_PREFIX, "Negotiating.")
                        ReferenceCountUtil.release(obj)
                    }
                    else -> {
                        LOG.warn(LOG_PREFIX, "Negotiation on progress but we got message type : {}. The connection will be disconnected.", obj.javaClass)
                        this.transferMode = TransferMode.Terminated
                        ReferenceCountUtil.release(obj)
                        ctx.close()
                    }
                }
            }
            is TransferMode.ConnectStreaming -> {
                if (obj is ByteBuf) {
                    LOG.debug(LOG_PREFIX, "Streaming data")
                    ctx.fireChannelRead(obj)
                } else {
                    LOG.warn(LOG_PREFIX, "Unknown data to stream : {}", obj.javaClass)
                    this.transferMode = TransferMode.Terminated
                    ReferenceCountUtil.release(obj)
                    ctx.close()
                }
            }
            is TransferMode.HttpMessaging -> {
                when (obj) {
                    is LastHttpContent -> {
                        this.transferMode = TransferMode.Terminated
                        LOG.debug(LOG_PREFIX, "Streaming last http message")
                        ctx.fireChannelRead(obj)
                    }
                    is HttpContent -> {
                        LOG.debug(LOG_PREFIX, "Streaming http message")
                        ctx.fireChannelRead(obj)
                    }
                    else -> {
                        LOG.info(LOG_PREFIX, "Unknown message when streaming http message : {}", obj.javaClass)
                        this.transferMode = TransferMode.Terminated
                        ReferenceCountUtil.release(obj)
                        ctx.close()
                    }
                }
            }
            TransferMode.Terminated -> {
                LOG.info(LOG_PREFIX, "Streaming terminated. But got message : {}", obj.javaClass)
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
                    ctx.writeAndFlush(ctx.alloc().buffer().writeBytes("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray()))
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