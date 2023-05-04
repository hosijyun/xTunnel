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
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ClientConnectionMTProtoInboundHandler(
    connectionId: Long,
    secret: String
) : ChannelDuplexHandler() {
    companion object {
        private val LOG = LoggerFactory.getLogger("client-connection-mtproto")
        private val DC_IPV4 = arrayOf(
            "149.154.175.50",
            "149.154.167.51",
            "149.154.175.100",
            "149.154.167.91",
            "149.154.171.5"
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
    private val proxyToServerCoder = Cipher.getInstance("AES/CTR/NoPadding")
    private var clientBuffer: ByteBuf? = null

    private val serverToProxyCoder = Cipher.getInstance("AES/CTR/NoPadding")
    private val proxyToClientCoder = Cipher.getInstance("AES/CTR/NoPadding")

    init {
        val sharedSecret = ByteArray(secret.length / 2)
        repeat(sharedSecret.size) {
            sharedSecret[it] = Integer.parseInt(secret.substring(it * 2, (it + 1) * 2), 16).toByte()
        }
        this.sharedSecret = sharedSecret
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is ByteBuf) {
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
        val clientHandshakeData = ByteArray(64)
        buffer.readBytes(clientHandshakeData)
        val clientHandshakeDataInv = clientHandshakeData.reversedArray()

        val clientSharedDecryptKey = clientHandshakeData.sliceArray(8 until 40)
        val clientSharedDecryptIV = clientHandshakeData.sliceArray(40 until 56)
        val clientSharedEncryptKey = clientHandshakeDataInv.sliceArray(8 until 40)
        val clientSharedEncryptIV = clientHandshakeDataInv.sliceArray(40 until 56)

        val sha256 = MessageDigest.getInstance("SHA256")
        sha256.update(clientSharedDecryptKey)
        sha256.update(this.sharedSecret)
        val clientDecryptKey = sha256.digest()

        sha256.reset()
        sha256.update(clientSharedEncryptKey)
        sha256.update(this.sharedSecret)
        val clientEncryptKey = sha256.digest()

        this.clientToProxyCoder.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(clientDecryptKey, "AES"),
            IvParameterSpec(clientSharedDecryptIV)
        )
        this.proxyToClientCoder.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(clientEncryptKey, "AES"),
            IvParameterSpec(clientSharedEncryptIV)
        )

        val decodedHandshake = this.clientToProxyCoder.doFinal(clientHandshakeData)
        val protocol = decodedHandshake.sliceArray(56 until 60)
        val dataCenterIndex = Endian.LE.getShort(decodedHandshake, 60).toInt() - 1
        if (dataCenterIndex < 0 || dataCenterIndex >= DC_IPV4.size) {
            ctx.close()
            buffer.release()
            this.status = Status.Inactive
        }
        val dataCenter = DC_IPV4[dataCenterIndex]

        ctx.fireChannelRead(
            ServerConnectionRequest(
                InetSocketAddress.createUnresolved(
                    dataCenter,
                    443
                ), null
            )
        )

        val serverHandshakeData = this.generateTGHandshakeData()
        protocol.copyInto(serverHandshakeData, 56)
        val serverHandshakeDataInv = serverHandshakeData.reversedArray()

        val serverSharedEncryptKey = serverHandshakeData.sliceArray(8 until 40)
        val serverSharedEncryptIV = serverHandshakeData.sliceArray(40 until 56)
        val serverSharedDecryptKey = serverHandshakeDataInv.sliceArray(8 until 40)
        val serverSharedDecryptIV = serverHandshakeDataInv.sliceArray(40 until 56)
        this.proxyToServerCoder.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(serverSharedEncryptKey, "AES"),
            IvParameterSpec(serverSharedEncryptIV)
        )
        this.serverToProxyCoder.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(serverSharedDecryptKey, "AES"),
            IvParameterSpec(serverSharedDecryptIV)
        )
        val encryptedServerHandshakeData = this.proxyToServerCoder.update(serverHandshakeData)
        check(encryptedServerHandshakeData != null && encryptedServerHandshakeData.size == 64)
        encryptedServerHandshakeData.copyInto(serverHandshakeData, 56, 56, 64)
        ctx.fireChannelRead(ctx.alloc().buffer().writeBytes(serverHandshakeData))

        this.status = Status.Established
        this.processClientData(ctx, buffer)
    }

    private fun generateTGHandshakeData(): ByteArray {
        val serverHandshakeData = ByteArray(64)
        while (true) {
            SecureRandom().nextBytes(serverHandshakeData)
            if (serverHandshakeData[0] == 0xef.toByte()) {
                continue
            }
            val firstInt = Endian.LE.getInt(serverHandshakeData, 0)
            when (firstInt) {
                0x44414548,
                0x54534f50,
                0x20544547,
                0x4954504f,
                0x02010316,
                0xddddddddL.toInt(),
                0xeeeeeeeeL.toInt() -> continue
            }
            val secondInt = Endian.LE.getInt(serverHandshakeData, 4)
            if (secondInt == 0) {
                continue
            }
            break
        }
        return serverHandshakeData
    }

    private fun processClientData(ctx: ChannelHandlerContext, clientBuffer: ByteBuf) {
        if (clientBuffer.readableBytes() > 0) {
            val buffer = ByteArray(clientBuffer.readableBytes())
            clientBuffer.readBytes(buffer)
            val plainData = this.clientToProxyCoder.update(buffer)
            if (plainData != null) {
                val encodedData = this.proxyToServerCoder.update(plainData)
                if (encodedData != null) {
                    ctx.fireChannelRead(ctx.alloc().buffer().writeBytes(encodedData))
                }
            }
        }
        clientBuffer.release()
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
                Status.Inactive, Status.Handshaking -> {
                    msg.release()
                    promise.setSuccess()
                    ctx.close()
                }

                Status.Established -> {
                    val buffer = ByteArray(msg.readableBytes())
                    msg.readBytes(buffer)
                    msg.release()

                    val plainData = this.serverToProxyCoder.update(buffer)
                    if (plainData != null) {
                        val encodedData = this.proxyToClientCoder.update(plainData)
                        if (encodedData != null) {
                            ctx.write(ctx.alloc().buffer().writeBytes(encodedData), promise)
                        } else {
                            promise.setSuccess()
                        }
                    } else {
                        promise.setSuccess()
                    }
                }
            }
        } else {
            super.write(ctx, msg, promise)
        }
    }
}