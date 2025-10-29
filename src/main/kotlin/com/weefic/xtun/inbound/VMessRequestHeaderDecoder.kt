package com.weefic.xtun.inbound

import com.weefic.xtun.ServerConnectionRequest
import com.weefic.xtun.Tunnel
import com.weefic.xtun.utils.Endian
import com.weefic.xtun.utils.toByteArray
import com.weefic.xtun.vmess.*
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.ReplayingDecoder
import org.bouncycastle.crypto.digests.MD5Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.modes.CFBBlockCipher
import org.bouncycastle.crypto.modes.CFBModeCipher
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.slf4j.LoggerFactory
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.util.*
import java.util.zip.CRC32
import kotlin.experimental.and

class VMessRequestHeaderDecoder(
    connectionId: Long,
    val passwords: List<String>
) : ReplayingDecoder<VMessRequestHeaderDecoder.Status>(Status.Initialize) {


    companion object {
        private val LOG = LoggerFactory.getLogger("client-connection-vmess")
        const val NAME = "ClientConnectionVMessInboundHandler"

        private fun isAEADAuthID(key: ByteArray, data: ByteArray): Boolean {
            val cipher = VMessAuthID.newCipherFromKey(key, false)
            val decryptedData = ByteArray(16)
            cipher.processBlock(data, 0, decryptedData, 0)
            // Check CRC32
            val crc32 = CRC32()
            crc32.update(decryptedData, 0, 12)
            val crc32Value = crc32.value.toInt()
            val decryptedCrc32Value = Endian.BE.getInt(decryptedData, 12)
            return crc32Value == decryptedCrc32Value
        }

        private fun decryptTimestamp(account: VMessAccount, data: ByteArray, timestamp: Long): Long? {
            val ids = listOf(account.id) + account.alterIDs
            val mac = HMac(MD5Digest())
            val guessTimestampData = ByteArray(8)
            val hash = ByteArray(16)
            for (id in ids) {
                mac.init(KeyParameter(id.uuid.toByteArray()))
                for (delta in -35L..50L) {
                    val guessTimestamp = timestamp + delta
                    Endian.BE.putLong(guessTimestampData, 0, guessTimestamp)
                    mac.update(guessTimestampData, 0, guessTimestampData.size)
                    mac.doFinal(hash, 0)
                    if (hash.contentEquals(data)) {
                        return guessTimestamp
                    }
                }
            }
            return null
        }

        private fun createCipherFromHead16(account: VMessAccount, data: ByteArray, timestamp: Long): CFBModeCipher? {
            val userTimestamp = decryptTimestamp(account, data, timestamp) ?: return null

            val x = ByteArray(8)
            Endian.BE.putLong(x, 0, userTimestamp)
            val md5 = MD5Digest()
            md5.update(x, 0, x.size)
            md5.update(x, 0, x.size)
            md5.update(x, 0, x.size)
            md5.update(x, 0, x.size)

            val iv = ByteArray(md5.digestSize)
            md5.doFinal(iv, 0)

            val cipher = CFBBlockCipher.newInstance(AESEngine.newInstance(), iv.size * 8)
            val params = ParametersWithIV(KeyParameter(account.id.cmdKey), iv)
            cipher.init(false, params)
            return cipher
        }
    }

    sealed class Status {
        object Initialize : Status()
        class AEADAuthIDRead(val authID: ByteArray) : Status() // AEAD Auth ID read
        class AEADHeaderLengthRead(val authID: ByteArray, val connectionNonce: ByteArray, val headerSize: Int) : Status() //
        class LegacyHeaderStart(val cipher: CFBModeCipher) : Status()
        class LegacyHeaderPrefix(val cipher: CFBModeCipher, val prefix: ByteArray) : Status()
        class HeaderData(val isAEADRequest: Boolean, val header: ByteArray) : Status()
        object Failure : Status()
    }

    private val TAG = Tunnel.MARKERS.getDetachedMarker("-$connectionId")
    private val account = VMessAccount(
        id = UUID.fromString("4c9eed81-01d5-4b49-a5b3-9e6ae815403b"),
        alterID = 32,
    )


    private var user: String? = null
    private fun ChannelHandlerContext.writeData(data: ByteArray): ChannelFuture {
        return this.writeAndFlush(this.alloc().buffer().writeBytes(data))
    }


    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        when (val state = this.state()) {
            is Status.Initialize -> {
                val head16 = ByteArray(16)
                msg.readBytes(head16)
                // Detect AEAD
                val isAEAD = isAEADAuthID(this.account.id.cmdKey, head16)
                if (isAEAD) {
                    this.checkpoint(Status.AEADAuthIDRead(head16))
                } else if (this.account.aead) {
                    this.checkpoint(Status.Failure)
                    throw DecoderException("AEAD required")
                } else {
                    val now = System.currentTimeMillis() / 1000
                    val cipher = createCipherFromHead16(this.account, head16, now)
                    if (cipher == null) {
                        this.checkpoint(Status.Failure)
                        throw DecoderException("Authentication failed")
                    } else {
                        checkpoint(Status.LegacyHeaderStart(cipher))
                    }
                }
            }

            is Status.AEADAuthIDRead -> {
                val headerSizeEncrypted = ByteArray(18)
                val connectionNonce = ByteArray(8)
                msg.readBytes(headerSizeEncrypted)
                msg.readBytes(connectionNonce)

                val headerSizeKey = VMessKDF.kdf16(this.account.id.cmdKey, VMessConsts.KDFSaltConstVMessHeaderPayloadLengthAEADKey, state.authID, connectionNonce)
                val headerSizeNonce = VMessKDF.kdf(this.account.id.cmdKey, VMessConsts.KDFSaltConstVMessHeaderPayloadLengthAEADIV, state.authID, connectionNonce).copyOfRange(0, 12)
                val headerSizeCipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
                headerSizeCipher.init(false, AEADParameters(KeyParameter(headerSizeKey), 128, headerSizeNonce, state.authID))
                val headerSizeData = ByteArray(2)
                var processed = headerSizeCipher.processBytes(headerSizeEncrypted, 0, headerSizeEncrypted.size, headerSizeData, 0)
                processed += headerSizeCipher.doFinal(headerSizeData, processed)
                check(processed == 2)

                val headerSize = Endian.BE.getShort(headerSizeData, 0).toInt() and 0xFFFF
                this.checkpoint(Status.AEADHeaderLengthRead(state.authID, connectionNonce, headerSize))
            }

            is Status.AEADHeaderLengthRead -> {
                val headerDataEncrypted = ByteArray(state.headerSize + 16)
                msg.readBytes(headerDataEncrypted)

                val headerKey = VMessKDF.kdf16(this.account.id.cmdKey, VMessConsts.KDFSaltConstVMessHeaderPayloadAEADKey, state.authID, state.connectionNonce)
                val headerNonce = VMessKDF.kdf(this.account.id.cmdKey, VMessConsts.KDFSaltConstVMessHeaderPayloadAEADIV, state.authID, state.connectionNonce).copyOfRange(0, 12)

                val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
                cipher.init(false, AEADParameters(KeyParameter(headerKey), 128, headerNonce, state.authID))
                val headerData = ByteArray(state.headerSize)
                var processed = cipher.processBytes(headerDataEncrypted, 0, headerDataEncrypted.size, headerData, 0)
                processed += cipher.doFinal(headerData, processed)
                check(processed == state.headerSize)
                this.checkpoint(Status.HeaderData(true, headerData))
            }

            is Status.LegacyHeaderStart -> {
                val data = ByteArray(42)
                msg.readBytes(data)
                state.cipher.processBytes(data, 0, data.size, data, 0)
                this.checkpoint(Status.LegacyHeaderPrefix(state.cipher, data))
            }

            is Status.LegacyHeaderPrefix -> {
                val command = state.prefix[37]
                val paddingLength = state.prefix[35].toInt() shr 4 and 0xF
                var suffixDataSize: Int? = null
                if (command == 2.toByte()) { // MUX: No host/port
                    suffixDataSize = 38 + paddingLength + 4 - state.prefix.size
                } else {
                    val addressType = state.prefix[40]
                    if (addressType == 1.toByte()) { // IPV4
                        suffixDataSize = 38 + 2 + 1 + 4 + paddingLength + 4 - state.prefix.size
                    } else if (addressType == 2.toByte()) { // Domain
                        val domainLength = state.prefix[41].toInt() and 0xFF
                        suffixDataSize = 38 + 2 + 1 + 1 + domainLength + paddingLength + 4 - state.prefix.size
                    } else if (addressType == 3.toByte()) { // IPV6
                        suffixDataSize = 38 + 2 + 1 + 16 + paddingLength + 4 - state.prefix.size
                    } else {
                        this.checkpoint(Status.Failure)
                        throw DecoderException("Unknown address type: $addressType")
                    }
                }
                val suffix = ByteArray(suffixDataSize)
                msg.readBytes(suffix)
                state.cipher.processBytes(suffix, 0, suffix.size, suffix, 0)
                this.checkpoint(Status.HeaderData(false, state.prefix + suffix))
            }

            is Status.HeaderData -> {
                val version = VMessVersion.of(state.header[0]) ?: throw DecoderException("Unknown version type: ${state.header[0]}")
                val requestBodyIV = state.header.copyOfRange(1, 17)
                val requestBodyKey = state.header.copyOfRange(17, 33)
                val responseHeader = state.header[33]
                val option = state.header[34]
                val paddingLength = state.header[35].toInt() shr 4 and 0xF
                val security = VMessSecurityType.of(state.header[35] and 0xF) ?: throw DecoderException("Unknown security type: ${state.header[35] and 0xF}")
                val keep = state.header[36]
                val command = VMessRequestCommand.of(state.header[37]) ?: throw DecoderException("Unknown command: $state.header[37]")
                val (requestAddress, paddingStart) = when (command) {
                    VMessRequestCommand.MUX -> VMessRequestAddress("v1.mux.cool", 0) to 38
                    VMessRequestCommand.TCP, VMessRequestCommand.UDP -> {
                        val port = Endian.BE.getShort(state.header, 38).toInt() and 0xFFFF
                        val addressType = state.header[40]
                        val paddingStart: Int
                        val address: String
                        if (addressType == 1.toByte()) { // IPV4
                            address = Inet4Address.getByAddress(state.header.copyOfRange(41, 45)).hostAddress
                            paddingStart = 45
                        } else if (addressType == 2.toByte()) { // Domain
                            val domainLength = state.header[41].toInt() and 0xFF
                            address = state.header.copyOfRange(42, 42 + domainLength).decodeToString()
                            paddingStart = 42 + domainLength
                        } else if (addressType == 3.toByte()) { // IPV6
                            address = Inet6Address.getByAddress(state.header.copyOfRange(41, 57)).hostAddress
                            paddingStart = 57
                        } else {
                            // Unknown
                            checkpoint(Status.Failure)
                            throw DecoderException("Unknown address type: $addressType")
                        }
                        VMessRequestAddress(address, port) to paddingStart
                    }
                }
                if (paddingLength > 0) {
                    // in header
                }
                // TODO Checksum
                if (security == VMessSecurityType.Unknown || security == VMessSecurityType.Auto) {
                    throw DecoderException("Unknown security type: ${security}")
                }
                // SessionID
                val header = VMessHeader(
                    version = version,
                    isAEADRequest = state.isAEADRequest,
                    requestBodyIV = requestBodyIV,
                    requestBodyKey = requestBodyKey,
                    responseHeader = responseHeader,
                    options = option.toInt() and 0xFF,
                    command = command,
                    address = requestAddress,
                    security = security,
                )



                if (header.command == VMessRequestCommand.TCP) { // TCP
                    ctx.fireChannelRead(ServerConnectionRequest(InetSocketAddress.createUnresolved(requestAddress.host, requestAddress.port), this.account.id.toString()))
                    this.processConnected(ctx, header)
                } else { // 2: UDP 3:MUX
                    this.checkpoint(Status.Failure)
                    throw DecoderException("Unknown command: $command")
                }
            }

            else -> {
                throw UnsupportedOperationException("Unknown state: $state")
            }
        }
    }

    private fun processConnected(ctx: ChannelHandlerContext, requestHeader: VMessHeader) {
        var sizeParser = if (requestHeader.options and VMessOptions.RequestOptionChunkMasking != 0) {
            ShakeSizeParser(requestHeader.requestBodyIV)
        } else {
            PlainChunkSizeParser()
        }
        var padding: PaddingLengthGenerator? = null
        if (requestHeader.options and VMessOptions.RequestOptionGlobalPadding != 0) {
            padding = sizeParser as? PaddingLengthGenerator
            if (padding == null) {
                this.checkpoint(Status.Failure)
                throw DecoderException("Padding length generator is not supported")
            }
        }
        when (requestHeader.security) {
            VMessSecurityType.None -> {
                if (requestHeader.options and VMessOptions.RequestOptionChunkStream != 0) {
                    if (requestHeader.command.getTransferType() == VMessTransferType.Stream) {
                        println()
                    } else {
                        println()
                    }
                    throw UnsupportedOperationException()
                } else {
                    val decoder = VMessDataPassthoughtDecoder()
                    ctx.channel().pipeline().addAfter(NAME, null, VMessDataDecodeHandler(this.account, requestHeader, sizeParser, decoder, padding))
                    ctx.channel().pipeline().remove(this)
                }
            }

            VMessSecurityType.AES128GCM -> {
                val authenticator = VMessAEADAuthenticator(
                    aeadKey = requestHeader.requestBodyKey,
                    aead = GCMBlockCipher.newInstance(AESEngine.newInstance()),
                    nonceGenerator = VMessAEADNonceGenerator(requestHeader.requestBodyIV, 12),
                    additionalDataGenerator = VMessEmptyBytesGenerator(),
                )
                if (requestHeader.options and VMessOptions.RequestOptionAuthenticatedLength != 0) {
                    val authenticatedLengthKey = VMessKDF.kdf16(requestHeader.requestBodyKey, "auth_len".encodeToByteArray())
                    val lengthAuth = VMessAEADAuthenticator(
                        aeadKey = authenticatedLengthKey,
                        aead = GCMBlockCipher.newInstance(AESEngine.newInstance()),
                        nonceGenerator = VMessAEADNonceGenerator(requestHeader.requestBodyIV, 12),
                        additionalDataGenerator = VMessEmptyBytesGenerator(),
                    )
                    sizeParser = AEADChunkSizeParser(lengthAuth)
                }

                val decoder = VMessAEADDataDecoder(authenticator, sizeParser, requestHeader.command.getTransferType())
                ctx.channel().pipeline().addAfter(NAME, null, VMessDataDecodeHandler(this.account, requestHeader, sizeParser, decoder, padding))
                ctx.channel().pipeline().remove(this)
            }

            else -> {
                throw java.lang.UnsupportedOperationException("Unknown security type")
            }
        }
        ctx.fireChannelRead(
            ServerConnectionRequest(
                InetSocketAddress.createUnresolved(requestHeader.address.host, requestHeader.address.port),
                this.account.id.toString()
            )
        )
    }
}