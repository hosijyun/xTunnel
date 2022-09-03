package com.weefic.xtun


data class UserCredential(val user: String, val password: String)

enum class ShadowsocksEncryptionMethod {
    AES128GCM,
    AES192GCM,
    AES256GCM,
    AES128CFB,
    AES192CFB,
    AES256CFB,
    AES128CTR,
    AES192CTR,
    AES256CTR,
    Camellia128CFB,
    Camellia192CFB,
    Camellia256CFB,
    Chacha20IETFPoly1305,

    // XChacha20IETFPoly1305,
    Salsa20,
    Chacha20,
    Chacha20IETF,
}

sealed class TunnelInboundConfig(val port: Int) {
    class Http(port: Int, val credentials: List<UserCredential>? = null) : TunnelInboundConfig(port)
    class Socks5(port: Int, val credentials: List<UserCredential>? = null) : TunnelInboundConfig(port)
    class Shadowsocks(port: Int, val encryptionMethod: ShadowsocksEncryptionMethod, val password: String) : TunnelInboundConfig(port)
}

sealed class TunnelOutboundConfig() {
    object Direct : TunnelOutboundConfig()
    object Blackhole : TunnelOutboundConfig()
    object Echo : TunnelOutboundConfig()
    class Http(val host: String, val port: Int, val credential: UserCredential? = null) : TunnelOutboundConfig()
    class Socks5(val host: String, val port: Int, val credential: UserCredential? = null) : TunnelOutboundConfig()
    class Shadowsocks(val host: String, val port: Int, val encryptionMethod: ShadowsocksEncryptionMethod, val password: String) : TunnelOutboundConfig()
}

class TunnelRouteConfig(
    val inbound: String,
    val outbound: String,
    val clientAddress: String? = null,
    val user: String? = null,
)

class TunnelConfig(
    val route: List<TunnelRouteConfig>,
    val inbound: Map<String, TunnelInboundConfig>,
    val outbound: Map<String, TunnelOutboundConfig>,
)