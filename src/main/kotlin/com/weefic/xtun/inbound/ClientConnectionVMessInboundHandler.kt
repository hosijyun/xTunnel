package com.weefic.xtun.inbound

import com.weefic.xtun.ServerConnectionRequest
import com.weefic.xtun.Tunnel
import com.weefic.xtun.async.AsyncInboundHandler
import com.weefic.xtun.async.AsyncReader
import com.weefic.xtun.utils.Endian
import com.weefic.xtun.utils.toByteArray
import com.weefic.xtun.vmess.*
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
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

class ClientConnectionVMessInboundHandler(
    connectionId: Long,
    val passwords: List<String>
) : AsyncInboundHandler() {
    companion object {
        private val LOG = LoggerFactory.getLogger("client-connection-vmess")

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

    private sealed class Status {
        object Initialize : Status()
        class AEADAuthIDRead(val authID: ByteArray) : Status() // AEAD Auth ID read
        class AEADHeaderLengthRead(val authID: ByteArray, val connectionNonce: ByteArray, val headerSize: Int) : Status() //
        class LegacyHeaderStart(val cipher: CFBModeCipher) : Status()
        class LegacyHeaderPrefix(val cipher: CFBModeCipher, val prefix: ByteArray) : Status()
        class HeaderData(val header: ByteArray) : Status()
        class Established(
            val requestBodyIV: ByteArray,
            val options: Int,
            val securityType: VMessSecurityType?,
        ) : Status()

        object Closed : Status()
    }

    private val TAG = Tunnel.MARKERS.getDetachedMarker("-$connectionId")
    private var status: Status = Status.Initialize
    private val account = VMessAccount(
        id = UUID.fromString("4c9eed81-01d5-4b49-a5b3-9e6ae815403b"),
        alterID = 32,
    )


    private var user: String? = null


    private fun ChannelHandlerContext.writeData(data: ByteArray): ChannelFuture {
        return this.writeAndFlush(this.alloc().buffer().writeBytes(data))
    }

    private suspend fun decodeRequestHeader(ctx: ChannelHandlerContext, reader: AsyncReader): VMessHeader? {
        val head16 = reader.readFully(16)
        val foundAEAD = isAEADAuthID(this.account.id.cmdKey, head16)
        val header = if (foundAEAD) {
            val fixedSizeCmdKey = this.account.id.cmdKey
            val authID = head16

            val payloadHeaderLengthAEADEncrypted = reader.readFully(18)
            val connectionNonce = reader.readFully(8)

            val headerSizeKey = VMessKDF.kdf16(fixedSizeCmdKey, VMessConsts.KDFSaltConstVMessHeaderPayloadLengthAEADKey, authID, connectionNonce)
            val headerSizeNonce = VMessKDF.kdf(fixedSizeCmdKey, VMessConsts.KDFSaltConstVMessHeaderPayloadLengthAEADIV, authID, connectionNonce).copyOfRange(0, 12)
            val headerSizeCipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
            headerSizeCipher.init(false, AEADParameters(KeyParameter(headerSizeKey), 128, headerSizeNonce, authID))

            val headerSizeData = ByteArray(2)
            val headerSizeOffset = headerSizeCipher.processBytes(payloadHeaderLengthAEADEncrypted, 0, payloadHeaderLengthAEADEncrypted.size, headerSizeData, 0)
            headerSizeCipher.doFinal(headerSizeData, headerSizeOffset)
            val headerSize = Endian.BE.getShort(headerSizeData, 0).toInt() and 0xFFFF

            val headerEncrypted = reader.readFully(headerSize + 16)
            val headerKey = VMessKDF.kdf16(fixedSizeCmdKey, VMessConsts.KDFSaltConstVMessHeaderPayloadAEADKey, authID, connectionNonce)
            val headerNonce = VMessKDF.kdf(fixedSizeCmdKey, VMessConsts.KDFSaltConstVMessHeaderPayloadAEADIV, authID, connectionNonce).copyOfRange(0, 12)

            val headerCipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
            headerCipher.init(false, AEADParameters(KeyParameter(headerKey), 128, headerNonce, authID))
            val headerData = ByteArray(headerSize)
            val headerOffset = headerCipher.processBytes(headerEncrypted, 0, headerEncrypted.size, headerData, 0)
            headerCipher.doFinal(headerData, headerOffset)

            headerData // result
        } else {
            val now = System.currentTimeMillis() / 1000
            val cipher = createCipherFromHead16(this.account, head16, now) ?: return null
            val headerPrefix = reader.readFully(42)
            cipher.processBytes(headerPrefix, 0, headerPrefix.size, headerPrefix, 0)
            val command = headerPrefix[37]
            val paddingLength = headerPrefix[35].toInt() shr 4 and 0xF
            var suffixDataSize: Int
            if (command == 2.toByte()) { // MUX: No host/port
                suffixDataSize = 38 + paddingLength + 4 - headerPrefix.size
            } else {
                val addressType = headerPrefix[40]
                if (addressType == 1.toByte()) { // IPV4
                    suffixDataSize = 38 + 2 + 1 + 4 + paddingLength + 4 - headerPrefix.size
                } else if (addressType == 2.toByte()) { // Domain
                    val domainLength = headerPrefix[41].toInt() and 0xFF
                    suffixDataSize = 38 + 2 + 1 + 1 + domainLength + paddingLength + 4 - headerPrefix.size
                } else if (addressType == 3.toByte()) { // IPV6
                    suffixDataSize = 38 + 2 + 1 + 16 + paddingLength + 4 - headerPrefix.size
                } else {
                    return null
                }
            }
            val headerSuffix = reader.readFully(suffixDataSize)
            cipher.processBytes(headerSuffix, 0, headerSuffix.size, headerSuffix, 0)
            headerPrefix + headerSuffix
        }
        val version = VMessVersion.of(header[0]) ?: return null
        val requestBodyIV = header.copyOfRange(1, 17)
        val requestBodyKey = header.copyOfRange(17, 33)
        val responseHeader = header[33]
        val options = header[34].toInt() and 0xFF
        val paddingLength = header[35].toInt() shr 4 and 0xF
        val security = VMessSecurityType.of(header[35] and 0xF) ?: return null
        val reserved = header[36]
        val command = VMessRequestCommand.of(header[37]) ?: return null
        if (security == VMessSecurityType.Unknown || security == VMessSecurityType.Auto) {
            return null
        }
        if (command != VMessRequestCommand.MUX) { // TCP
            val port = Endian.BE.getShort(header, 38).toInt() and 0xFFFF
            val addressType = header[40]
            val address = if (addressType == 1.toByte()) { // IPV4
                Inet4Address.getByAddress(header.copyOfRange(41, 45)).hostAddress
            } else if (addressType == 2.toByte()) { // Domain
                val domainLength = header[41].toInt() and 0xFF
                header.copyOfRange(42, 42 + domainLength).decodeToString()
            } else if (addressType == 3.toByte()) { // IPV6
                Inet6Address.getByAddress(header.copyOfRange(41, 57)).hostAddress
            } else {
                // Unknown
                return null
            }
            return VMessHeader(
                version,
                requestBodyIV,
                requestBodyKey,
                responseHeader,
                options,
                security,
                command,
                VMessRequestAddress(address, port)
            )
        } else { // 3:MUX
            return null
        }
    }


    override suspend fun handle(ctx: ChannelHandlerContext, reader: AsyncReader) {
        val request = this.decodeRequestHeader(ctx, reader)
        if (request == null) {
            ctx.close()
            return
        }
        println()
    }


    fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        when (val status = this.status) {
            is Status.Initialize -> {
                if (msg.readableBytes() >= 16) {
                    val head16 = ByteArray(16)
                    msg.readBytes(head16)
                    // Detect AEAD
                    val isAEAD = isAEADAuthID(this.account.id.cmdKey, head16)
                    if (isAEAD) {
                        val generatedAuthID = head16
                        this.status = Status.AEADAuthIDRead(generatedAuthID)
                    } else if (this.account.aead) {
                        //
                        ctx.close()
                        this.status = Status.Closed
                        return
                    } else {
                        val now = System.currentTimeMillis() / 1000
                        val cipher = createCipherFromHead16(this.account, head16, now)
                        if (cipher == null) {
                            ctx.close()
                            this.status = Status.Closed
                            return
                        } else {
                            this.status = Status.LegacyHeaderStart(cipher)
                        }
                    }
                }
            }

            is Status.AEADAuthIDRead -> {
                if (msg.readableBytes() >= 26) {
                    val payloadHeaderLengthAEADEncrypted = ByteArray(18)
                    val connectionNonce = ByteArray(8)
                    msg.readBytes(payloadHeaderLengthAEADEncrypted)
                    msg.readBytes(connectionNonce)

                    val payloadHeaderLengthAEADKey = VMessKDF.kdf16(this.account.id.cmdKey, VMessConsts.KDFSaltConstVMessHeaderPayloadLengthAEADKey, status.authID, connectionNonce)
                    val payloadHeaderLengthAEADNonce = VMessKDF.kdf(this.account.id.cmdKey, VMessConsts.KDFSaltConstVMessHeaderPayloadLengthAEADIV, status.authID, connectionNonce).copyOfRange(0, 12)
                    val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
                    cipher.init(false, AEADParameters(KeyParameter(payloadHeaderLengthAEADKey), 128, payloadHeaderLengthAEADNonce, status.authID))
                    val aeadPayloadLengthSerializeBuffer = ByteArray(2)
                    val i = cipher.processBytes(payloadHeaderLengthAEADEncrypted, 0, payloadHeaderLengthAEADEncrypted.size, aeadPayloadLengthSerializeBuffer, 0)
                    cipher.doFinal(aeadPayloadLengthSerializeBuffer, i)
                    val headerSize = Endian.BE.getShort(aeadPayloadLengthSerializeBuffer, 0).toInt() and 0xFFFF
                    this.status = Status.AEADHeaderLengthRead(status.authID, connectionNonce, headerSize)
                }
            }

            is Status.AEADHeaderLengthRead -> {
                if (msg.readableBytes() >= status.headerSize + 16) {
                    val payloadHeaderAEADEncrypted = ByteArray(status.headerSize + 16)
                    msg.readBytes(payloadHeaderAEADEncrypted)

                    val payloadHeaderAEADKey = VMessKDF.kdf16(this.account.id.cmdKey, VMessConsts.KDFSaltConstVMessHeaderPayloadAEADKey, status.authID, status.connectionNonce)
                    val payloadHeaderAEADNonce = VMessKDF.kdf(this.account.id.cmdKey, VMessConsts.KDFSaltConstVMessHeaderPayloadAEADIV, status.authID, status.connectionNonce).copyOfRange(0, 12)

                    val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
                    cipher.init(false, AEADParameters(KeyParameter(payloadHeaderAEADKey), 128, payloadHeaderAEADNonce, status.authID))
                    val data = ByteArray(status.headerSize)
                    val i = cipher.processBytes(payloadHeaderAEADEncrypted, 0, payloadHeaderAEADEncrypted.size, data, 0)
                    cipher.doFinal(data, i)
                    this.status = Status.HeaderData(data)
                }
            }

            is Status.LegacyHeaderStart -> {
                if (msg.readableBytes() >= 42) {
                    val data = ByteArray(42)
                    msg.readBytes(data)
                    status.cipher.processBytes(data, 0, data.size, data, 0)
                    this.status = Status.LegacyHeaderPrefix(status.cipher, data)
                }
            }

            is Status.LegacyHeaderPrefix -> {
                val command = status.prefix[37]
                val paddingLength = status.prefix[35].toInt() shr 4 and 0xF
                var suffixDataSize: Int? = null
                if (command == 2.toByte()) { // MUX: No host/port
                    suffixDataSize = 38 + paddingLength + 4 - status.prefix.size
                } else {
                    val addressType = status.prefix[40]
                    if (addressType == 1.toByte()) { // IPV4
                        suffixDataSize = 38 + 2 + 1 + 4 + paddingLength + 4 - status.prefix.size
                    } else if (addressType == 2.toByte()) { // Domain
                        val domainLength = status.prefix[41].toInt() and 0xFF
                        suffixDataSize = 38 + 2 + 1 + 1 + domainLength + paddingLength + 4 - status.prefix.size
                    } else if (addressType == 3.toByte()) { // IPV6
                        suffixDataSize = 38 + 2 + 1 + 16 + paddingLength + 4 - status.prefix.size
                    } else {
                        ctx.close()
                        this.status = Status.Closed
                        return
                    }
                }
                if (msg.readableBytes() >= suffixDataSize) {
                    val suffix = ByteArray(suffixDataSize)
                    msg.readBytes(suffix)
                    status.cipher.processBytes(suffix, 0, suffix.size, suffix, 0)
                    this.status = Status.HeaderData(status.prefix + suffix)
                }
            }

            is Status.HeaderData -> {
                val version = status.header[0]
                val requestBodyIV = status.header.copyOfRange(1, 17)
                val requestBodyKey = status.header.copyOfRange(17, 33)
                val responseHeader = status.header[33]
                val option = status.header[34]
                val paddingLength = status.header[35].toInt() shr 4 and 0xF
                val security = VMessSecurityType.of(status.header[35] and 0xF)
                val keep = status.header[36]
                val command = status.header[37]
                if (command == 1.toByte()) { // TCP
                    val port = Endian.BE.getShort(status.header, 38).toInt() and 0xFFFF
                    val addressType = status.header[40]
                    val address = if (addressType == 1.toByte()) { // IPV4
                        Inet4Address.getByAddress(status.header.copyOfRange(41, 45)).hostAddress
                    } else if (addressType == 2.toByte()) { // Domain
                        val domainLength = status.header[41].toInt() and 0xFF
                        status.header.copyOfRange(42, 42 + domainLength).decodeToString()
                    } else if (addressType == 3.toByte()) { // IPV6
                        Inet6Address.getByAddress(status.header.copyOfRange(41, 57)).hostAddress
                    } else {
                        // Unknown
                        ctx.close()
                        this.status = Status.Closed
                        return
                    }
                    // PADDING
                    // CHECKSUM
                    ctx.fireChannelRead(
                        ServerConnectionRequest(
                            InetSocketAddress.createUnresolved(address, port),
                            this.account.id.toString()
                        )
                    )
                    this.status = Status.Established(
                        requestBodyIV = requestBodyIV,
                        options = option.toInt() and 0xFF,
                        securityType = security,
                    )
                    return
                } else { // 2: UDP 3:MUX
                    ctx.close()
                    this.status = Status.Closed
                    return
                }
            }

            is Status.Established -> {
                val sizeParser = if (status.options and VMessOptions.RequestOptionChunkMasking != 0) {
                    ShakeSizeCoder(status.requestBodyIV)
                } else {
                    PlainChunkSizeCoder()
                }
                when (status.securityType) {
                    VMessSecurityType.AES128GCM -> {

                    }

                    else -> {
                        ctx.close()
                        this.status = Status.Closed
                        return
                    }
                }
            }

            else -> {
                return
            }
        }
    }


    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        super.userEventTriggered(ctx, evt)
    }
}