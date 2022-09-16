package com.weefic.xtun

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo


data class UserCredential(
    @JsonProperty(required = true) val user: String,
    @JsonProperty(required = true) val password: String
)

enum class ShadowsocksEncryptionMethod {
    @JsonProperty("none")
    None,

    @JsonProperty("aes-128-gcm")
    AES128GCM,

    @JsonProperty("aes-192-gcm")
    AES192GCM,

    @JsonProperty("aes-256-gcm")
    AES256GCM,

    @JsonProperty("aes-128-cfb")
    AES128CFB,

    @JsonProperty("aes-192-cfb")
    AES192CFB,

    @JsonProperty("aes-256-cfb")
    AES256CFB,

    @JsonProperty("aes-128-ctr")
    AES128CTR,

    @JsonProperty("aes-192-ctr")
    AES192CTR,

    @JsonProperty("aes-256-ctr")
    AES256CTR,

    @JsonProperty("camellia-128-cfb")
    Camellia128CFB,

    @JsonProperty("camellia-192-cfb")
    Camellia192CFB,

    @JsonProperty("camellia-256-cfb")
    Camellia256CFB,

    @JsonProperty("chacha20-ietf-poly1305")
    Chacha20IETFPoly1305,

    // XChacha20IETFPoly1305,

    @JsonProperty("salsa20")
    Salsa20,

    @JsonProperty("chacha20")
    Chacha20,

    @JsonProperty("chacha20-ietf")
    Chacha20IETF,
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(name = "http", value = TunnelInboundConfig.Http::class),
    JsonSubTypes.Type(name = "socks5", value = TunnelInboundConfig.Socks5::class),
    JsonSubTypes.Type(name = "shadowsocks", value = TunnelInboundConfig.Shadowsocks::class),
    JsonSubTypes.Type(name = "nat", value = TunnelInboundConfig.NAT::class),
)
sealed class TunnelInboundConfig {
    abstract val id: String
    abstract val port: Int
    abstract val host: String?


    data class Http(
        @JsonProperty(required = true) override val id: String,
        @JsonProperty(required = true) override val port: Int,
        @JsonProperty(required = false) override val host: String? = null,
        val users: List<UserCredential>? = null,
    ) : TunnelInboundConfig()

    data class Socks5(
        @JsonProperty(required = true) override val id: String,
        @JsonProperty(required = true) override val port: Int,
        @JsonProperty(required = false) override val host: String? = null,
        val users: List<UserCredential>? = null,
    ) : TunnelInboundConfig()

    data class Shadowsocks(
        @JsonProperty(required = true) override val id: String,
        @JsonProperty(required = true) override val port: Int,
        @JsonProperty(required = true) val method: ShadowsocksEncryptionMethod,
        @JsonProperty(required = true) val password: String,
        @JsonProperty(required = false) override val host: String? = null,
    ) : TunnelInboundConfig()

    data class NAT(
        @JsonProperty(required = true) override val id: String,
        @JsonProperty(required = true) override val port: Int,
        @JsonProperty(required = true, value = "server_host") val serverHost: String,
        @JsonProperty(required = true, value = "server_port") val serverPort: Int,
        @JsonProperty(required = false) override val host: String? = null,
    ) : TunnelInboundConfig()
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(name = "direct", value = TunnelOutboundConfig.Direct::class),
    JsonSubTypes.Type(name = "blackhole", value = TunnelOutboundConfig.Blackhole::class),
    JsonSubTypes.Type(name = "echo", value = TunnelOutboundConfig.Echo::class),
    JsonSubTypes.Type(name = "http", value = TunnelOutboundConfig.Http::class),
    JsonSubTypes.Type(name = "socks5", value = TunnelOutboundConfig.Socks5::class),
    JsonSubTypes.Type(name = "shadowsocks", value = TunnelOutboundConfig.Shadowsocks::class),
)
sealed class TunnelOutboundConfig {
    abstract val id: String

    data class Direct(
        @JsonProperty(required = true) override val id: String,
    ) : TunnelOutboundConfig()

    data class Blackhole(
        @JsonProperty(required = true) override val id: String,
    ) : TunnelOutboundConfig()

    data class Echo(
        @JsonProperty(required = true) override val id: String,
    ) : TunnelOutboundConfig()

    data class Http(
        @JsonProperty(required = true) override val id: String,
        @JsonProperty(required = true) val host: String,
        @JsonProperty(required = true) val port: Int,
        val user: UserCredential? = null
    ) : TunnelOutboundConfig()

    data class Socks5(
        @JsonProperty(required = true) override val id: String,
        @JsonProperty(required = true) val host: String,
        @JsonProperty(required = true) val port: Int,
        @JsonProperty(required = true) val user: UserCredential? = null
    ) : TunnelOutboundConfig()

    data class Shadowsocks(
        @JsonProperty(required = true) override val id: String,
        @JsonProperty(required = true) val host: String,
        @JsonProperty(required = true) val port: Int,
        @JsonProperty(required = true) val method: ShadowsocksEncryptionMethod,
        @JsonProperty(required = true) val password: String
    ) : TunnelOutboundConfig()
}

data class TunnelRouteMatchConfig(
    val user: String? = null,
    @JsonProperty(value = "client_address") val clientAddresses: List<String>? = null,
    @JsonProperty(value = "server_address") val serverAddresses: List<String>? = null,
    val pac: String? = null,
)

data class TunnelRouteConfig(
    @JsonProperty(required = true, value = "in") val inbound: String,
    @JsonProperty(required = true, value = "out") val outbound: String,
    @JsonProperty(required = false, value = "match") val match: TunnelRouteMatchConfig? = null,
)

class TunnelConfig(
    @JsonProperty(required = true) val route: List<TunnelRouteConfig>,
    @JsonProperty(required = true, value = "in") val inbound: List<TunnelInboundConfig>,
    @JsonProperty(required = true, value = "out") val outbound: List<TunnelOutboundConfig>,
)