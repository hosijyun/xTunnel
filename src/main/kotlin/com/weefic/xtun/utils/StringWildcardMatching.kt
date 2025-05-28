package com.weefic.xtun.utils

import java.util.*

fun String.matchWildcard(pattern: String): Boolean {
    val content = this
    val patternLength = pattern.length
    val contentLength = content.length

    // Use two arrays to store the current and previous rows of the dp table
    var previous = BitSet(contentLength + 1)
    var current = BitSet(contentLength + 1)
    previous[0] = true // Empty pattern matches empty content


    for (i in 1..patternLength) {
        val p = pattern[i - 1]
        // Handle patterns with '*' at the beginning
        current[0] = p == '*' && previous[0]
        for (j in 1..contentLength) {
            val c = content[j - 1]
            when (p) {
                '*' -> {
                    // '*' can match zero or more characters
                    current[j] = previous[j] || current[j - 1]
                }

                '?', c -> {
                    // '?' matches any single character or character matches without a wildcard
                    current[j] = previous[j - 1]
                }

                else -> {
                    current[j] = false
                }
            }
        }
        // Swap current and previous rows for the next iteration
        val temp = previous
        previous = current
        current = temp
    }
    return previous[contentLength]
}