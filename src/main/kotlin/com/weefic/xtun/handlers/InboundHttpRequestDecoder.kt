package com.weefic.xtun.handlers

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.http.HttpConstants
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpRequest
import io.netty.util.CharsetUtil

class InboundHttpRequestDecoder : InboundHttpObjectDecoder<HttpRequest>() {
    private val SLASH = '/'
    private val QUESTION_MARK = '?'
    private val SLASH_AND_SPACE_SHORT = SLASH.code shl 8 or HttpConstants.SP.toInt()
    private val SPACE_SLASH_AND_SPACE_MEDIUM = HttpConstants.SP.toInt() shl 16 or SLASH_AND_SPACE_SHORT

    override fun acceptInboundMessage(msg: Any): Boolean {
        return msg is HttpRequest || msg is HttpContent
    }

    @Throws(Exception::class)
    override fun encodeInitialLine(buf: ByteBuf, request: HttpRequest) {
        ByteBufUtil.copy(request.method().asciiName(), buf)
        val uri = request.uri()
        if (uri.isEmpty()) {
            // Add " / " as absolute path if uri is not present.
            // See https://tools.ietf.org/html/rfc2616#section-5.1.2
            ByteBufUtil.writeMediumBE(buf, SPACE_SLASH_AND_SPACE_MEDIUM)
        } else {
            var uriCharSequence: CharSequence? = uri
            var needSlash = false
            var start = uri.indexOf("://")
            if (start != -1 && uri[0] != SLASH) {
                start += 3
                // Correctly handle query params.
                // See https://github.com/netty/netty/issues/2732
                val index = uri.indexOf(QUESTION_MARK, start)
                if (index == -1) {
                    if (uri.lastIndexOf(SLASH) < start) {
                        needSlash = true
                    }
                } else {
                    if (uri.lastIndexOf(SLASH, index) < start) {
                        uriCharSequence = StringBuilder(uri).insert(index, SLASH)
                    }
                }
            }
            buf.writeByte(HttpConstants.SP.toInt()).writeCharSequence(uriCharSequence, CharsetUtil.UTF_8)
            if (needSlash) {
                // write "/ " after uri
                ByteBufUtil.writeShortBE(buf, SLASH_AND_SPACE_SHORT)
            } else {
                buf.writeByte(HttpConstants.SP.toInt())
            }
        }
        buf.writeCharSequence(request.protocolVersion().text(), CharsetUtil.US_ASCII)
        ByteBufUtil.writeShortBE(buf, InboundHttpObjectDecoder.CRLF_SHORT)
    }
}