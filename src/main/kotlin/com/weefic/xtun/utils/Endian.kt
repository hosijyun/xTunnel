package com.weefic.xtun.utils

sealed class Endian {
    abstract fun getShort(data: ByteArray, offset: Int): Short
    abstract fun putShort(data: ByteArray, offset: Int, value: Short)
    abstract fun getInt(data: ByteArray, offset: Int): Int
    abstract fun putInt(data: ByteArray, offset: Int, value: Int)
    abstract fun getLong(data: ByteArray, offset: Int): Long
    abstract fun putLong(data: ByteArray, offset: Int, value: Long)


    object LE : Endian() {
        override fun getShort(data: ByteArray, offset: Int): Short {
            val b0: Int = data[offset + 0].toInt() and 0xFF
            val b1: Int = data[offset + 1].toInt() and 0xFF
            return (b1 shl 8 or b0).toShort()
        }

        override fun putShort(data: ByteArray, offset: Int, v: Short) {
            data[offset + 0] = v.toByte()
            data[offset + 1] = (v.toInt() shr 8).toByte()
        }

        override fun getInt(data: ByteArray, offset: Int): Int {
            val b0: Int = data[offset + 0].toInt() and 0xFF
            val b1: Int = data[offset + 1].toInt() and 0xFF
            val b2: Int = data[offset + 2].toInt() and 0xFF
            val b3: Int = data[offset + 3].toInt() and 0xFF
            return b3 shl 24 or (b2 shl 16) or (b1 shl 8) or b0
        }

        override fun putInt(data: ByteArray, offset: Int, v: Int) {
            data[offset + 0] = v.toByte()
            data[offset + 1] = (v shr 8).toByte()
            data[offset + 2] = (v shr 16).toByte()
            data[offset + 3] = (v shr 24).toByte()
        }

        override fun getLong(data: ByteArray, offset: Int): Long {
            val b0: Long = data[offset + 0].toLong() and 0xFFL
            val b1: Long = data[offset + 1].toLong() and 0xFFL
            val b2: Long = data[offset + 2].toLong() and 0xFFL
            val b3: Long = data[offset + 3].toLong() and 0xFFL
            val b4: Long = data[offset + 4].toLong() and 0xFFL
            val b5: Long = data[offset + 5].toLong() and 0xFFL
            val b6: Long = data[offset + 6].toLong() and 0xFFL
            val b7: Long = data[offset + 7].toLong() and 0xFFL
            return b7 shl 56 or (b6 shl 48) or (b5 shl 40) or (b4 shl 32) or (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
        }

        override fun putLong(data: ByteArray, offset: Int, v: Long) {
            data[offset + 0] = v.toByte()
            data[offset + 1] = (v shr 8).toByte()
            data[offset + 2] = (v shr 16).toByte()
            data[offset + 3] = (v shr 24).toByte()
            data[offset + 4] = (v shr 32).toByte()
            data[offset + 5] = (v shr 40).toByte()
            data[offset + 6] = (v shr 48).toByte()
            data[offset + 7] = (v shr 56).toByte()
        }
    }

    object BE : Endian() {
        override fun getShort(data: ByteArray, offset: Int): Short {
            val b0: Int = data[offset + 1].toInt() and 0xFF
            val b1: Int = data[offset + 0].toInt() and 0xFF
            return (b1 shl 8 or b0).toShort()
        }

        override fun putShort(data: ByteArray, offset: Int, v: Short) {
            data[offset + 1] = v.toByte()
            data[offset + 0] = (v.toInt() shr 8).toByte()
        }

        override fun getInt(data: ByteArray, offset: Int): Int {
            val b0: Int = data[offset + 3].toInt() and 0xFF
            val b1: Int = data[offset + 2].toInt() and 0xFF
            val b2: Int = data[offset + 1].toInt() and 0xFF
            val b3: Int = data[offset + 0].toInt() and 0xFF
            return b3 shl 24 or (b2 shl 16) or (b1 shl 8) or b0
        }

        override fun putInt(data: ByteArray, offset: Int, v: Int) {
            data[offset + 3] = v.toByte()
            data[offset + 2] = (v shr 8).toByte()
            data[offset + 1] = (v shr 16).toByte()
            data[offset + 0] = (v shr 24).toByte()
        }

        override fun getLong(data: ByteArray, offset: Int): Long {
            val b0: Long = data[offset + 7].toLong() and 0xFFL
            val b1: Long = data[offset + 6].toLong() and 0xFFL
            val b2: Long = data[offset + 5].toLong() and 0xFFL
            val b3: Long = data[offset + 4].toLong() and 0xFFL
            val b4: Long = data[offset + 3].toLong() and 0xFFL
            val b5: Long = data[offset + 2].toLong() and 0xFFL
            val b6: Long = data[offset + 1].toLong() and 0xFFL
            val b7: Long = data[offset + 0].toLong() and 0xFFL
            return b7 shl 56 or (b6 shl 48) or (b5 shl 40) or (b4 shl 32) or (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
        }

        override fun putLong(data: ByteArray, offset: Int, v: Long) {
            data[offset + 7] = v.toByte()
            data[offset + 6] = (v shr 8).toByte()
            data[offset + 5] = (v shr 16).toByte()
            data[offset + 4] = (v shr 24).toByte()
            data[offset + 3] = (v shr 32).toByte()
            data[offset + 2] = (v shr 40).toByte()
            data[offset + 1] = (v shr 48).toByte()
            data[offset + 0] = (v shr 56).toByte()
        }
    }
}