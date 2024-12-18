package com.weefic.xtun

import ch.qos.logback.classic.util.ContextInitializer
import io.netty.util.ResourceLeakDetector
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security


fun main() {
    Security.addProvider(BouncyCastleProvider())
    System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, "./logback.xml")
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