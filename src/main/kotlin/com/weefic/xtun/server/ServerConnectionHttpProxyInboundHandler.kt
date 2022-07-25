package com.weefic.xtun.server

import com.weefic.xtun.ServerConnectionEstablishedEvent
import com.weefic.xtun.ServerConnectionNegotiationFailedEvent
import com.weefic.xtun.Tunnel
import com.weefic.xtun.UserCredential
import com.weefic.xtun.utils.getText
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.*
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory
import java.util.*

class ServerConnectionHttpProxyInboundHandler(
    val connectionId: Long,
    val host: String, val port: Int,
    val userCredential: UserCredential? = null,
) : ChannelInboundHandlerAdapter() {
    companion object {
        private val LOG = LoggerFactory.getLogger("Server-Connection-HTTPProxy")
        const val HTTP_DECODER_NAME = "HTTP_DECODER"
    }

    private var LOG_PREFIX = Tunnel.MARKERS.getDetachedMarker("-${this.connectionId}")
    private var connectionEstablished = false
    private var connectionReceiveResponseCode = -1

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        LOG.info(LOG_PREFIX, "Server connection negotiating")
        val request = DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "$host:$port")
        request.headers().set(HttpHeaderNames.HOST, this.host)
        this.userCredential?.let {
            val credential = Base64.getEncoder().encodeToString("${it.user}:${it.password}".encodeToByteArray())
            request.headers().set(HttpHeaderNames.PROXY_AUTHORIZATION, "Basic $credential")
        }
        ctx.pipeline().write(request).addListener {
            if (!it.isSuccess) {
                LOG.warn(LOG_PREFIX, "Server connection negotiate failed", it.cause())
                ctx.close()
            }
        }
        ctx.pipeline().write(LastHttpContent.EMPTY_LAST_CONTENT).addListener {
            if (!it.isSuccess) {
                LOG.warn(LOG_PREFIX, "Server connection negotiate failed", it.cause())
                ctx.close()
            }
        }
        ctx.flush()
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (this.connectionEstablished) {
            super.channelRead(ctx, msg)
        } else {
            if (this.connectionReceiveResponseCode == -1) {
                if (msg is HttpResponse) {
                    val code = msg.status().code()
                    if (code == 200) {
                        LOG.info(LOG_PREFIX, "Server negotiation receive success response.")
                    } else {
                        LOG.info(LOG_PREFIX, "Server negotiating failed. Error code : {}", code)
                    }
                    this.connectionReceiveResponseCode = code
                } else {
                    LOG.warn(LOG_PREFIX, "Server negotiating. But we got message {}", msg.javaClass)
                    ctx.close()
                }
            } else {
                if (msg is LastHttpContent) {
                    if (this.connectionReceiveResponseCode == 200) {
                        this.connectionEstablished = true
                        ctx.pipeline().remove(HTTP_DECODER_NAME)
                        ctx.fireChannelRead(ServerConnectionEstablishedEvent)
                        LOG.info(LOG_PREFIX, "Server negotiating finished. Ready for streaming.")
                    } else {
                        ctx.pipeline().remove(HTTP_DECODER_NAME)
                        ctx.fireChannelRead(ServerConnectionNegotiationFailedEvent)
                        ctx.close()
                    }
                } else if (msg is HttpContent) {
                    // Bypass
                } else {
                    if (msg is ByteBuf) {
                        LOG.warn(LOG_PREFIX, "Server negotiating. But we got message {}", msg.getText())
                    } else {
                        LOG.warn(LOG_PREFIX, "Server negotiating. But we got message {}", msg.javaClass)
                    }
                    ctx.close()
                }
            }
            ReferenceCountUtil.release(msg)
            ctx.read()
        }
    }
}