package com.sadturtleman.flowershoes.core.compression

/** 압축기 하나. iOS 에서는 Swift(libzstd)가 이것을 구현해 [Zstd.codec] 에 꽂는다. */
interface Compressor {
    fun compress(bytes: ByteArray, level: Int): ByteArray

    /** 크기를 확인한 뒤 제한 안에서만 할당한다. 잘못된/잘린/크기 미상 프레임은 null이다. */
    fun decompress(frame: ByteArray, maxOutputBytes: Int = Zstd.MAX_DECOMPRESSED_BYTES): ByteArray?
}

/**
 * zstd 압축(설계 6 · 7-6).
 *
 * - 그림 문서: 압축하면 zstd 프레임(28 B5 2F FD), 아니면 원래 문서("KKD\1"). 첫 4바이트로 구별한다.
 * - 백업 조각: 앞에 1바이트 표시(0 = 그대로, 1 = zstd). SQLite 조각은 아무 바이트로나 시작할 수 있어서다.
 * - 압축해도 작아지지 않으면(이미 압축된 PNG 등) 그대로 둔다.
 */
object Zstd {
    val MAGIC = byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte())
    const val LEVEL = 3
    const val MAX_DECOMPRESSED_BYTES = 16 * 1024 * 1024

    /** 플랫폼 구현. iOS 는 앱이 시작할 때 Swift 구현을 넣는다. */
    var codec: Compressor? = platformZstd()

    fun isFrame(bytes: ByteArray): Boolean = bytes.size >= 4 && bytes.copyOf(4).contentEquals(MAGIC)

    /** 작아질 때만 zstd 프레임, 아니면 원래 바이트. */
    fun pack(bytes: ByteArray): ByteArray {
        val c = codec ?: return bytes
        val z = c.compress(bytes, LEVEL)
        return if (z.size < bytes.size) z else bytes
    }

    /** 프레임이면 풀고, 아니면 그대로. */
    fun unpack(bytes: ByteArray, maxOutputBytes: Int = MAX_DECOMPRESSED_BYTES): ByteArray {
        require(maxOutputBytes in 0..MAX_DECOMPRESSED_BYTES && bytes.size <= MAX_DECOMPRESSED_BYTES) { "압축 입력이 허용 크기를 넘는다" }
        val result = if (isFrame(bytes)) checkNotNull(codec) { "zstd 구현이 없다" }.decompress(bytes, maxOutputBytes) else bytes
        require(result != null && result.size <= maxOutputBytes) { "압축 문서를 안전하게 풀 수 없다" }
        return result
    }

    fun packFlagged(bytes: ByteArray): ByteArray {
        val packed = pack(bytes)
        return byteArrayOf(if (packed === bytes) RAW else ZSTD) + packed
    }

    fun unpackFlagged(bytes: ByteArray): ByteArray {
        require(bytes.isNotEmpty() && bytes.size <= MAX_DECOMPRESSED_BYTES + 1) { "조각 크기가 올바르지 않다" }
        val body = bytes.copyOfRange(1, bytes.size)
        return when (bytes[0]) {
            RAW -> body.also { require(it.size <= MAX_DECOMPRESSED_BYTES) { "조각이 허용 크기를 넘는다" } }
            ZSTD -> checkNotNull(checkNotNull(codec) { "zstd 구현이 없다" }.decompress(body)) { "압축 조각을 안전하게 풀 수 없다" }
            else -> error("모르는 압축 표시: ${bytes[0]}")
        }
    }

    private const val RAW: Byte = 0
    private const val ZSTD: Byte = 1
}

internal expect fun platformZstd(): Compressor?
