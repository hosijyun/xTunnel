package com.weefic.xtun.inbound

import com.weefic.xtun.vmess.*
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder

class VMessDataDecodeHandler(
    val account: VMessAccount,
    val requestHeader: VMessHeader,
    val sizeDecoder: ChunkSizeDecoder,
    val decoder: VMessDataDecoder,
    val padding: PaddingLengthGenerator?,
) : ByteToMessageDecoder() {
    private val sizeOffset: Int
    private var contentSize = 0
    private var paddingSize = 0

    init {
        if (sizeDecoder is ChunkSizeDecoderWithOffset) {
            this.sizeOffset = sizeDecoder.hasConstantOffset().toInt() and 0xFFFF
        } else {
            this.sizeOffset = 0
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)

    }

    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        if (this.contentSize == 0) {
            val requiredSize = this.sizeDecoder.sizeBytes()
            if (msg.readableBytes() >= requiredSize) {
                val sizeData = ByteArray(requiredSize)
                msg.readBytes(sizeData)
                this.paddingSize = (this.padding?.nextPaddingLen() ?: 0).toInt() and 0xFFFF
                this.contentSize = (this.sizeDecoder.decode(sizeData).toInt() and 0xFFFF) + this.sizeOffset
            }
        } else if (msg.readableBytes() >= this.contentSize) {
            val content = ByteArray(this.contentSize)
            msg.readBytes(content)
            val result = this.decoder.process(content.copyOfRange(0, this.contentSize - this.paddingSize))
            out.add(Unpooled.wrappedBuffer(result))
        }
    }
}