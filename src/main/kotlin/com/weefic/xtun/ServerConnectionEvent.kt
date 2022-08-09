package com.weefic.xtun

import java.net.InetSocketAddress

class ServerConnectionRequest(val address: InetSocketAddress)

enum class ServerConnectionResult {
    Success,
    NetworkUnreachable,
    AuthenticationRejected,
    DataFlowInvalid,
}