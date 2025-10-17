package com.weefic.xtun.utils

import org.slf4j.Logger
import org.slf4j.Marker
import java.io.IOException

object ChannelLoggingUtils {
    fun logChannelException(logger: Logger, tag: Marker, message: String, e: Throwable) {
        if (logger.isErrorEnabled(tag)) {
            if (e is IOException) {
                val errorMessage = e.message
                when (errorMessage) {
                    "Connection reset",
                    "Connection reset by peer",
                    "Connection timed out",
                    "Broken pipe" -> {
                        logger.error(tag, "{}: [{}]{}", message, e.javaClass.simpleName, errorMessage)
                        return
                    }
                }
            }
            logger.error(tag, message, e)
        }
    }
}