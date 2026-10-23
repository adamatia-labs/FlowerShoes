package com.sadturtleman.flowershoes.core.drawing

/** 가변길이 정수 · TLV 쓰기. 그림 문서 인코딩에만 쓰는 작은 도구다. */
internal class ByteWriter(initial: Int = 256) {
    private var buf = ByteArray(initial)
    var size = 0
        private set

    private fun ensure(extra: Int) {
        if (size + extra <= buf.size) return
        buf = buf.copyOf(maxOf(buf.size * 2, size + extra))
    }

    fun byte(b: Int) {
        ensure(1)
        buf[size++] = b.toByte()
    }

    fun bytes(src: ByteArray) {
        ensure(src.size)
        src.copyInto(buf, size)
        size += src.size
    }

    /** 부호 없는 LEB128. */
    fun varint(value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            byte(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        byte(v.toInt())
    }

    fun zigzag(value: Long) = varint((value shl 1) xor (value shr 63))

    /** `[tag][length][본문]`. 읽는 쪽은 모르는 tag 를 length 만큼 건너뛴다. */
    fun field(tag: Int, body: ByteWriter.() -> Unit) {
        val inner = ByteWriter().apply(body)
        varint(tag.toLong())
        varint(inner.size.toLong())
        bytes(inner.toByteArray())
    }

    fun toByteArray(): ByteArray = buf.copyOf(size)
}

internal class ByteReader(private val buf: ByteArray, private var pos: Int = 0, private val end: Int = buf.size) {

    val hasMore: Boolean get() = pos < end

    fun byte(): Int {
        if (pos >= end) throw DrawingFormatException("문서가 중간에 끝났다")
        return buf[pos++].toInt() and 0xFF
    }

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = byte()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift > 63) throw DrawingFormatException("varint 가 너무 길다")
        }
    }

    fun zigzag(): Long {
        val v = varint()
        return (v ushr 1) xor -(v and 1)
    }

    fun bytes(length: Int): ByteArray {
        if (length < 0 || pos + length > end) throw DrawingFormatException("길이가 범위를 넘는다")
        return buf.copyOfRange(pos, pos + length).also { pos += length }
    }

    /** 다음 `[tag][length][본문]` 하나를 읽어 본문을 따로 읽는 리더와 함께 돌려준다. */
    fun field(): Pair<Int, ByteReader> {
        val tag = varint().toInt()
        val length = varint().toInt()
        if (length < 0 || pos + length > end) throw DrawingFormatException("필드 길이가 범위를 넘는다")
        val body = ByteReader(buf, pos, pos + length)
        pos += length
        return tag to body
    }
}

class DrawingFormatException(message: String) : Exception(message)
