package com.sadturtleman.flowershoes.core.compression

/** zstd-jni(aar, ABI 별 .so 포함). */
internal actual fun platformZstd(): Compressor? = object : Compressor {
    override fun compress(bytes: ByteArray, level: Int): ByteArray = com.github.luben.zstd.Zstd.compress(bytes, level)

    override fun decompress(frame: ByteArray): ByteArray {
        val size = com.github.luben.zstd.Zstd.getFrameContentSize(frame)
        require(size in 0..Int.MAX_VALUE) { "프레임에 원래 크기가 없다" }
        return com.github.luben.zstd.Zstd.decompress(frame, size.toInt())
    }
}
