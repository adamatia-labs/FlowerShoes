package com.sadturtleman.flowershoes.core.compression

/** zstd-jni(aar, ABI 별 .so 포함). */
internal actual fun platformZstd(): Compressor? = object : Compressor {
    override fun compress(bytes: ByteArray, level: Int): ByteArray = com.github.luben.zstd.Zstd.compress(bytes, level)

    override fun decompress(frame: ByteArray, maxOutputBytes: Int): ByteArray? {
        if (maxOutputBytes !in 0..Zstd.MAX_DECOMPRESSED_BYTES || frame.size > Zstd.MAX_DECOMPRESSED_BYTES) return null
        return try {
            val size = com.github.luben.zstd.Zstd.getFrameContentSize(frame)
            if (size !in 0..maxOutputBytes.toLong()) return null
            com.github.luben.zstd.Zstd.decompress(frame, size.toInt()).takeIf { it.size.toLong() == size }
        } catch (_: com.github.luben.zstd.ZstdException) { null }
    }
}
