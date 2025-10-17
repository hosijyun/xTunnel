package com.weefic.xtun

import ch.qos.logback.classic.ClassicConstants
import com.weefic.xtun.shadowsocks.cipher.ShadowSocksKeyDerive
import io.netty.util.ResourceLeakDetector
import org.bouncycastle.crypto.engines.ChaChaEngine
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import kotlin.io.encoding.Base64
import kotlin.system.exitProcess


private fun testChacha() {
    val password = "Password"
    val iv1 = Base64.decode("apLCoUmR35o=")
    val key = ShadowSocksKeyDerive.genericDeriveKey(password, 32)
    val params = ParametersWithIV(KeyParameter(key), iv1)


    val c1 = ChaChaEngine()
    c1.init(true, params)

    val c2 = ChaChaEngine()
    c2.init(false, params)

    val d1 = Base64.decode("AwpkbnMuZ29vZ2xlADU=")
    println("IN:${Base64.encode(d1)}")
    c1.processBytes(d1, 0, d1.size, d1, 0)
    println("OUT:${Base64.encode(d1)}")

    val d2 = Base64.decode("ACJPbgEAAAEAAAAAAAAFbXRhbGsGZ29vZ2xlA2NvbQAAAQAB")
    println("IN:${Base64.encode(d2)}")
    c1.processBytes(d2, 0, d2.size, d2, 0)
    println("OUT:${Base64.encode(d2)}")

    println("========")
    println("IN:${Base64.encode(d1)}")
    c2.processBytes(d1, 0, d1.size, d1, 0)
    println("OUT:${Base64.encode(d1)}")


    println("IN:${Base64.encode(d2)}")
    c2.processBytes(d2, 0, d2.size, d2, 0)
    println("OUT:${Base64.encode(d2)}")

}

fun main() {
    Security.addProvider(BouncyCastleProvider())
    testChacha()
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