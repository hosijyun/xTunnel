package com.weefic.xtun

import java.net.InetSocketAddress

interface PAC {
    fun accept(address: InetSocketAddress): Boolean
}