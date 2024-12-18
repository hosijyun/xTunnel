package com.weefic.xtun

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.util.*


data class UserCredential(
    @JsonProperty(required = true) val user: String,
    @JsonProperty(required = true) val password: String
)

data class TlsKeyPair(
    @JsonProperty(required = true, value = "id") val id: String,
    @JsonProperty(required = true, value = "certificatePath") val certificate: String,
    @JsonProperty(required = true, value = "keyPath") val keyPath: String,
)

data class TlsConfig(
    @JsonProperty(required = true, value = "keyPairs")
    val keyPairs: List<TlsKeyPair>,
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
    JsonSubTypes.Type(name = "mix", value = TunnelInboundConfig.Mix::class),
    JsonSubTypes.Type(name = "http", value = TunnelInboundConfig.Http::class),
    JsonSubTypes.Type(name = "socks5", value = TunnelInboundConfig.Socks5::class),
    JsonSubTypes.Type(name = "shadowsocks", value = TunnelInboundConfig.Shadowsocks::class),
    JsonSubTypes.Type(name = "nat", value = TunnelInboundConfig.NAT::class),
    JsonSubTypes.Type(name = "mtproto", value = TunnelInboundConfig.MTProto::class),
)
sealed class TunnelInboundConfig {
    abstract val port: Int
    abstract val host: String?
    abstract val rule: List<TunnelRouteConfig>
    abstract val useDefaultRules: Boolean
    abstract val tls: String?

    data class Mix(
        @JsonProperty(required = true) override val port: Int,
        @JsonProperty(required = false) override val host: String? = null,
        @JsonProperty(required = false) val users: List<UserCredential> = emptyList(),
        @JsonProperty(required = false) override val rule: List<TunnelRouteConfig> = emptyList(),
        @JsonProperty(required = false) override val useDefaultRules: Boolean = false,
        @JsonProperty(required = false) override val tls: String? = null,
    ) : TunnelInboundConfig()

    data class Http(
        @JsonProperty(required = true) override val port: Int,
        @JsonProperty(required = false) override val host: String? = null,
        @JsonProperty(required = false) val users: List<UserCredential> = emptyList(),
        @JsonProperty(required = false) override val rule: List<TunnelRouteConfig> = emptyList(),
        @JsonProperty(required = false) override val useDefaultRules: Boolean = false,
        @JsonProperty(required = false) override val tls: String? = null,
    ) : TunnelInboundConfig()

    data class Socks5(
        @JsonProperty(required = true) override val port: Int,
        @JsonProperty(required = false) override val host: String? = null,
        @JsonProperty(required = false) val users: List<UserCredential> = emptyList(),
        @JsonProperty(required = false) override val rule: List<TunnelRouteConfig> = emptyList(),
        @JsonProperty(required = false) override val useDefaultRules: Boolean = false,
        @JsonProperty(required = false) override val tls: String? = null,
    ) : TunnelInboundConfig()

    data class Shadowsocks(
        @JsonProperty(required = true) override val port: Int,
        @JsonProperty(required = true) val method: ShadowsocksEncryptionMethod,
        @JsonProperty(required = true) val password: String,
        @JsonProperty(required = false) override val host: String? = null,
        @JsonProperty(required = false) override val rule: List<TunnelRouteConfig> = emptyList(),
        @JsonProperty(required = false) override val useDefaultRules: Boolean = false,
        @JsonProperty(required = false) override val tls: String? = null,
    ) : TunnelInboundConfig()

    data class NAT(
        @JsonProperty(required = true) override val port: Int,
        @JsonProperty(required = true, value = "server_host") val serverHost: String,
        @JsonProperty(required = true, value = "server_port") val serverPort: Int,
        @JsonProperty(required = false) override val host: String? = null,
        @JsonProperty(required = false) override val rule: List<TunnelRouteConfig> = emptyList(),
        @JsonProperty(required = false) override val useDefaultRules: Boolean = false,
        @JsonProperty(required = false) override val tls: String? = null,
    ) : TunnelInboundConfig()

    data class MTProto(
        @JsonProperty(required = true) override val port: Int,
        @JsonProperty(required = true) val secret: String,
        @JsonProperty(required = false) override val host: String? = null,
        @JsonProperty(required = false) override val rule: List<TunnelRouteConfig> = emptyList(),
        @JsonProperty(required = false) override val useDefaultRules: Boolean = false,
        @JsonProperty(required = false) override val tls: String? = null,
    ) : TunnelInboundConfig()
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(name = "direct", value = TunnelOutboundConfig.Direct::class),
    JsonSubTypes.Type(name = "blackhole", value = TunnelOutboundConfig.Blackhole::class),
    JsonSubTypes.Type(name = "echo", value = TunnelOutboundConfig.Echo::class),
    JsonSubTypes.Type(name = "reject", value = TunnelOutboundConfig.Reject::class),
    JsonSubTypes.Type(name = "http", value = TunnelOutboundConfig.Http::class),
    JsonSubTypes.Type(name = "socks5", value = TunnelOutboundConfig.Socks5::class),
    JsonSubTypes.Type(name = "shadowsocks", value = TunnelOutboundConfig.Shadowsocks::class),
)
sealed class TunnelOutboundConfig {
    class Direct() : TunnelOutboundConfig()

    class Blackhole() : TunnelOutboundConfig()

    class Echo() : TunnelOutboundConfig()

    class Reject() : TunnelOutboundConfig()

    class Http(
        @JsonProperty(required = true) val host: String,
        @JsonProperty(required = true) val port: Int,
        @JsonProperty(required = false) val user: UserCredential? = null
    ) : TunnelOutboundConfig()

    data class Socks5(
        @JsonProperty(required = true) val host: String,
        @JsonProperty(required = true) val port: Int,
        @JsonProperty(required = false) val user: UserCredential? = null
    ) : TunnelOutboundConfig()

    data class Shadowsocks(
        @JsonProperty(required = true) val host: String,
        @JsonProperty(required = true) val port: Int,
        @JsonProperty(required = true) val method: ShadowsocksEncryptionMethod,
        @JsonProperty(required = true) val password: String
    ) : TunnelOutboundConfig()
}

data class TunnelRouteMatchConfig(
    @JsonProperty(value = "user") val users: List<String> = emptyList(),
    @JsonProperty(value = "client_address") val clientAddresses: List<String> = emptyList(),
    @JsonProperty(value = "server_address") val serverAddresses: List<String> = emptyList(),
    val pac: String? = null,
    @JsonProperty(value = "country")
    val countries: List<String> = emptyList(),
)

data class TunnelRouteRedirectConfig(
    @JsonProperty(value = "from", required = true) val from: String,
    @JsonProperty(value = "to", required = true) val to: String,
)

data class TunnelRouteConfig(
    @JsonProperty(required = false, value = "id") val id: String = UUID.randomUUID().toString(),
    @JsonProperty(required = true, value = "out") val outbound: String,
    @JsonProperty(required = false, value = "match") val match: TunnelRouteMatchConfig = TunnelRouteMatchConfig(),
    @JsonProperty(required = false, value = "redirect") val redirect: List<TunnelRouteRedirectConfig> = emptyList(),
)

data class TunnelAssets(
    @JsonProperty(required = false, value = "geoip") val geoip: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
open class TunnelConfig(
    @JsonProperty(required = true, value = "proxies") val proxies: Map<String, TunnelInboundConfig>,
    @JsonProperty(required = false, value = "out") val outbound: Map<String, TunnelOutboundConfig> = mapOf(),
    @JsonProperty(required = false, value = "assets") val assets: TunnelAssets? = null,
    @JsonProperty(required = false, value = "defaultRules") val defaultRules: List<TunnelRouteConfig> = mutableListOf(),
)