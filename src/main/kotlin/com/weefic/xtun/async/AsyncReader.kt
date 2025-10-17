package com.weefic.xtun.async

import com.weefic.xtun.utils.Endian
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.io.EOFException
import kotlin.math.min


interface AsyncReaderSource {
    suspend fun next(): ByteArray
    val isEmpty: Boolean
}

class AsyncReaderChannelSource(val source: Channel<ByteArray>) : AsyncReaderSource {
    override suspend fun next(): ByteArray {
        return source.receive()
    }

    override val isEmpty: Boolean get() = this.source.isEmpty
}


class AsyncReader(val source: AsyncReaderSource) : AsyncReaderSource {
    companion object {
        private val EMPTY_BUFFER = ByteArray(0)
    }

    private var buffer = EMPTY_BUFFER
    private var readerIndex = 0

    suspend fun read(): Int {
        if (this.readerIndex < this.buffer.size) {
            return this.buffer[this.readerIndex++].toInt() and 0xFF
        }
        try {
            this.buffer = this.source.next()
            this.readerIndex = 0
        } catch (_: ClosedReceiveChannelException) {
            return -1
        }
        return this.read()
    }

    suspend fun readByte(): Byte {
        this.wait(1)
        return this.buffer[this.readerIndex++]
    }

    suspend fun readShort(endian: Endian): Short {
        this.wait(2)
        val value = endian.getShort(this.buffer, this.readerIndex)
        this.readerIndex += 2
        return value
    }

    suspend fun readInt(endian: Endian): Int {
        this.wait(4)
        val value = endian.getInt(this.buffer, this.readerIndex)
        this.readerIndex += 4
        return value
    }

    suspend fun readLong(endian: Endian): Long {
        this.wait(8)
        val value = endian.getLong(this.buffer, this.readerIndex)
        this.readerIndex += 8
        return value
    }


    suspend fun read(data: ByteArray, offset: Int, length: Int): Int {
        var remain = length
        while (remain > 0) {
            val bufferRemain = this.buffer.size - this.readerIndex
            if (bufferRemain == 0) {
                try {
                    this.buffer = this.source.next()
                    this.readerIndex = 0
                } catch (e: ClosedReceiveChannelException) {
                    return length - remain
                }
            } else {
                val sizeToCopy = min(remain, this.buffer.size - this.readerIndex)
                this.buffer.copyInto(data, offset + (length - remain), this.readerIndex, this.readerIndex + sizeToCopy)
                this.readerIndex += sizeToCopy
                remain -= sizeToCopy
            }
        }
        return length
    }

    suspend fun readFully(nBytes: Int): ByteArray {
        if (this.readerIndex == 0 && this.buffer.size == nBytes) {
            val result = this.buffer
            this.buffer = EMPTY_BUFFER
            return result
        }
        val data = ByteArray(nBytes)
        val count = this.read(data, 0, nBytes)
        if (count != nBytes) {
            throw EOFException()
        }
        return data
    }

    suspend fun readAvailable(): List<ByteArray> {
        val allAvailable = ArrayList<ByteArray>()
        if (this.readerIndex == 0) { //
            if (this.buffer.isNotEmpty()) {
                allAvailable.add(this.buffer)
                this.buffer = EMPTY_BUFFER
            } else {
                // EMPTY
            }
        } else if (this.readerIndex < this.buffer.size) {
            allAvailable.add(this.buffer.copyOfRange(this.readerIndex, this.buffer.size))
            this.buffer = EMPTY_BUFFER
            this.readerIndex = 0
        }
        while (true) {
            if (this.source.isEmpty) {
                if (allAvailable.isNotEmpty()) {
                    return allAvailable
                }
            }
            try {
                val result = this.source.next()
                allAvailable.add(result)
            } catch (e: ClosedReceiveChannelException) {
                if (allAvailable.isEmpty()) {
                    throw EOFException()
                } else {
                    return allAvailable
                }
            }
        }
    }

    private suspend fun wait(size: Int) {
        while (true) {
            val remain = this.buffer.size - this.readerIndex
            if (remain >= size) {
                return
            }
            try {
                this.buffer += this.source.next()
            } catch (e: ClosedReceiveChannelException) {
                throw EOFException()
            }
        }
    }

    override val isEmpty: Boolean
        get() {
            return this.readerIndex == this.buffer.size && this.source.isEmpty
        }

    override suspend fun next(): ByteArray {
        if (this.readerIndex != this.buffer.size) {
            if (this.readerIndex == 0) {
                val buffer = this.buffer
                this.buffer = EMPTY_BUFFER
                return buffer
            } else {
                val buffer = this.buffer.copyOfRange(this.readerIndex, this.buffer.size)
                this.buffer = EMPTY_BUFFER
                this.readerIndex = 0
                return buffer
            }
        } else {
            return this.source.next()
        }
    }
}