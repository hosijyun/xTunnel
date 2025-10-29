package com.weefic.xtun

import ch.qos.logback.classic.ClassicConstants
import com.weefic.xtun.shadowsocks.cipher.ShadowSocksKeyDerive
import io.netty.util.ResourceLeakDetector
import org.bouncycastle.crypto.digests.SHAKEDigest
import org.bouncycastle.crypto.engines.ChaChaEngine
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import kotlin.io.encoding.Base64
import kotlin.system.exitProcess


private fun testAny() {
    val nonce = Base64.decode("zWWnbXXTjuQ44vOnKystQg==")
    val digest = SHAKEDigest(128)
    digest.update(nonce, 0, nonce.size)
    val buf = ByteArray(2)
    digest.doFinal(buf, 0)
    println()
}

fun main() {
    Security.addProvider(BouncyCastleProvider())
    testAny()
    exitProcess(0)


//    if (Conscrypt.isAvailable()) {
//        Security.insertProviderAt(Conscrypt.newProviderBuilder().build(), 0)
//    }
    System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "./logback.xml")
    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID)
    val config = TunnelConfig(
        proxies = mapOf(
            "in1" to TunnelInboundConfig.Http(port = 9812),
        ),
        outbound = mapOf(
            "out1" to TunnelOutboundConfig.Direct()
        )
    )
    xtun(config)
}