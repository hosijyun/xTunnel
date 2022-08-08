package com.weefic.xtun

interface AbstractConnection {
    abstract fun peerWritableChanged()
    abstract fun triggerEvent(event: Any)
    abstract fun write(message: Any)
    abstract fun writeAndFlush(message: Any)
    abstract fun flush()
    abstract fun close()
}