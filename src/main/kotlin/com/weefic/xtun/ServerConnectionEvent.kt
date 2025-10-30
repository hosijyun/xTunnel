package com.weefic.xtun

import java.net.InetSocketAddress

class ServerConnectionRequest(
    val address: InetSocketAddress,
    val user: String?
)

enum class ServerConnectionResult {
    Success,
    NetworkUnreachable,
    AuthenticationRejected,
    DataFlowInvalid,
}