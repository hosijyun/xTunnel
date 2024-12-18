package com.weefic.xtun.inbound

import com.weefic.xtun.ServerConnectionRequest
import com.weefic.xtun.Tunnel
import com.weefic.xtun.utils.Endian
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlin.random.Random

class ClientConnectionMTProtoInboundHandler(
    connectionId: Long,
    secret: String
) : ChannelDuplexHandler() {
    companion object {
        private val LOG = LoggerFactory.getLogger("client-connection-mtproto")
        private const val FAST_MODE = true
        private val DC_IPV4 = arrayOf(
            "149.154.175.53",
            "149.154.167.51",
            "149.154.175.100",
            "149.154.167.91",
            "91.108.56.130"
        )
    }

    private enum class Status {
        Handshaking,
        Established,
        Inactive,
    }

    private val TAG = Tunnel.MARKERS.getDetachedMarker("-$connectionId")
    private val sharedSecret: ByteArray
    private var status = Status.Handshaking

    private val clientToProxyCoder = Cipher.getInstance("AES/CTR/NoPadding")
    private val proxyToClientCoder = Cipher.getInstance("AES/CTR/NoPadding")
    private var clientBuffer: ByteBuf? = null

    private val proxyToServerCoder = Cipher.getInstance("AES/CTR/NoPadding")
    private val serverToProxyCoder = Cipher.getInstance("AES/CTR/NoPadding")

    init {
        val sharedSecret = ByteArray(secret.length / 2)
        repeat(sharedSecret.size) {
            sharedSecret[it] = Integer.parseInt(secret.substring(it * 2, (it + 1) * 2), 16).toByte()
        }
        this.sharedSecret = sharedSecret
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is ByteBuf) {
            val message = ByteArray(msg.readableBytes())
            msg.getBytes(msg.readerIndex(), message)
            LOG.info("Client data : ${Base64.getEncoder().encodeToString(message)}")

            when (val status = this.status) {
                Status.Inactive -> ctx.close()
                Status.Handshaking -> {
                    var buffer = this.clientBuffer
                    if (buffer == null) {
                        buffer = msg
                    } else {
                        buffer.writeBytes(msg)
                        msg.release()
                    }
                    if (buffer.readableBytes() >= 64) {
                        this.clientBuffer = null
                        this.doHandshake(ctx, buffer)
                    } else {
                        this.clientBuffer = buffer
                    }
                }

                Status.Established -> {
                    this.processClientData(ctx, msg)
                }
            }
        } else {
            super.channelRead(ctx, msg)
        }
    }

    private fun doHandshake(ctx: ChannelHandlerContext, buffer: ByteBuf) {
        val sha256 = MessageDigest.getInstance("SHA256")

        val clientInit = ByteArray(64)
        buffer.readBytes(clientInit)
        val clientInitRev = clientInit.reversedArray()

        val clientDecryptKey = clientInit.sliceArray(8 until 40)
        val clientDecryptIV = clientInit.sliceArray(40 until 56)
        val clientEncryptKey = clientInitRev.sliceArray(8 until 40)
        val clientEncryptIV = clientInitRev.sliceArray(40 until 56)

        sha256.update(clientDecryptKey)
        sha256.update(this.sharedSecret)
        val clientDataDecryptKey = sha256.digest()

        sha256.reset()
        sha256.update(clientEncryptKey)
        sha256.update(this.sharedSecret)
        val clientDataEncryptKey = sha256.digest()

        this.clientToProxyCoder.init(Cipher.DECRYPT_MODE, SecretKeySpec(clientDataDecryptKey, "AES"), IvParameterSpec(clientDecryptIV))
        this.proxyToClientCoder.init(Cipher.ENCRYPT_MODE, SecretKeySpec(clientDataEncryptKey, "AES"), IvParameterSpec(clientEncryptIV))

        val clientInitPlainData = this.clientToProxyCoder.update(clientInit)
        val protocol = Endian.LE.getInt(clientInitPlainData, 56)
        if (protocol != 0xefefefefL.toInt() && protocol != 0xeeeeeeeeL.toInt() && protocol != 0xddddddddL.toInt()) {
            ctx.close()
            buffer.release()
            this.status = Status.Inactive
            return
        }
        val dataCenterValue = Endian.LE.getShort(clientInitPlainData, 60)
        val dataCenterIndex = abs(dataCenterValue.toInt()) - 1
        if (dataCenterIndex < 0 || dataCenterIndex >= DC_IPV4.size) {
            ctx.close()
            buffer.release()
            this.status = Status.Inactive
            return
        }
        val dataCenter = DC_IPV4[dataCenterIndex]
        ctx.fireChannelRead(ServerConnectionRequest(InetSocketAddress.createUnresolved(dataCenter, 443), null))
        this.status = Status.Established

        // Server Handshake
        val serverInit = ByteArray(64)
        Random.nextBytes(serverInit)
        if (FAST_MODE) {
            clientDataDecryptKey.copyInto(serverInit, 8)
            clientDecryptIV.copyInto(serverInit, 40)
        }
        Endian.LE.putInt(serverInit, 56, protocol)
        val serverInitRev = serverInit.reversedArray()

        val serverEncryptKey = serverInit.copyOfRange(8, 40)
        val serverEncryptIV = serverInit.copyOfRange(40, 56)
        val serverDecryptKey = serverInitRev.copyOfRange(8, 40)
        val serverDecryptIV = serverInitRev.copyOfRange(40, 56)
        this.proxyToServerCoder.init(Cipher.ENCRYPT_MODE, SecretKeySpec(serverEncryptKey, "AES"), IvParameterSpec(serverEncryptIV))
        this.serverToProxyCoder.init(Cipher.DECRYPT_MODE, SecretKeySpec(serverDecryptKey, "AES"), IvParameterSpec(serverDecryptIV))
        val encryptedServerInit = this.proxyToServerCoder.update(serverInit)
        ctx.fireChannelRead(
            ctx.alloc().buffer()
                .writeBytes(serverInit, 0, 56)
                .writeBytes(encryptedServerInit, 56, 8)
        )
        // Process remaining data
        this.processClientData(ctx, buffer)
        ctx.fireChannelReadComplete()
    }

    private fun processClientData(ctx: ChannelHandlerContext, clientBuffer: ByteBuf) {
        //if (clientBuffer.readableBytes() > 0) {
        if (FAST_MODE) {
            ctx.fireChannelRead(clientBuffer)
        } else {
            val clientData = ByteArray(clientBuffer.readableBytes())
            clientBuffer.readBytes(clientData)
            val plainData = this.clientToProxyCoder.update(clientData)
            val toServerData = this.proxyToServerCoder.update(plainData)
            ctx.fireChannelRead(ctx.alloc().buffer().writeBytes(toServerData))
            //}
            clientBuffer.release()
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        this.clientBuffer?.release()
        this.clientBuffer = null
        this.status = Status.Inactive
        super.channelInactive(ctx)
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg is ByteBuf) {
            when (this.status) {
                Status.Inactive,
                Status.Handshaking -> {
                    msg.release()
                    promise.setSuccess()
                    ctx.close()
                }

                Status.Established -> {
                    val serverData = ByteArray(msg.readableBytes())
                    msg.readBytes(serverData)
                    msg.release()
                    val plainData = this.serverToProxyCoder.update(serverData)
                    val toClientData = this.proxyToClientCoder.update(plainData)
                    ctx.write(ctx.alloc().buffer().writeBytes(toClientData), promise)
                }
            }
        } else {
            super.write(ctx, msg, promise)
        }
    }
}