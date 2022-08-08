package com.weefic.xtun.handlers

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageDecoder
import io.netty.handler.codec.http.*
import io.netty.util.AsciiString
import io.netty.util.CharsetUtil
import io.netty.util.internal.StringUtil

abstract class InboundHttpObjectDecoder<H : HttpMessage> : MessageToMessageDecoder<HttpObject>() {
    companion object {
        const val CRLF_SHORT = HttpConstants.CR.toInt() shl 8 or HttpConstants.LF.toInt()
        private const val COLON_AND_SPACE_SHORT = HttpConstants.COLON.toInt() shl 8 or HttpConstants.SP.toInt()
        private const val ZERO_CRLF_MEDIUM = '0'.code shl 16 or CRLF_SHORT
        private val ZERO_CRLF_CRLF = byteArrayOf('0'.code.toByte(), HttpConstants.CR, HttpConstants.LF, HttpConstants.CR, HttpConstants.LF)
        private val CRLF_BUF = Unpooled.unreleasableBuffer(Unpooled.directBuffer(2).writeByte(HttpConstants.CR.toInt()).writeByte(HttpConstants.LF.toInt())).asReadOnly()
        private val ZERO_CRLF_CRLF_BUF = Unpooled.unreleasableBuffer(Unpooled.directBuffer(ZERO_CRLF_CRLF.size).writeBytes(ZERO_CRLF_CRLF)).asReadOnly()

        private const val HEADERS_WEIGHT_NEW = 1 / 5f
        private const val HEADERS_WEIGHT_HISTORICAL = 1 - HEADERS_WEIGHT_NEW
        private const val TRAILERS_WEIGHT_NEW = HEADERS_WEIGHT_NEW
        private const val TRAILERS_WEIGHT_HISTORICAL = HEADERS_WEIGHT_HISTORICAL
    }

    private enum class State {
        ST_INIT,
        ST_CONTENT_NON_CHUNK,
        ST_CONTENT_CHUNK,
        ST_CONTENT_ALWAYS_EMPTY,
    }

    private var state = State.ST_INIT
    private var headersEncodedSizeAccumulator = 256f
    private var trailersEncodedSizeAccumulator = 256f

    @Throws(java.lang.Exception::class)
    override fun acceptInboundMessage(msg: Any): Boolean {
        return msg is HttpMessage
    }

    override fun decode(ctx: ChannelHandlerContext, msg: HttpObject, out: MutableList<Any>) {
        var buf: ByteBuf? = null
        if (msg is HttpMessage) {
            check(this.state == State.ST_INIT) { "unexpected message type: ${StringUtil.simpleClassName(msg)}, state: ${this.state}" }
            @Suppress("UNCHECKED_CAST")
            val m = msg as H
            buf = ctx.alloc().buffer(this.headersEncodedSizeAccumulator.toInt())
            this.encodeInitialLine(buf, m)
            this.state = if (this.isContentAlwaysEmpty(m))
                State.ST_CONTENT_ALWAYS_EMPTY else
                if (HttpUtil.isTransferEncodingChunked(m))
                    State.ST_CONTENT_CHUNK
                else
                    State.ST_CONTENT_NON_CHUNK
            this.sanitizeHeadersBeforeEncode(m, this.state == State.ST_CONTENT_ALWAYS_EMPTY)
            this.encodeHeaders(m.headers(), buf)
            ByteBufUtil.writeShortBE(buf, CRLF_SHORT)
            this.headersEncodedSizeAccumulator = HEADERS_WEIGHT_NEW * padSizeForAccumulation(buf.readableBytes()) + HEADERS_WEIGHT_HISTORICAL * headersEncodedSizeAccumulator
        }
        if (msg is HttpContent) {
            when (this.state) {
                State.ST_INIT -> throw IllegalStateException("unexpected message type: ${StringUtil.simpleClassName(msg)}, state: ${this.state}")
                State.ST_CONTENT_NON_CHUNK -> {
                    val contentLength = msg.content().readableBytes()
                    if (contentLength > 0) {
                        if (buf != null && buf.writableBytes() >= contentLength) {
                            buf.writeBytes(msg.content())
                            out.add(buf)
                        } else {
                            if (buf != null) {
                                out.add(buf)
                            }
                            out.add(msg.retain())
                        }
                    } else {
                        out.add(buf ?: Unpooled.EMPTY_BUFFER)
                    }
                }
                State.ST_CONTENT_ALWAYS_EMPTY ->
                    out.add(buf ?: Unpooled.EMPTY_BUFFER)
                State.ST_CONTENT_CHUNK -> {
                    if (buf != null) {
                        out.add(buf)
                    }
                    this.encodeChunkedContent(ctx, msg, msg.content().readableBytes(), out)
                }
            }
            if (msg is LastHttpContent) {
                this.state = State.ST_INIT
            }
        } else if (buf != null) {
            out.add(buf)
        }
    }

    protected open fun sanitizeHeadersBeforeEncode(msg: H, isAlwaysEmpty: Boolean) {
        // noop
    }

    protected open fun isContentAlwaysEmpty(msg: H): Boolean {
        return false
    }

    @Throws(Exception::class)
    protected abstract fun encodeInitialLine(buf: ByteBuf, message: H)

    protected open fun encodeHeaders(headers: HttpHeaders, buf: ByteBuf) {
        for ((key, value) in headers.iteratorCharSequence()) {
            this.encoderHeader(key, value, buf)
        }
    }

    protected open fun encoderHeader(name: CharSequence, value: CharSequence, buf: ByteBuf) {
        val nameLen = name.length
        val valueLen = value.length
        val entryLen = nameLen + valueLen + 4
        buf.ensureWritable(entryLen)
        var offset = buf.writerIndex()
        this.writeAscii(buf, offset, name)
        offset += nameLen
        ByteBufUtil.setShortBE(buf, offset, COLON_AND_SPACE_SHORT)
        offset += 2
        this.writeAscii(buf, offset, value)
        offset += valueLen
        ByteBufUtil.setShortBE(buf, offset, CRLF_SHORT)
        offset += 2
        buf.writerIndex(offset)
    }

    protected fun writeAscii(buf: ByteBuf, offset: Int, value: CharSequence) {
        if (value is AsciiString) {
            ByteBufUtil.copy(value, 0, buf, offset, value.length)
        } else {
            buf.setCharSequence(offset, value, CharsetUtil.US_ASCII)
        }
    }

    protected fun padSizeForAccumulation(readableBytes: Int): Int {
        return (readableBytes shl 2) / 3
    }


    protected fun encodeChunkedContent(ctx: ChannelHandlerContext, msg: HttpContent, contentLength: Int, out: MutableList<Any>) {
        if (contentLength > 0) {
            val lengthHex = Integer.toHexString(contentLength)
            val buf = ctx.alloc().buffer(lengthHex.length + 2)
            buf.writeCharSequence(lengthHex, CharsetUtil.US_ASCII)
            ByteBufUtil.writeShortBE(buf, CRLF_SHORT)
            out.add(buf)
            out.add(msg.retain())
            out.add(CRLF_BUF.duplicate())
        }
        if (msg is LastHttpContent) {
            val headers = msg.trailingHeaders()
            if (headers.isEmpty) {
                out.add(ZERO_CRLF_CRLF_BUF.duplicate())
            } else {
                val buf = ctx.alloc().buffer(this.trailersEncodedSizeAccumulator.toInt())
                ByteBufUtil.writeMediumBE(buf, ZERO_CRLF_MEDIUM)
                this.encodeHeaders(headers, buf)
                ByteBufUtil.writeShortBE(buf, CRLF_SHORT)
                this.trailersEncodedSizeAccumulator = TRAILERS_WEIGHT_NEW * padSizeForAccumulation(buf.readableBytes()) + TRAILERS_WEIGHT_HISTORICAL * trailersEncodedSizeAccumulator
                out.add(buf)
            }
        } else if (contentLength == 0) {
            out.add(msg.retain())
        }
    }
}