package com.weefic.xtun.utils

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpRequest
import java.util.regex.Pattern

object HttpProxyUtils {
    private val HTTP_PREFIX = Pattern.compile("^https?://.*", Pattern.CASE_INSENSITIVE)

    fun identifyHostAndPort(httpRequest: HttpRequest): String {
        var hostAndPort = this.parseHostAndPort(httpRequest)
        if (hostAndPort.isBlank()) {
            val hosts = httpRequest.headers().getAll(HttpHeaderNames.HOST)
            if (hosts != null && hosts.isNotEmpty()) {
                hostAndPort = hosts[0]
            }
        }
        return hostAndPort
    }


    fun parseHostAndPort(httpRequest: HttpRequest): String {
        return parseHostAndPort(httpRequest.uri())
    }

    fun parseHostAndPort(uri: String): String {
        val tempUri = if (!HTTP_PREFIX.matcher(uri).matches()) {
            uri
        } else {
            uri.substringAfter("://")
        }
        val hostAndPort = if (tempUri.contains("/")) {
            tempUri.substring(0, tempUri.indexOf("/"))
        } else {
            tempUri
        }
        return hostAndPort
    }
}