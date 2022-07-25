package com.weefic.xtun

class ServerConnectionRequest(val host: String, val port: Int)

enum class ServerConnectionResult {
    Success,
    NetworkUnreachable,
    AuthenticationRejected,
    DataFlowInvalid,
}